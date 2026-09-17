package com.hz.delivery.service.sms;

import com.hz.delivery.common.BizException;
import com.hz.delivery.common.ErrorCode;
import com.hz.delivery.config.BizProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 河北大学统一短信服务平台通道（真实发送）。
 *
 * <p>按《高校统一短信服务平台 · 短信接口接入文档》实现：
 * <pre>
 *   POST  {base-url}
 *   Content-Type: application/x-www-form-urlencoded; charset=UTF-8
 *   cid=7&amp;key=***&amp;code=手机号&amp;model=yzmmb&amp;variables={"code":"123456","time":"5"}
 * </pre>
 *
 * <p>要点：
 * <ul>
 *   <li>{@code msg} 与 {@code model} 必须二选一：验证码走 {@code model}，自检正文走 {@code msg}；</li>
 *   <li>成功返回 {@code ok}，失败返回具体错误信息，本类会把原文带进异常提示，便于排查；</li>
 *   <li>短信签名由平台自动添加，单条正文上限 60 字；</li>
 *   <li>密钥不写进代码，通过环境变量 {@code SMS_HBU_KEY} 注入。</li>
 * </ul>
 *
 * <p>当 {@code hz.delivery.sms.provider = hbu} 时启用。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "hz.delivery.sms.provider", havingValue = "hbu")
public class HbuSmsSender implements SmsSender {

    private static final String OK = "ok";

    private final BizProperties props;
    private final HttpClient http;

    public HbuSmsSender(BizProperties props) {
        this.props = props;
        BizProperties.Sms.Hbu cfg = props.getSms().getHbu();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(cfg.getConnectTimeout()))
                .build();
        log.info("[短信-河北大学平台] 通道已启用 baseUrl={} cid={} model={} key={}",
                cfg.getBaseUrl(), cfg.getCid(), cfg.getModel(),
                StringUtils.hasText(cfg.getKey()) ? "已配置" : "未配置");
    }

    @Override
    public String provider() {
        return "hbu";
    }

    @Override
    public String sendCode(String phone, String code) {
        BizProperties.Sms.Hbu cfg = props.getSms().getHbu();
        Map<String, String> form = baseForm(phone);
        form.put("model", cfg.getModel());
        // 模板变量键名必须与模板完全一致：yzmmb = ["code", "time"]
        form.put("variables", "{\"code\":\"" + code + "\",\"time\":\"" + cfg.getTimeMinutes() + "\"}");
        return post(form, phone, "验证码");
    }

    @Override
    public String sendText(String phone, String text) {
        BizProperties.Sms.Hbu cfg = props.getSms().getHbu();
        String body = text == null ? "" : text;
        if (body.isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "短信正文不能为空");
        }
        if (body.length() > cfg.getMaxTextLength()) {
            throw BizException.of(ErrorCode.PARAM_ERROR,
                    "短信正文超过 " + cfg.getMaxTextLength() + " 字上限（当前 " + body.length() + " 字）");
        }
        Map<String, String> form = baseForm(phone);
        form.put("msg", body);
        return post(form, phone, "自检正文");
    }

    private Map<String, String> baseForm(String phone) {
        BizProperties.Sms.Hbu cfg = props.getSms().getHbu();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("cid", cfg.getCid());
        form.put("key", cfg.getKey());
        form.put("code", phone);
        return form;
    }

    private String post(Map<String, String> form, String phone, String scene) {
        BizProperties.Sms.Hbu cfg = props.getSms().getHbu();
        if (!StringUtils.hasText(cfg.getKey())) {
            throw BizException.of(ErrorCode.SMS_SEND_FAILED,
                    "短信通道未配置密钥，请设置环境变量 SMS_HBU_KEY 后重启服务");
        }

        String body = form.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));

        HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.getBaseUrl()))
                .timeout(Duration.ofSeconds(cfg.getReadTimeout()))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("[短信-河北大学平台] 连接失败 scene={} phone={} err={}", scene, mask(phone), e.toString());
            throw BizException.of(ErrorCode.SMS_SEND_FAILED,
                    "短信平台连接失败，请确认服务器可访问 " + cfg.getBaseUrl() + "（校园网/内网地址需放通）");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BizException.of(ErrorCode.SMS_SEND_FAILED, "短信发送被中断，请重试");
        }

        String raw = resp.body() == null ? "" : resp.body().trim();
        if (resp.statusCode() != 200 || !isOk(raw)) {
            log.error("[短信-河北大学平台] 发送失败 scene={} phone={} http={} resp={}",
                    scene, mask(phone), resp.statusCode(), raw);
            throw BizException.of(ErrorCode.SMS_SEND_FAILED,
                    "短信平台返回失败（HTTP " + resp.statusCode() + "）：" + (raw.isEmpty() ? "无响应内容" : raw));
        }
        log.info("[短信-河北大学平台] 发送成功 scene={} phone={} resp={}", scene, mask(phone), raw);
        return raw;
    }

    /** 文档约定：成功返回 ok，失败返回具体错误信息 */
    private boolean isOk(String raw) {
        return raw.equalsIgnoreCase(OK) || raw.toLowerCase().startsWith(OK + " ");
    }

    private String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private String mask(String phone) {
        if (phone == null || phone.length() < 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
