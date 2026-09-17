package com.hz.delivery.job;

import com.hz.delivery.service.SlaAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * SLA 发货预警定时任务。
 *
 * <p>默认每日 09:30 扫描一次（可由环境变量 SLA_ALERT_CRON 调整）。
 * 之所以做成定时任务而不是纯看板：10 日发货是硬指标，靠人主动去看迟早会漏。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaAlertJob {

    private final SlaAlertService slaAlertService;

    @Scheduled(cron = "${hz.delivery.sla-alert-cron:0 30 9 * * ?}")
    public void scanDaily() {
        try {
            Map<String, Object> r = slaAlertService.scan(true);
            log.info("SLA 发货预警定时扫描完成 result={}", r);
        } catch (Exception e) {
            // 定时任务异常必须自行兜住，否则调度线程会中断后续触发
            log.error("SLA 发货预警定时扫描异常", e);
        }
    }
}
