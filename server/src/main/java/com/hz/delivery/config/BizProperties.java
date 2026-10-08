package com.hz.delivery.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务参数配置，对应 application.yml 中的 hz.delivery.*
 */
@Data
@Component
@ConfigurationProperties(prefix = "hz.delivery")
public class BizProperties {

    /** 承诺发货天数（招标要求：收到配送信息后 10 日内发货） */
    private int slaDays = 10;

    /** 每名教职工可领取套餐份数，本项目为 1（每人只能选择一个套餐） */
    private int defaultQuota = 1;

    /** 验证码有效期（秒） */
    private long codeTtl = 300;

    /**
     * 演示环境固定验证码。
     * 仅在短信通道为 mock 时生效；一旦接入真实通道（provider≠mock），本项会被忽略，
     * 验证码改为随机 6 位，避免「真实短信 + 固定验证码」并存造成的越权风险。
     */
    private String mockSmsCode = "123456";

    /** 是否默认允许放代收点（招标要求：默认不允许） */
    private boolean allowStationDefault = false;

    /**
     * C 端登录方式：
     * name=手机号+姓名核验（不依赖短信，仅演示环境使用）；
     * sms=短信验证码（正式使用，默认值）。
     *
     * <p>注意：这里只是代码兜底默认值，实际以环境变量 LOGIN_MODE 为准。
     */
    private String loginMode = "sms";

    /** 同一手机号连续核验失败达到该次数即临时锁定，防止枚举同事姓名/暴力猜测验证码冒领 */
    private int loginMaxFail = 5;

    /** 登录失败锁定时长（分钟），同时作为失败计数的统计窗口 */
    private int loginLockMinutes = 10;

    /** 后台账号连续登录失败上限（账号与来源 IP 分别计数） */
    private int adminLoginMaxFail = 5;

    /** 后台登录失败锁定时长（分钟） */
    private int adminLoginLockMinutes = 10;

    /** 仅供确有跨域需求的管理页面使用；同源部署不需要加入列表 */
    private List<String> corsAllowedOrigins = new ArrayList<>(List.of(
            "http://localhost", "http://127.0.0.1"));

    /**
     * 同一手机号每日验证码发送上限。
     *
     * <p>验证码只有 60 秒频控，若不设日限，有人可对名单内号码轮流轰炸，
     * 既消耗短信费用又骚扰教职工，故再加一道每日总量闸门。
     */
    private int smsDailyLimit = 10;

    /**
     * SLA 发货超时预警的短信接收号码（多个用英文逗号分隔）。
     * 留空则只落库供后台看板标红、不发送短信，避免演示环境误发打扰。
     */
    private String slaAlertPhone = "";

    /** 短信通道配置 */
    private Sms sms = new Sms();

    /** 真实物流轨迹查询配置 */
    private Logistics logistics = new Logistics();

    @Data
    public static class Logistics {

        /** disabled=关闭 / kuaidi100=快递100企业版实时查询 */
        private String provider = "disabled";

        /** 用户主动查询运单时是否尝试刷新真实轨迹 */
        private boolean queryOnAccess = true;

        /** 发货前是否必须通过真实接口校验承运商与运单号 */
        private boolean validateOnShip = true;

        /** 同一运单最短查询间隔；快递100官方要求不得低于 30 分钟 */
        private int minIntervalMinutes = 30;

        /** 定时任务每批最多同步的在途订单数 */
        private int batchSize = 50;

        private Kuaidi100 kuaidi100 = new Kuaidi100();

        @Data
        public static class Kuaidi100 {
            private String baseUrl = "https://poll.kuaidi100.com/poll/query.do";
            /** 企业管理后台中的授权 key */
            private String key = "";
            /** 企业管理后台中的 customer 授权码 */
            private String customer = "";
            private int connectTimeout = 5;
            private int readTimeout = 10;
        }
    }

    @Data
    public static class Sms {

        /** 通道标识：mock=演示 / hbu=河北大学统一短信平台 / aliyun=阿里云 */
        private String provider = "mock";

        /** 是否开放后台「短信通道自检」接口；生产环境建议关闭 */
        private boolean testEnabled = false;

        /** 河北大学统一短信服务平台 */
        private Hbu hbu = new Hbu();

        @Data
        public static class Hbu {
            /** POST 正式接口地址 */
            private String baseUrl = "http://sim.hbu.edu.cn/sms-hbu/api";
            /** 客户端 ID */
            private String cid = "7";
            /** 接口密钥（建议通过环境变量 SMS_HBU_KEY 注入，不要写进代码库） */
            private String key = "";
            /** 验证码模板 model */
            private String model = "yzmmb";
            /** 模板中验证码有效期的分钟数（模板变量 time） */
            private String timeMinutes = "5";
            /** 自定义正文时的签名前缀说明：平台会自动加签名，这里仅用于本地校验字数 */
            private int maxTextLength = 60;
            /** 连接超时（秒） */
            private int connectTimeout = 5;
            /** 读取超时（秒） */
            private int readTimeout = 10;
        }
    }
}
