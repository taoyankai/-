-- 华中商贸配送中心：正式环境首次初始化（MySQL 8.0）
-- 仅供全新空数据库；不是升级/迁移脚本，不要手动导入已有业务数据库。
-- 保留与 init.sql 一致的 12 张表，仅写入承运商基础数据。
-- 不包含演示名单、套餐、公告、订单或轨迹；后台账号由应用启动时补齐。
-- 刻意不使用 DROP TABLE 或 IF NOT EXISTS：已存在表时应报错，避免掩盖结构差异。

SET NAMES utf8mb4;

CREATE TABLE `t_admin` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `username`       VARCHAR(32)  NOT NULL                COMMENT '登录账号',
  `password`       VARCHAR(255) NOT NULL                COMMENT 'PBKDF2 加密密码',
  `real_name`      VARCHAR(32)  DEFAULT NULL            COMMENT '姓名',
  `role`           VARCHAR(16)  NOT NULL DEFAULT 'operator' COMMENT 'admin/operator/viewer',
  `status`         TINYINT      NOT NULL DEFAULT 1      COMMENT '1 启用 0 停用',
  `must_change_password` TINYINT NOT NULL DEFAULT 1     COMMENT '1 首次登录必须修改初始密码',
  `last_login_time` DATETIME    DEFAULT NULL,
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '后台管理员';

CREATE TABLE `t_grantee` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `emp_no`      VARCHAR(32)  DEFAULT NULL               COMMENT '工号',
  `name`        VARCHAR(32)  NOT NULL                   COMMENT '姓名',
  `phone`       VARCHAR(16)  NOT NULL                   COMMENT '手机号（登录标识）',
  `org`         VARCHAR(64)  DEFAULT NULL               COMMENT '所在单位',
  `dept`        VARCHAR(64)  DEFAULT NULL               COMMENT '所在部门',
  `quota`       INT          NOT NULL DEFAULT 1         COMMENT '可领取份数（本项目固定 1）',
  `used`        INT          NOT NULL DEFAULT 0         COMMENT '已领取份数',
  `status`      TINYINT      NOT NULL DEFAULT 1         COMMENT '1 启用 0 停用',
  `batch_id`    BIGINT       DEFAULT NULL               COMMENT '来源导入批次',
  `remark`      VARCHAR(255) DEFAULT NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_grantee_phone` (`phone`),
  KEY `idx_org` (`org`),
  KEY `idx_used` (`used`),
  KEY `idx_batch` (`batch_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '教职工领取名单';

CREATE TABLE `t_package` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT,
  `no`            VARCHAR(32)   DEFAULT NULL              COMMENT '套餐编号',
  `name`          VARCHAR(64)   NOT NULL                  COMMENT '套餐名称',
  `sub`           VARCHAR(128)  DEFAULT NULL              COMMENT '副标题',
  `cover`         VARCHAR(255)  DEFAULT NULL              COMMENT '封面图',
  `price`         DECIMAL(10,2) DEFAULT 0.00              COMMENT '参考价（仅展示）',
  `stock`         INT           NOT NULL DEFAULT 0        COMMENT '库存',
  `warn`          INT           NOT NULL DEFAULT 0        COMMENT '库存预警阈值',
  `sort`          INT           NOT NULL DEFAULT 0,
  `status`        TINYINT       NOT NULL DEFAULT 1        COMMENT '1 上架 0 下架',
  `change_reason` VARCHAR(255)  DEFAULT NULL              COMMENT '变更留痕',
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_status_sort` (`status`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '慰问品套餐';

CREATE TABLE `t_package_item` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `package_id` BIGINT       NOT NULL,
  `name`       VARCHAR(64)  NOT NULL COMMENT '商品名称',
  `spec`       VARCHAR(64)  DEFAULT NULL COMMENT '规格',
  `qty`        INT          NOT NULL DEFAULT 1,
  `unit`       VARCHAR(16)  DEFAULT NULL,
  `sort`       INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_package` (`package_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '套餐商品明细';

CREATE TABLE `t_order` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `order_no`        VARCHAR(32)  NOT NULL                COMMENT '订单号 HZ+日期+序号',
  `grantee_id`      BIGINT       NOT NULL,
  `emp_no`          VARCHAR(32)  DEFAULT NULL,
  `grantee_name`    VARCHAR(32)  DEFAULT NULL,
  `org`             VARCHAR(64)  DEFAULT NULL,
  `dept`            VARCHAR(64)  DEFAULT NULL,
  `package_id`      BIGINT       DEFAULT NULL,
  `package_name`    VARCHAR(64)  DEFAULT NULL,
  `quantity`        INT          NOT NULL DEFAULT 1,
  `goods_snapshot`  TEXT         COMMENT '套餐内容快照 JSON',
  `receiver`        VARCHAR(32)  NOT NULL COMMENT '收件人',
  `phone`           VARCHAR(16)  NOT NULL COMMENT '收件电话',
  `province`        VARCHAR(32)  DEFAULT NULL,
  `city`            VARCHAR(32)  DEFAULT NULL,
  `district`        VARCHAR(32)  DEFAULT NULL,
  `detail`          VARCHAR(255) DEFAULT NULL COMMENT '详细地址（含门牌）',
  `allow_station`   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否允许放代收点 0否',
  `remark`          VARCHAR(255) DEFAULT NULL,
  `addr_change_count` TINYINT    NOT NULL DEFAULT 0 COMMENT '收货地址自助修改次数（上限 1 次，发货后锁定）',
  `expect_carrier`  VARCHAR(16)  DEFAULT NULL COMMENT '用户选择的期望承运商编码（包邮自选）',
  `expect_carrier_name` VARCHAR(32) DEFAULT NULL COMMENT '期望承运商名称',
  `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '0待发货 10已发货 20运输中 30派送中 40已签收 50已取消 60异常 70退回',
  `carrier`         VARCHAR(16)  DEFAULT NULL,
  `carrier_name`    VARCHAR(32)  DEFAULT NULL,
  `waybill_no`      VARCHAR(64)  DEFAULT NULL,
  `exception_reason` VARCHAR(255) DEFAULT NULL,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `sla_deadline`    DATETIME     DEFAULT NULL COMMENT '承诺发货截止时间',
  `ship_time`       DATETIME     DEFAULT NULL,
  `sign_time`       DATETIME     DEFAULT NULL,
  `cancel_time`     DATETIME     DEFAULT NULL,
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_order_no` (`order_no`),
  KEY `idx_grantee` (`grantee_id`),
  KEY `idx_status` (`status`),
  KEY `idx_org` (`org`),
  UNIQUE KEY `uk_order_waybill_no` (`waybill_no`),
  KEY `idx_sla` (`status`, `sla_deadline`),
  KEY `idx_create` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '配送订单';

CREATE TABLE `t_order_track` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `order_id`    BIGINT       NOT NULL,
  `order_no`    VARCHAR(32)  DEFAULT NULL,
  `status`      TINYINT      DEFAULT NULL COMMENT '节点对应订单状态',
  `description` VARCHAR(255) NOT NULL,
  `track_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `source`      VARCHAR(16)  DEFAULT 'system' COMMENT 'system/manual/carrier',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`, `track_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '物流轨迹';

CREATE TABLE `t_address` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `grantee_id`    BIGINT       NOT NULL,
  `name`          VARCHAR(32)  NOT NULL,
  `phone`         VARCHAR(16)  NOT NULL,
  `province`      VARCHAR(32)  DEFAULT NULL,
  `city`          VARCHAR(32)  DEFAULT NULL,
  `district`      VARCHAR(32)  DEFAULT NULL,
  `detail`        VARCHAR(255) NOT NULL,
  `allow_station` TINYINT      NOT NULL DEFAULT 0,
  `is_default`    TINYINT      NOT NULL DEFAULT 0,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_grantee` (`grantee_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收货地址簿';

CREATE TABLE `t_notice` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `type`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1公告 2配送说明 3时效说明',
  `title`       VARCHAR(128) NOT NULL,
  `content`     TEXT,
  `status`      TINYINT      NOT NULL DEFAULT 1,
  `sort`        INT          NOT NULL DEFAULT 0,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '公告与说明';

CREATE TABLE `t_import_batch` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `batch_no`      VARCHAR(32)  NOT NULL,
  `type`          VARCHAR(16)  NOT NULL COMMENT 'grantee 名单 / waybill 运单',
  `file_name`     VARCHAR(255) DEFAULT NULL,
  `total_count`   INT          NOT NULL DEFAULT 0,
  `success_count` INT          NOT NULL DEFAULT 0,
  `fail_count`    INT          NOT NULL DEFAULT 0,
  `fail_detail`   TEXT,
  `operator`      VARCHAR(32)  DEFAULT NULL,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_import_batch_no` (`batch_no`),
  KEY `idx_type_time` (`type`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '导入批次记录';

CREATE TABLE `t_carrier` (
  `id`        BIGINT      NOT NULL AUTO_INCREMENT,
  `code`      VARCHAR(16) NOT NULL COMMENT '编码',
  `name`      VARCHAR(32) NOT NULL COMMENT '名称',
  `track_url` VARCHAR(255) DEFAULT NULL COMMENT '查询链接模板，{no} 占位',
  `phone`     VARCHAR(32)  DEFAULT NULL COMMENT '客服电话',
  `sort`      INT         NOT NULL DEFAULT 0,
  `status`    TINYINT     NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_carrier_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '承运商字典';

CREATE TABLE `t_operation_log` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT,
  `admin_id`      BIGINT        DEFAULT NULL COMMENT '操作人 ID',
  `admin_account` VARCHAR(64)   DEFAULT NULL COMMENT '操作人账号',
  `admin_name`    VARCHAR(64)   DEFAULT NULL COMMENT '操作人姓名',
  `admin_role`    VARCHAR(32)   DEFAULT NULL COMMENT '操作人角色',
  `module`        VARCHAR(32)   NOT NULL COMMENT '模块 grantee/order/package/notice/sms',
  `action`        VARCHAR(32)   NOT NULL COMMENT '动作 export/import/delete/reset-quota',
  `target`        VARCHAR(255)  DEFAULT NULL COMMENT '操作对象或范围描述',
  `detail`        VARCHAR(1024) DEFAULT NULL COMMENT '明细：筛选条件、影响条数',
  `row_count`     INT           DEFAULT NULL COMMENT '涉及记录数',
  `ip`            VARCHAR(64)   DEFAULT NULL COMMENT '客户端 IP',
  `user_agent`    VARCHAR(255)  DEFAULT NULL COMMENT '浏览器标识',
  `result`        VARCHAR(16)   NOT NULL DEFAULT 'success' COMMENT 'success/fail',
  `error_msg`     VARCHAR(512)  DEFAULT NULL COMMENT '失败原因',
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_time` (`create_time`),
  KEY `idx_admin` (`admin_id`),
  KEY `idx_module_action` (`module`, `action`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作审计日志';

CREATE TABLE `t_sla_alert` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `alert_date`  DATE         NOT NULL COMMENT '预警日期',
  `level`       TINYINT      NOT NULL COMMENT '3=已超期 2=24小时内 1=72小时内',
  `order_count` INT          NOT NULL DEFAULT 0 COMMENT '涉及订单数',
  `order_nos`   VARCHAR(1024) DEFAULT NULL COMMENT '订单号清单（逗号分隔，最多 30 条）',
  `notified`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已短信通知',
  `notify_resp` VARCHAR(255) DEFAULT NULL COMMENT '通知结果（短信平台原文）',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_date_level` (`alert_date`, `level`),
  KEY `idx_date` (`alert_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'SLA 发货预警记录';

-- 承运商基础数据
INSERT INTO `t_carrier` (`code`, `name`, `track_url`, `phone`, `sort`, `status`) VALUES
('SF',  '顺丰速运', 'https://www.sf-express.com/chn/sc/waybill/list?billcode={no}', '95338', 1, 1),
('JD',  '京东物流', 'https://www.jdl.com/orderSearch?waybillCode={no}',                '950616', 2, 1),
('YTO', '圆通速递', 'https://www.yto.net.cn/query.html?no={no}',                      '95554', 3, 1),
('ZTO', '中通快递', 'https://www.zto.com/express/expressCheck.html?txtBill={no}',     '95311', 4, 1),
('STO', '申通快递', 'https://www.sto.cn/',                                             '95543', 5, 1),
('EMS', '中国邮政', 'https://www.ems.com.cn/queryList?mailNum={no}',                  '11183', 6, 1);
