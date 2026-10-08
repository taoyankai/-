package com.hz.delivery.service.logistics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hz.delivery.config.BizProperties;
import com.hz.delivery.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 快递100企业版“实时快递查询”实现。 */
@Slf4j
@Component
public class Kuaidi100LogisticsProvider implements LogisticsProvider {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<String, String> COMPANY_CODES = Map.of(
            "SF", "shunfeng",
            "JD", "jd",
            "YTO", "yuantong",
            "ZTO", "zhongtong",
            "STO", "shentong",
            "EMS", "ems"
    );

    private final BizProperties props;
    private final ObjectMapper json;
    private final RestClient client;

    public Kuaidi100LogisticsProvider(BizProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
        BizProperties.Logistics.Kuaidi100 cfg = props.getLogistics().getKuaidi100();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(cfg.getConnectTimeout() * 1000);
        factory.setReadTimeout(cfg.getReadTimeout() * 1000);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public boolean isEnabled() {
        BizProperties.Logistics.Kuaidi100 cfg = props.getLogistics().getKuaidi100();
        return "kuaidi100".equalsIgnoreCase(props.getLogistics().getProvider())
                && StringUtils.hasText(cfg.getKey())
                && StringUtils.hasText(cfg.getCustomer());
    }

    @Override
    public String providerCode(String carrierCode) {
        return COMPANY_CODES.get(carrierCode);
    }

    @Override
    public LogisticsQueryResult query(Order order) {
        if (!isEnabled()) {
            return LogisticsQueryResult.failed("真实物流通道未启用或缺少 KUAIDI100_KEY/KUAIDI100_CUSTOMER");
        }
        String company = providerCode(order.getCarrier());
        if (!StringUtils.hasText(company)) {
            return LogisticsQueryResult.failed("暂不支持承运商编码：" + order.getCarrier());
        }
        if (!StringUtils.hasText(order.getWaybillNo())) {
            return LogisticsQueryResult.failed("订单未绑定真实运单号");
        }

        BizProperties.Logistics.Kuaidi100 cfg = props.getLogistics().getKuaidi100();
        try {
            String param = buildParam(order, company);
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("customer", cfg.getCustomer());
            form.add("sign", md5Upper(param + cfg.getKey() + cfg.getCustomer()));
            form.add("param", param);

            String body = client.post()
                    .uri(cfg.getBaseUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return parse(body);
        } catch (RestClientException e) {
            log.warn("快递100查询失败 waybill={} error={}", order.getWaybillNo(), e.getMessage());
            return LogisticsQueryResult.failed("物流平台连接失败，请稍后重试");
        } catch (Exception e) {
            log.warn("快递100响应处理失败 waybill={} error={}", order.getWaybillNo(), e.getMessage());
            return LogisticsQueryResult.failed("物流平台响应异常，请稍后重试");
        }
    }

    private String buildParam(Order order, String company) throws Exception {
        var node = json.createObjectNode();
        node.put("com", company);
        node.put("num", order.getWaybillNo().trim());
        // 顺丰和中通必须传收/寄件人电话；其他承运商一并传入可提升匹配准确率。
        if (StringUtils.hasText(order.getPhone())) {
            node.put("phone", order.getPhone().trim());
        }
        String destination = safe(order.getProvince()) + safe(order.getCity()) + safe(order.getDistrict());
        if (StringUtils.hasText(destination)) {
            node.put("to", destination);
        }
        node.put("resultv2", "4");
        node.put("show", "0");
        node.put("order", "desc");
        node.put("lang", "zh");
        return json.writeValueAsString(node);
    }

    private LogisticsQueryResult parse(String body) throws Exception {
        if (!StringUtils.hasText(body)) {
            return LogisticsQueryResult.failed("物流平台返回空响应");
        }
        JsonNode root = json.readTree(body);
        String status = root.path("status").asText();
        String message = root.path("message").asText("查询失败");
        if (!"200".equals(status)) {
            return LogisticsQueryResult.failed(message);
        }

        List<LogisticsQueryResult.Trace> traces = new ArrayList<>();
        JsonNode data = root.path("data");
        if (data.isArray()) {
            for (JsonNode item : data) {
                String description = item.path("context").asText();
                LocalDateTime time = parseTime(item.path("time").asText());
                if (StringUtils.hasText(description) && time != null) {
                    traces.add(new LogisticsQueryResult.Trace(description, time,
                            item.path("status").asText(), item.path("statusCode").asText()));
                }
            }
        }
        return new LogisticsQueryResult(true, message, root.path("state").asText(),
                root.path("com").asText(), root.path("nu").asText(), traces);
    }

    private static LocalDateTime parseTime(String value) {
        try {
            return LocalDateTime.parse(value, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String md5Upper(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(32);
        for (byte b : digest) {
            hex.append(String.format("%02X", b & 0xff));
        }
        return hex.toString().toUpperCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
