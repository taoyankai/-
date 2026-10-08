package com.hz.delivery.config;

import com.hz.delivery.common.PasswordUtil;
import com.hz.delivery.entity.Admin;
import com.hz.delivery.entity.Carrier;
import com.hz.delivery.mapper.AdminMapper;
import com.hz.delivery.mapper.CarrierMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 启动初始化：首次启动时创建默认后台账号。
 * 默认密码可通过环境变量 ADMIN_INIT_PASSWORD 指定，上线后请立即修改。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final AdminMapper adminMapper;
    private final CarrierMapper carrierMapper;
    private final Environment env;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureAdminPasswordFlag();
        ensureUniqueConstraints();
        ensureCarrier("STO", "申通快递", "https://www.sto.cn/", "95543", 5);
        String pwd = env.getProperty("ADMIN_INIT_PASSWORD", "hz@2026");
        boolean created = ensureAdmin("admin", pwd, "系统管理员", "admin")
                | ensureAdmin("operator", pwd, "运营专员", "operator")
                | ensureAdmin("viewer", pwd, "只读账号", "viewer");
        if (created) {
            log.warn("==================================================================");
            log.warn(" 已补齐默认后台账号：admin / operator / viewer");
            log.warn(" 初始密码：{}   —— 请登录后立即修改！", pwd);
            log.warn("==================================================================");
        }
    }

    /** 为已有数据卷补充首次改密标记；新建库则已由 init.sql 创建。 */
    private void ensureAdminPasswordFlag() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_admin' " +
                        "AND COLUMN_NAME = 'must_change_password'",
                Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE t_admin ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 1 " +
                    "COMMENT '1 首次登录必须修改初始密码' AFTER status");
            log.warn("已为后台账号补充首次修改密码标记，现有账号下次登录必须修改密码");
        }
    }

    /**
     * 为已有数据卷补齐并发安全所需的唯一约束。
     * 发现历史重复数据时不自动删除，而是阻止启动并输出冲突值，避免静默损坏业务数据。
     */
    private void ensureUniqueConstraints() {
        ensureUniqueIndex("t_admin", "username", "uk_admin_username", "idx_username");
        ensureUniqueIndex("t_grantee", "phone", "uk_grantee_phone", "idx_phone");
        ensureUniqueIndex("t_order", "order_no", "uk_order_order_no", "idx_order_no");
        ensureUniqueIndex("t_order", "waybill_no", "uk_order_waybill_no", "idx_waybill");
        ensureUniqueIndex("t_carrier", "code", "uk_carrier_code", "idx_code");
        ensureUniqueIndex("t_import_batch", "batch_no", "uk_import_batch_no", null);
    }

    private void ensureUniqueIndex(String table, String column, String uniqueIndex, String oldIndex) {
        if (indexExists(table, uniqueIndex)) {
            return;
        }

        String duplicateSql = "SELECT `" + column + "` AS duplicate_value, COUNT(*) AS duplicate_count "
                + "FROM `" + table + "` WHERE `" + column + "` IS NOT NULL "
                + "GROUP BY `" + column + "` HAVING COUNT(*) > 1 LIMIT 10";
        List<Map<String, Object>> duplicates = jdbcTemplate.queryForList(duplicateSql);
        if (!duplicates.isEmpty()) {
            String detail = duplicates.stream()
                    .map(row -> String.valueOf(row.get("duplicate_value")) + "×" + row.get("duplicate_count"))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("未知冲突");
            throw new IllegalStateException("无法创建唯一索引 " + uniqueIndex
                    + "：表 " + table + " 的字段 " + column + " 存在重复数据（" + detail + "）");
        }

        StringBuilder ddl = new StringBuilder("ALTER TABLE `").append(table).append("` ");
        if (oldIndex != null && indexExists(table, oldIndex)) {
            ddl.append("DROP INDEX `").append(oldIndex).append("`, ");
        }
        ddl.append("ADD UNIQUE KEY `").append(uniqueIndex).append("` (`").append(column).append("`)");
        jdbcTemplate.execute(ddl.toString());
        log.warn("已补充唯一索引：{}.{} -> {}", table, column, uniqueIndex);
    }

    private boolean indexExists(String table, String index) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class, table, index);
        return count != null && count > 0;
    }

    private void ensureCarrier(String code, String name, String trackUrl, String phone, int sort) {
        Long count = carrierMapper.selectCount(new LambdaQueryWrapper<Carrier>().eq(Carrier::getCode, code));
        if (count != null && count > 0) {
            return;
        }
        Carrier carrier = new Carrier();
        carrier.setCode(code);
        carrier.setName(name);
        carrier.setTrackUrl(trackUrl);
        carrier.setPhone(phone);
        carrier.setSort(sort);
        carrier.setStatus(1);
        try {
            carrierMapper.insert(carrier);
            log.info("已补充承运商字典：{} {}", code, name);
        } catch (DuplicateKeyException e) {
            // 多实例同时启动时由数据库唯一索引决定胜者，另一个实例直接继续。
            log.info("承运商字典已由其他实例补充：{}", code);
        }
    }

    private boolean ensureAdmin(String username, String rawPassword, String realName, String role) {
        if (adminMapper.selectByUsername(username) != null) {
            return false;
        }
        try {
            adminMapper.insert(build(username, rawPassword, realName, role));
            return true;
        } catch (DuplicateKeyException e) {
            // 多实例首次启动时允许一个实例创建成功，其余实例不应因此启动失败。
            log.info("默认后台账号已由其他实例创建：{}", username);
            return false;
        }
    }

    private Admin build(String username, String rawPassword, String realName, String role) {
        Admin a = new Admin();
        a.setUsername(username);
        a.setPassword(PasswordUtil.hash(rawPassword));
        a.setRealName(realName);
        a.setRole(role);
        a.setStatus(1);
        a.setMustChangePassword(1);
        return a;
    }
}
