package com.hz.delivery.config;

import com.hz.delivery.common.ApiResult;
import com.hz.delivery.common.Constants;
import com.hz.delivery.common.ErrorCode;
import com.hz.delivery.entity.Admin;
import com.hz.delivery.mapper.AdminMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 鉴权拦截器：C 端与后台共用一套 Token 机制，按路径前缀区分身份。
 * 允许名单统一配置在 WebConfig，本类只负责校验。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    /** 只读角色：仅可查询与导出，禁止任何写操作 */
    private static final String ROLE_VIEWER = "viewer";

    private final StringRedisTemplate redis;
    private final AdminMapper adminMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        String token = request.getHeader(Constants.HEADER_TOKEN);
        if (!StringUtils.hasText(token)) {
            token = request.getParameter("token");
        }

        if (!StringUtils.hasText(token)) {
            return reject(response);
        }

        boolean isAdmin = path.startsWith("/api/admin");
        String key = (isAdmin ? Constants.KEY_ADMIN_TOKEN : Constants.KEY_CLIENT_TOKEN) + token;
        String value = redis.opsForValue().get(key);
        if (!StringUtils.hasText(value)) {
            return reject(response);
        }

        if (isAdmin) {
            Long adminId = Long.valueOf(value);
            request.setAttribute(Constants.ATTR_ADMIN_ID, adminId);
            // 只读账号（viewer）禁止一切写操作，避免误改名单 / 套餐 / 发货数据
            if (!"GET".equalsIgnoreCase(request.getMethod())
                    && !path.startsWith("/api/admin/auth")) {
                Admin admin = adminMapper.selectById(adminId);
                if (admin == null) {
                    return reject(response, ErrorCode.UNAUTHORIZED);
                }
                if (ROLE_VIEWER.equalsIgnoreCase(admin.getRole())) {
                    return reject(response, ErrorCode.FORBIDDEN, "当前为只读账号，无操作权限");
                }
            }
        } else {
            request.setAttribute(Constants.ATTR_GRANTEE_ID, Long.valueOf(value));
        }
        return true;
    }

    private boolean reject(HttpServletResponse response) throws Exception {
        return reject(response, ErrorCode.UNAUTHORIZED);
    }

    private boolean reject(HttpServletResponse response, ErrorCode code) throws Exception {
        return reject(response, code, code.getMessage());
    }

    private boolean reject(HttpServletResponse response, ErrorCode code, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResult.fail(code, msg)));
        return false;
    }
}
