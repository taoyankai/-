package com.hz.delivery.controller;

import com.hz.delivery.common.ApiResult;
import com.hz.delivery.common.BizException;
import com.hz.delivery.common.ErrorCode;
import com.hz.delivery.config.BizProperties;
import com.hz.delivery.service.sms.SmsSender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 短信通道运维自检接口。
 *
 * <p>部署到服务器后，用来确认「短信到底发得出去吗」——不需要造业务数据，
 * 直接向指定手机号发一条测试短信，并把短信平台的原始返回原样带回，便于定位问题。
 *
 * <p>安全约束：
 * <ul>
 *   <li>该路径位于 {@code /api/admin/**} 下，与其它后台接口一样需要管理员登录态；</li>
 *   <li>额外由 {@code hz.delivery.sms.test-enabled} 控制开关，生产环境建议保持 false，
 *       需要排查时临时打开、用完关闭。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/sms")
@RequiredArgsConstructor
@Validated
public class AdminSmsController {

    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final SmsSender smsSender;
    private final BizProperties props;

    /** 当前短信通道与自检开关状态 */
    @GetMapping("/status")
    public ApiResult<Map<String, Object>> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", smsSender.provider());
        m.put("testEnabled", props.getSms().isTestEnabled());
        m.put("loginMode", props.getLoginMode());
        return ApiResult.ok(m);
    }

    /**
     * 发送一条真实测试短信。
     *
     * @param phone 接收号码
     * @param msg   自定义正文，留空则发送带时间戳的自检文本
     */
    @PostMapping("/test")
    public ApiResult<Map<String, Object>> test(
            @RequestParam @NotBlank(message = "请填写手机号")
            @Pattern(regexp = "^1\\d{10}$", message = "请填写正确的 11 位手机号") String phone,
            @RequestParam(required = false) String msg) {
        if (!props.getSms().isTestEnabled()) {
            throw BizException.of(ErrorCode.SMS_TEST_DISABLED);
        }
        String text = StringUtils.hasText(msg) ? msg.trim() : "短信通道自检 " + LocalDateTime.now().format(HHMMSS);
        String resp = smsSender.sendText(phone, text);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", smsSender.provider());
        m.put("phone", phone);
        m.put("text", text);
        m.put("response", resp);
        return ApiResult.ok(m);
    }
}
