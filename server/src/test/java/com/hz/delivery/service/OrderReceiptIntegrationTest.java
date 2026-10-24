package com.hz.delivery.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hz.delivery.common.BizException;
import com.hz.delivery.config.BizProperties;
import com.hz.delivery.entity.Order;
import com.hz.delivery.mapper.AdminMapper;
import com.hz.delivery.mapper.OrderMapper;
import com.hz.delivery.mapper.OrderTrackMapper;
import com.hz.delivery.service.logistics.LogisticsProvider;
import com.hz.delivery.service.logistics.LogisticsQueryResult;
import com.hz.delivery.service.logistics.LogisticsTrackingService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 只使用专门创建的临时MySQL，不接受业务服务地址，不执行任何业务环境迁移。 */
@EnabledIfEnvironmentVariable(named = "TEST_MYSQL_HOST", matches = "hz-receipt-test-mysql-[a-zA-Z0-9-]+")
@SpringJUnitConfig(OrderReceiptIntegrationTest.Config.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderReceiptIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired OrderMapper orders;
    @Autowired OrderTrackMapper tracks;
    @Autowired OrderReceiptService receipts;
    private JdbcTemplate jdbc;

    @BeforeAll
    void initializeIsolatedSchema() throws Exception {
        jdbc = new JdbcTemplate(dataSource);
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(System.getenv("TEST_SCHEMA_SQL")));
        }
        jdbc.update("INSERT INTO t_admin(id,username,password,real_name,role,status,must_change_password) "
                + "VALUES(2,'receipt-test-operator','unused','测试运营','operator',1,0)");
    }

    @BeforeEach
    void prepareOrder() {
        jdbc.update("DELETE FROM t_order_track");
        jdbc.update("DELETE FROM t_order");
        jdbc.update("INSERT INTO t_order(id,order_no,grantee_id,receiver,phone,status,carrier,waybill_no,ship_time) "
                + "VALUES(1,'RECEIPT-TEST-1',10,'测试收货人','00000000000',30,'SF','TEST-WAYBILL-1',NOW())");
    }

    @Test
    void concurrentClientAndAdminRequestsOnlyConfirmOnce() throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<OrderReceiptService.Confirmation>> tasks = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                boolean client = i % 2 == 0;
                tasks.add(() -> {
                    start.await();
                    return client ? receipts.confirmForClient(10L, 1L) : receipts.confirmForAdmin(2L, 1L, null);
                });
            }
            var futures = new ArrayList<java.util.concurrent.Future<OrderReceiptService.Confirmation>>();
            for (var task : tasks) futures.add(pool.submit(task));
            start.countDown();
            int changed = 0;
            for (var future : futures) if (future.get().changed()) changed++;
            assertEquals(1, changed);
            assertEquals(40, orders.selectById(1L).getStatus());
            assertNotNull(orders.selectById(1L).getSignTime());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM t_order_track", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void platformSignedOrderRequiresNoMoreManualConfirmation() {
        var signedAt = LocalDateTime.of(2026, 10, 23, 12, 0);
        jdbc.update("UPDATE t_order SET status=40,sign_time=? WHERE id=1", signedAt);
        assertFalse(receipts.confirmForClient(10L, 1L).changed());
        assertFalse(receipts.confirmForAdmin(2L, 1L, "不应重复写入").changed());
        assertEquals(signedAt, orders.selectById(1L).getSignTime());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM t_order_track", Integer.class));
    }

    @Test
    void delayedCarrierResponseCannotReopenConfirmedOrder() {
        Order stale = orders.selectById(1L);
        var confirmed = receipts.confirmForClient(10L, 1L);
        var logistics = new LogisticsTrackingService(orders, tracks, mock(StringRedisTemplate.class),
                mock(LogisticsProvider.class), new BizProperties());
        var response = new LogisticsQueryResult(true, "ok", "3", "shunfeng", "TEST-WAYBILL-1",
                List.of(new LogisticsQueryResult.Trace("承运商已签收", LocalDateTime.of(2026,10,23,9,0), "签收", "3")));
        assertEquals(40, logistics.applyValidatedResult(stale, response).getStatus());
        assertEquals(confirmed.signTime().withNano(0), orders.selectById(1L).getSignTime());
        // 再验证迟到的在途状态不能把签收订单改回运输中。
        assertEquals(0, orders.updateLogisticsStatus(1L, 30, 20, null, "SF", "TEST-WAYBILL-1"));
        assertEquals(40, orders.selectById(1L).getStatus());
    }

    @Test
    void trackInsertFailureRollsBackReceiptStatusAndTime() {
        jdbc.execute("CREATE TRIGGER receipt_test_reject_track BEFORE INSERT ON t_order_track "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test track failure'");
        try {
            assertThrows(RuntimeException.class, () -> receipts.confirmForClient(10L, 1L));
            assertEquals(30, orders.selectById(1L).getStatus());
            assertNull(orders.selectById(1L).getSignTime());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM t_order_track", Integer.class));
        } finally { jdbc.execute("DROP TRIGGER receipt_test_reject_track"); }
    }

    @Test
    void wrongOwnerAndReturnedOrderAreProtectedByRealDatabase() {
        assertEquals(1303, assertThrows(BizException.class, () -> receipts.confirmForClient(99L, 1L)).getCode());
        jdbc.update("UPDATE t_order SET status=70 WHERE id=1");
        assertEquals(1302, assertThrows(BizException.class, () -> receipts.confirmForAdmin(2L, 1L, null)).getCode());
        assertEquals(70, orders.selectById(1L).getStatus());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM t_order_track", Integer.class));
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:mysql://" + System.getenv("TEST_MYSQL_HOST")
                    + ":3306/receipt_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                    "root", System.getenv("TEST_MYSQL_PASSWORD"));
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new MybatisSqlSessionFactoryBean();
            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setDataSource(source); factory.setConfiguration(configuration);
            return factory.getObject();
        }
        @Bean OrderMapper orderMapper(SqlSessionFactory factory) {
            factory.getConfiguration().addMapper(OrderMapper.class);
            return new SqlSessionTemplate(factory).getMapper(OrderMapper.class);
        }
        @Bean OrderTrackMapper trackMapper(SqlSessionFactory factory) {
            factory.getConfiguration().addMapper(OrderTrackMapper.class);
            return new SqlSessionTemplate(factory).getMapper(OrderTrackMapper.class);
        }
        @Bean AdminMapper adminMapper(SqlSessionFactory factory) {
            factory.getConfiguration().addMapper(AdminMapper.class);
            return new SqlSessionTemplate(factory).getMapper(AdminMapper.class);
        }
        @Bean OrderReceiptService receiptService(OrderMapper orders, OrderTrackMapper tracks, AdminMapper admins) {
            return new OrderReceiptService(orders, tracks, admins);
        }
    }
}
