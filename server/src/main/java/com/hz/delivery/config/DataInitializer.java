package com.hz.delivery.config;

import com.hz.delivery.common.PasswordUtil;
import com.hz.delivery.entity.Admin;
import com.hz.delivery.mapper.AdminMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动初始化：首次启动时创建默认后台账号。
 * 默认密码可通过环境变量 ADMIN_INIT_PASSWORD 指定，上线后请立即修改。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final AdminMapper adminMapper;
    private final Environment env;

    @Override
    public void run(ApplicationArguments args) {
        Long count = adminMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }

        String pwd = env.getProperty("ADMIN_INIT_PASSWORD", "hz@2026");
        adminMapper.insert(build("admin", pwd, "系统管理员", "admin"));
        adminMapper.insert(build("operator", pwd, "运营专员", "operator"));
        adminMapper.insert(build("viewer", pwd, "只读账号", "viewer"));

        log.warn("==================================================================");
        log.warn(" 已创建默认后台账号：admin / operator / viewer");
        log.warn(" 初始密码：{}   —— 请登录后立即修改！", pwd);
        log.warn("==================================================================");
    }

    private Admin build(String username, String rawPassword, String realName, String role) {
        Admin a = new Admin();
        a.setUsername(username);
        a.setPassword(PasswordUtil.hash(rawPassword));
        a.setRealName(realName);
        a.setRole(role);
        a.setStatus(1);
        return a;
    }
}
