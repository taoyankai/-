package com.hz.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hz.delivery.common.PageResult;
import com.hz.delivery.entity.Admin;
import com.hz.delivery.entity.OperationLog;
import com.hz.delivery.mapper.AdminMapper;
import com.hz.delivery.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 操作审计日志服务。
 *
 * <p>核心原则：审计是旁路，绝不能影响业务主流程。导出一旦开始就应当完成，
 * 不能因为日志表写失败而让管理员拿不到名单，因此所有写入都做了兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    /** 明细字段长度上限（与表结构保持一致，超出即截断） */
    private static final int MAX_DETAIL = 900;
    private static final int MAX_TEXT = 240;

    private final OperationLogMapper logMapper;
    private final AdminMapper adminMapper;

    /** 记录一次成功操作 */
    public void success(Long adminId, String module, String action, String target,
                        String detail, Integer rowCount, HttpServletRequest request) {
        save(adminId, module, action, target, detail, rowCount, "success", null, request);
    }

    /** 记录一次失败操作（用于发现越权尝试与异常导出） */
    public void fail(Long adminId, String module, String action, String target,
                     String detail, String errorMsg, HttpServletRequest request) {
        save(adminId, module, action, target, detail, null, "fail", errorMsg, request);
    }

    private void save(Long adminId, String module, String action, String target, String detail,
                      Integer rowCount, String result, String errorMsg, HttpServletRequest request) {
        try {
            OperationLog l = new OperationLog();
            l.setAdminId(adminId);
            if (adminId != null) {
                Admin a = adminMapper.selectById(adminId);
                if (a != null) {
                    l.setAdminAccount(a.getUsername());
                    l.setAdminName(StringUtils.hasText(a.getRealName()) ? a.getRealName() : a.getUsername());
                    l.setAdminRole(a.getRole());
                }
            }
            l.setModule(module);
            l.setAction(action);
            l.setTarget(target);
            l.setDetail(truncate(detail, MAX_DETAIL));
            l.setRowCount(rowCount);
            l.setIp(clientIp(request));
            l.setUserAgent(request == null ? null : truncate(request.getHeader("User-Agent"), MAX_TEXT));
            l.setResult(result);
            l.setErrorMsg(truncate(errorMsg, 480));
            l.setCreateTime(LocalDateTime.now());
            logMapper.insert(l);
        } catch (Exception e) {
            log.warn("审计日志写入失败 module={} action={} err={}", module, action, e.getMessage());
        }
    }

    /**
     * 日志查询（后台「操作日志」页）
     */
    public PageResult<OperationLog> page(long page, long size, String module, String action,
                                         String keyword, String date) {
        long p = page < 1 ? 1 : page;
        long s = (size < 1 || size > 200) ? 20 : size;

        LambdaQueryWrapper<OperationLog> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(module)) {
            w.eq(OperationLog::getModule, module.trim());
        }
        if (StringUtils.hasText(action)) {
            w.eq(OperationLog::getAction, action.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.and(q -> q.like(OperationLog::getAdminAccount, k)
                    .or().like(OperationLog::getAdminName, k)
                    .or().like(OperationLog::getTarget, k)
                    .or().like(OperationLog::getDetail, k)
                    .or().like(OperationLog::getIp, k));
        }
        if (StringUtils.hasText(date)) {
            try {
                LocalDate d = LocalDate.parse(date.trim());
                w.ge(OperationLog::getCreateTime, d.atStartOfDay())
                        .le(OperationLog::getCreateTime, d.atTime(LocalTime.MAX));
            } catch (Exception ignore) {
                // 日期格式不合法时忽略该条件，不返回错误（查询条件而已）
            }
        }
        w.orderByDesc(OperationLog::getId);

        long total = logMapper.selectCount(w);
        List<OperationLog> list = logMapper.selectList(w.last("LIMIT " + (p - 1) * s + ", " + s));
        return PageResult.of(list, total, p, s);
    }

    /**
     * 取真实客户端 IP。经 Nginx 反代后 remoteAddr 是网关地址，
     * 因此优先读代理头，并按惯例只取 X-Forwarded-For 的第一段。
     */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String h : headers) {
            String v = request.getHeader(h);
            if (StringUtils.hasText(v) && !"unknown".equalsIgnoreCase(v)) {
                int comma = v.indexOf(',');
                return truncate(comma > 0 ? v.substring(0, comma).trim() : v.trim(), 60);
            }
        }
        return truncate(request.getRemoteAddr(), 60);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
