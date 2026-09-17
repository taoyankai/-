-- ============================================================================
--  华中商贸配送中心 · 慰问品配送平台  数据库初始化脚本
--  MySQL 8.0 / utf8mb4
--
--  说明：
--   1. 本脚本在容器首次启动（数据卷为空）时自动执行；
--   2. 后台账号由服务端首次启动时创建（见 DataInitializer），默认 admin / hz@2026；
--   3. 教职工名单正式环境应通过后台 Excel 导入，此处内置 36 条演示数据便于联调。
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- 1. 后台管理员
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_admin`;
CREATE TABLE `t_admin` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `username`       VARCHAR(32)  NOT NULL                COMMENT '登录账号',
  `password`       VARCHAR(255) NOT NULL                COMMENT 'PBKDF2 加密密码',
  `real_name`      VARCHAR(32)  DEFAULT NULL            COMMENT '姓名',
  `role`           VARCHAR(16)  NOT NULL DEFAULT 'operator' COMMENT 'admin/operator/viewer',
  `status`         TINYINT      NOT NULL DEFAULT 1      COMMENT '1 启用 0 停用',
  `last_login_time` DATETIME    DEFAULT NULL,
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '后台管理员';

-- ---------------------------------------------------------------------------
-- 2. 教职工名单（甲方 Excel 导入）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_grantee`;
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
  KEY `idx_phone` (`phone`),
  KEY `idx_org` (`org`),
  KEY `idx_used` (`used`),
  KEY `idx_batch` (`batch_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '教职工领取名单';

-- ---------------------------------------------------------------------------
-- 3. 慰问品套餐
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_package`;
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

DROP TABLE IF EXISTS `t_package_item`;
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

-- ---------------------------------------------------------------------------
-- 4. 配送订单
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_order`;
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
  KEY `idx_order_no` (`order_no`),
  KEY `idx_grantee` (`grantee_id`),
  KEY `idx_status` (`status`),
  KEY `idx_org` (`org`),
  KEY `idx_waybill` (`waybill_no`),
  KEY `idx_sla` (`status`, `sla_deadline`),
  KEY `idx_create` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '配送订单';

-- ---------------------------------------------------------------------------
-- 5. 物流轨迹
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_order_track`;
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

-- ---------------------------------------------------------------------------
-- 6. 收货地址簿
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_address`;
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

-- ---------------------------------------------------------------------------
-- 7. 公告 / 配送说明
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_notice`;
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

-- ---------------------------------------------------------------------------
-- 8. 导入批次
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_import_batch`;
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
  KEY `idx_type_time` (`type`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '导入批次记录';

-- ---------------------------------------------------------------------------
-- 9. 承运商字典
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_carrier`;
CREATE TABLE `t_carrier` (
  `id`        BIGINT      NOT NULL AUTO_INCREMENT,
  `code`      VARCHAR(16) NOT NULL COMMENT '编码',
  `name`      VARCHAR(32) NOT NULL COMMENT '名称',
  `track_url` VARCHAR(255) DEFAULT NULL COMMENT '查询链接模板，{no} 占位',
  `phone`     VARCHAR(32)  DEFAULT NULL COMMENT '客服电话',
  `sort`      INT         NOT NULL DEFAULT 0,
  `status`    TINYINT     NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '承运商字典';

-- ---------------------------------------------------------------------------
-- 10. 操作审计日志
--     涉及个人信息的操作（尤其是名单/订单导出）必须留痕：谁、何时、从哪个 IP、
--     按什么条件、导出了多少条。既是招标文件的数据合规要求，也是事后追溯的依据。
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_operation_log`;
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

-- ---------------------------------------------------------------------------
-- 11. SLA 发货预警记录
--     每日定时扫描待发货订单，按「已超期 / 24 小时内到期 / 72 小时内到期」三档
--     生成预警并通知库房管理员，确保 10 日内必出运单。
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_sla_alert`;
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

-- ============================================================================
--  初始化数据
-- ============================================================================

-- 承运商
INSERT INTO `t_carrier` (`code`, `name`, `track_url`, `phone`, `sort`, `status`) VALUES
('SF',  '顺丰速运', 'https://www.sf-express.com/chn/sc/waybill/list?billcode={no}', '95338', 1, 1),
('JD',  '京东物流', 'https://www.jdl.com/orderSearch?waybillCode={no}',                '950616', 2, 1),
('YTO', '圆通速递', 'https://www.yto.net.cn/query.html?no={no}',                      '95554', 3, 1),
('ZTO', '中通快递', 'https://www.zto.com/express/expressCheck.html?txtBill={no}',     '95311', 4, 1),
('EMS', '中国邮政', 'https://www.ems.com.cn/queryList?mailNum={no}',                  '11183', 5, 1);

-- 套餐
INSERT INTO `t_package` (`id`, `no`, `name`, `sub`, `price`, `stock`, `warn`, `sort`, `status`) VALUES
(1, 'PKA2026', '温暖关怀套餐 A', '米面粮油 · 家庭日常必备', 328.00, 1280, 200, 1, 1),
(2, 'PKB2026', '品质生活套餐 B', '高端食油 · 有机杂粮组合', 458.00,  860, 150, 2, 1),
(3, 'PKC2026', '健康养生套餐 C', '滋补养生 · 关怀长辈首选', 398.00,  640, 120, 3, 1),
(4, 'PKD2026', '团圆礼遇套餐 D', '坚果茶饮 · 节庆走亲必备', 368.00,    0, 100, 4, 1);

INSERT INTO `t_package_item` (`package_id`, `name`, `spec`, `qty`, `unit`, `sort`) VALUES
(1, '五常稻花香大米',   '5kg/袋',  1, '袋', 1),
(1, '一级压榨菜籽油',   '5L/桶',   1, '桶', 2),
(1, '新疆和田红枣',     '500g/袋', 2, '袋', 3),
(2, '特级初榨橄榄油',   '750ml/瓶', 2, '瓶', 1),
(2, '有机杂粮礼盒',     '2.4kg/盒', 1, '盒', 2),
(2, '东北黑木耳',       '250g/袋', 2, '袋', 3),
(3, '多功能养生壶',     '1.5L/台',  1, '台', 1),
(3, '宁夏枸杞原浆',     '300ml/盒', 2, '盒', 2),
(3, '洋槐花蜂蜜',       '500g/瓶',  1, '瓶', 3),
(3, '九蒸九晒黑芝麻丸', '200g/罐',  1, '罐', 4),
(4, '每日坚果混合装',   '750g/盒',  1, '盒', 1),
(4, '恩施玉露茶叶礼盒', '250g/盒',  1, '盒', 2),
(4, '新疆葡萄干',       '500g/袋',  2, '袋', 3);

-- 公告与配送说明
INSERT INTO `t_notice` (`type`, `title`, `content`, `status`, `sort`) VALUES
(2, '配送说明',
 '本项目慰问品由华中商贸配送中心统一供货，全国范围包邮配送（港澳台地区除外）。收到您提交的配送信息后 10 日内安排发货。为保证送达质量，全部订单均要求快递送达至您填写的门牌地址，默认不允许投放至快递代收点；如您确实不便当面签收，可在提交时开启「允许放代收点」授权。',
 1, 1),
(3, '偏远地区配送时效说明',
 '新疆、西藏、内蒙古部分区域、青海、甘肃部分地区因地理位置及物流资源限制，配送时效较全国其他地区延长 3-5 天，但仍在收到配送信息后 10 日发货承诺范围内，敬请理解。',
 1, 2),
(1, '关于慰问品领取的常见问题',
 '1. 每人限领 1 份，提交后不可修改套餐；2. 如填写信息有误，可在「我的订单」中取消后重新提交；3. 快递将送货上门并要求送达至门牌地址，请务必填写到楼栋、单元、门牌号；4. 如遇配送问题，请联系本单位工会或拨打配送中心服务电话。',
 1, 3);

-- 教职工名单（演示数据，正式环境请通过后台 Excel 导入）
INSERT INTO `t_grantee` (`emp_no`, `name`, `phone`, `org`, `dept`, `quota`, `used`, `status`) VALUES
('20180356', '张明远', '13800138000', '河北大学', '计算机科学与技术学院', 1, 0, 1),
('20190012', '李思远', '13900139001', '河北大学', '计算机科学与技术学院', 1, 0, 1),
('20200187', '王雨薇', '13900139002', '河北大学', '计算机科学与技术学院', 1, 0, 1),
('20170553', '刘嘉怡', '13900139003', '河北大学', '质量技术监督学院', 1, 0, 1),
('20210234', '陈子涵', '13900139004', '河北大学', '质量技术监督学院', 1, 0, 1),
('20160298', '杨宇航', '13900139005', '河北大学', '质量技术监督学院', 1, 0, 1),
('20220311', '黄静怡', '13900139006', '河北大学', '临床医学院', 1, 0, 1),
('20180445', '赵文博', '13900139007', '河北大学', '临床医学院', 1, 0, 1),
('20190672', '周晨曦', '13900139008', '河北大学', '临床医学院', 1, 0, 1),
('20200419', '吴浩然', '13900139009', '河北大学', '外国语学院', 1, 0, 1),
('20170886', '徐雅雯', '13900139010', '河北大学', '外国语学院', 1, 0, 1),
('20210528', '孙志强', '13900139011', '河北大学', '电子信息工程学院', 1, 0, 1),
('20160374', '马欣怡', '13900139012', '河北大学', '电子信息工程学院', 1, 0, 1),
('20220763', '朱建国', '13900139013', '河北大学', '电子信息工程学院', 1, 0, 1),
('20180921', '胡晓萌', '13900139014', '河北大学', '文学院', 1, 0, 1),
('20191055', '林泽宇', '13900139015', '河北大学', '文学院', 1, 0, 1),
('20200276', '郭婉如', '13900139016', '河北大学', '文学院', 1, 0, 1),
('20170638', '何立新', '13900139017', '河北大学', '数学与信息科学学院', 1, 0, 1),
('20210694', '高慧敏', '13900139018', '河北大学', '数学与信息科学学院', 1, 0, 1),
('20160517', '罗承志', '13900139019', '河北大学', '教育学院', 1, 0, 1),
('20220832', '郑嘉豪', '13900139020', '河北大学', '教育学院', 1, 0, 1),
('20180079', '梁悦琪', '13900139021', '河北大学', '教育学院', 1, 0, 1),
('20191163', '谢子轩', '13900139022', '河北大学', '化学与环境科学学院', 1, 0, 1),
('20200348', '宋雨桐', '13900139023', '河北大学', '化学与环境科学学院', 1, 0, 1),
('20170725', '唐嘉宁', '13900139024', '河北大学', '化学与环境科学学院', 1, 0, 1),
('20210781', '许志远', '13900139025', '河北大学', '管理学院', 1, 0, 1),
('20160446', '韩雅静', '13900139026', '河北大学', '管理学院', 1, 0, 1),
('20220915', '冯浩宇', '13900139027', '河北大学', '管理学院', 1, 0, 1),
('20180267', '邓思琪', '13900139028', '河北大学', '电子信息工程学院', 1, 0, 1),
('20191328', '曹宇航', '13900139029', '河北大学', '电子信息工程学院', 1, 0, 1),
('20200594', '彭雨欣', '13900139030', '河北大学', '电子信息工程学院', 1, 0, 1),
('20170961', '曾子墨', '13900139031', '河北大学', '计算机科学与技术学院', 1, 0, 1),
('20210847', '肖俊杰', '13900139032', '河北大学', '临床医学院', 1, 0, 1),
('20160683', '田雅琳', '13900139033', '河北大学', '文学院', 1, 0, 1),
('20220729', '董鑫磊', '13900139034', '河北大学', '管理学院', 1, 0, 1),
('20181034', '袁梦洁', '13900139035', '河北大学', '电子信息工程学院', 1, 0, 1),
('10000', '张冲', '15369865664', '河北大学', '后勤处', 1, 0, 1);

-- 演示订单（正式环境可清空）
INSERT INTO `t_order`
(`order_no`, `grantee_id`, `emp_no`, `grantee_name`, `org`, `dept`, `package_id`, `package_name`, `quantity`, `goods_snapshot`,
 `receiver`, `phone`, `province`, `city`, `district`, `detail`, `allow_station`, `remark`, `status`,
 `carrier`, `carrier_name`, `waybill_no`, `create_time`, `sla_deadline`, `ship_time`, `sign_time`,
 `expect_carrier`, `expect_carrier_name`) VALUES
('HZ20260901000001', 1, '20180356', '张明远', '河北大学', '计算机科学与技术学院', 1, '温暖关怀套餐 A', 1,
 '[{"name":"五常稻花香大米","spec":"5kg/袋","qty":1,"unit":"袋"},{"name":"一级压榨菜籽油","spec":"5L/桶","qty":1,"unit":"桶"},{"name":"新疆和田红枣","spec":"500g/袋","qty":2,"unit":"袋"}]',
 '张明远', '13800138000', '河北省', '保定市', '莲池区', '五四东路180号河北大学南院3栋502室', 0, '工作日 9:00-18:00 在家', 40,
 'SF', '顺丰速运', 'SF1345678901234',
 DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), 'SF', '顺丰速运'),
('HZ20260908000002', 2, '20190012', '李思远', '河北大学', '计算机科学与技术学院', 2, '品质生活套餐 B', 1,
 '[{"name":"特级初榨橄榄油","spec":"750ml/瓶","qty":2,"unit":"瓶"},{"name":"有机杂粮礼盒","spec":"2.4kg/盒","qty":1,"unit":"盒"},{"name":"东北黑木耳","spec":"250g/袋","qty":2,"unit":"袋"}]',
 '李思远', '13900139001', '河北省', '保定市', '莲池区', '五四东路180号河北大学东院12栋801室', 0, '', 40,
 'JD', '京东物流', 'JDVA00234567891',
 DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 0 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 'JD', '京东物流'),
('HZ20260910000003', 3, '20200187', '王雨薇', '河北大学', '计算机科学与技术学院', 3, '健康养生套餐 C', 1,
 '[{"name":"多功能养生壶","spec":"1.5L/台","qty":1,"unit":"台"},{"name":"宁夏枸杞原浆","spec":"300ml/盒","qty":2,"unit":"盒"},{"name":"洋槐花蜂蜜","spec":"500g/瓶","qty":1,"unit":"瓶"},{"name":"九蒸九晒黑芝麻丸","spec":"200g/罐","qty":1,"unit":"罐"}]',
 '王雨薇', '13900139002', '河北省', '保定市', '莲池区', '五四东路180号河北大学家属院B座3栋1602室', 0, '放门口即可，谢谢', 20,
 'YTO', '圆通速递', 'YT7788990011223',
 DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, 'YTO', '圆通速递'),
('HZ20260912000004', 16, '20191055', '林泽宇', '河北大学', '文学院', 4, '团圆礼遇套餐 D', 1,
 '[{"name":"每日坚果混合装","spec":"750g/盒","qty":1,"unit":"盒"},{"name":"恩施玉露茶叶礼盒","spec":"250g/盒","qty":1,"unit":"盒"},{"name":"新疆葡萄干","spec":"500g/袋","qty":2,"unit":"袋"}]',
 '林泽宇', '13900139015', '河北省', '保定市', '莲池区', '七一东路2666号河北大学坤舆园1栋201室', 0, '', 20,
 'ZTO', '中通快递', 'ZTO5566778899001',
 DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_ADD(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, 'ZTO', '中通快递'),
('HZ20260913000005', 5, '20210234', '陈子涵', '河北大学', '质量技术监督学院', 2, '品质生活套餐 B', 1,
 '[{"name":"特级初榨橄榄油","spec":"750ml/瓶","qty":2,"unit":"瓶"},{"name":"有机杂粮礼盒","spec":"2.4kg/盒","qty":1,"unit":"盒"},{"name":"东北黑木耳","spec":"250g/袋","qty":2,"unit":"袋"}]',
 '陈子涵', '13900139004', '河北省', '保定市', '莲池区', '七一东路2666号河北大学工学部9栋704室', 0, '', 10,
 'SF', '顺丰速运', 'SF2468013579246',
 DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, 'SF', '顺丰速运'),
('HZ20260913000006', 6, '20160298', '杨宇航', '河北大学', '质量技术监督学院', 3, '健康养生套餐 C', 1,
 '[{"name":"多功能养生壶","spec":"1.5L/台","qty":1,"unit":"台"},{"name":"宁夏枸杞原浆","spec":"300ml/盒","qty":2,"unit":"盒"},{"name":"洋槐花蜂蜜","spec":"500g/瓶","qty":1,"unit":"瓶"},{"name":"九蒸九晒黑芝麻丸","spec":"200g/罐","qty":1,"unit":"罐"}]',
 '杨宇航', '13900139005', '河北省', '保定市', '莲池区', '五四东路180号河北大学紫园7栋1101室', 0, '周末全天在家', 10,
 'EMS', '中国邮政', 'EMS9988776655443',
 DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_ADD(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 'EMS', '中国邮政'),
('HZ20260914000007', 17, '20200276', '郭婉如', '河北大学', '文学院', 1, '温暖关怀套餐 A', 1,
 '[{"name":"五常稻花香大米","spec":"5kg/袋","qty":1,"unit":"袋"},{"name":"一级压榨菜籽油","spec":"5L/桶","qty":1,"unit":"桶"},{"name":"新疆和田红枣","spec":"500g/袋","qty":2,"unit":"袋"}]',
 '郭婉如', '13900139016', '河北省', '保定市', '莲池区', '五四东路180号河北大学家属院12号楼2单元601室', 0, '', 0,
 NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), NULL, NULL, 'YTO', '圆通速递'),
('HZ20260914000008', 18, '20170638', '何立新', '河北大学', '数学与信息科学学院', 2, '品质生活套餐 B', 1,
 '[{"name":"特级初榨橄榄油","spec":"750ml/瓶","qty":2,"unit":"瓶"},{"name":"有机杂粮礼盒","spec":"2.4kg/盒","qty":1,"unit":"盒"},{"name":"东北黑木耳","spec":"250g/袋","qty":2,"unit":"袋"}]',
 '何立新', '13900139017', '河北省', '保定市', '莲池区', '七一东路2666号河北大学九栋505室', 0, '', 0,
 NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), NULL, NULL, 'ZTO', '中通快递'),
('HZ20260915000009', 23, '20191163', '谢子轩', '河北大学', '化学与环境科学学院', 3, '健康养生套餐 C', 1,
 '[{"name":"多功能养生壶","spec":"1.5L/台","qty":1,"unit":"台"},{"name":"宁夏枸杞原浆","spec":"300ml/盒","qty":2,"unit":"盒"},{"name":"洋槐花蜂蜜","spec":"500g/瓶","qty":1,"unit":"瓶"},{"name":"九蒸九晒黑芝麻丸","spec":"200g/罐","qty":1,"unit":"罐"}]',
 '谢子轩', '13900139022', '河北省', '保定市', '莲池区', '五四东路180号河北大学教师公寓4栋202室', 0, '', 0,
 NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 8 DAY), NULL, NULL, 'SF', '顺丰速运'),
('HZ20260915000010', 24, '20200348', '宋雨桐', '河北大学', '化学与环境科学学院', 1, '温暖关怀套餐 A', 1,
 '[{"name":"五常稻花香大米","spec":"5kg/袋","qty":1,"unit":"袋"},{"name":"一级压榨菜籽油","spec":"5L/桶","qty":1,"unit":"桶"},{"name":"新疆和田红枣","spec":"500g/袋","qty":2,"unit":"袋"}]',
 '宋雨桐', '13900139023', '河北省', '保定市', '莲池区', '裕华西路342号河北大学医学部3栋303室', 0, '', 0,
 NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 180 HOUR), DATE_ADD(NOW(), INTERVAL 60 HOUR), NULL, NULL, 'JD', '京东物流'),
('HZ20260915000011', 25, '20170725', '唐嘉宁', '河北大学', '化学与环境科学学院', 2, '品质生活套餐 B', 1,
 '[{"name":"特级初榨橄榄油","spec":"750ml/瓶","qty":2,"unit":"瓶"},{"name":"有机杂粮礼盒","spec":"2.4kg/盒","qty":1,"unit":"盒"},{"name":"东北黑木耳","spec":"250g/袋","qty":2,"unit":"袋"}]',
 '唐嘉宁', '13900139024', '河北省', '保定市', '莲池区', '七一东路2666号河北大学专家公寓2栋1503室', 1, '可放驿站', 0,
 NULL, NULL, NULL,
 DATE_ADD(DATE_SUB(NOW(), INTERVAL 10 DAY), INTERVAL 3 HOUR), DATE_ADD(NOW(), INTERVAL 3 HOUR), NULL, NULL, 'YTO', '圆通速递'),
('HZ20260915000012', 27, '20160446', '韩雅静', '河北大学', '管理学院', 3, '健康养生套餐 C', 1,
 '[{"name":"多功能养生壶","spec":"1.5L/台","qty":1,"unit":"台"},{"name":"宁夏枸杞原浆","spec":"300ml/盒","qty":2,"unit":"盒"},{"name":"洋槐花蜂蜜","spec":"500g/瓶","qty":1,"unit":"瓶"},{"name":"九蒸九晒黑芝麻丸","spec":"200g/罐","qty":1,"unit":"罐"}]',
 '韩雅静', '13900139026', '河北省', '保定市', '莲池区', '七一东路2666号河北大学德明园7栋904室', 0, '', 0,
 NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL, 'EMS', '中国邮政');

-- 同步演示订单的领取状态与轨迹
UPDATE `t_grantee` SET `used` = 1 WHERE `id` IN (1,2,3,5,6,16,17,18,23,24,25,27);

INSERT INTO `t_order_track` (`order_id`, `order_no`, `status`, `description`, `track_time`, `source`) VALUES
(1, 'HZ20260901000001', 0,  '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 12 DAY), 'system'),
(1, 'HZ20260901000001', 10, '您的慰问品已由「顺丰速运」揽收发出，运单号 SF1345678901234，我们将全程跟踪直至送达', DATE_SUB(NOW(), INTERVAL 9 DAY), 'system'),
(1, 'HZ20260901000001', 20, '快件已从【保定中转场】发出，下一站【保定莲池集散点】', DATE_SUB(NOW(), INTERVAL 7 DAY), 'carrier'),
(1, 'HZ20260901000001', 30, '快件正在派送，配送员【李师傅 138****6621】正在为您送货上门', DATE_SUB(NOW(), INTERVAL 4 DAY), 'carrier'),
(1, 'HZ20260901000001', 40, '您的快件已由本人签收，送达至门牌地址，感谢使用', DATE_SUB(NOW(), INTERVAL 4 DAY), 'carrier'),
(5, 'HZ20260913000005', 0,  '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 5 DAY), 'system'),
(5, 'HZ20260913000005', 10, '您的慰问品已由「顺丰速运」揽收发出，运单号 SF2468013579246', DATE_SUB(NOW(), INTERVAL 2 DAY), 'system'),
(6, 'HZ20260913000006', 0,  '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 4 DAY), 'system'),
(6, 'HZ20260913000006', 10, '您的慰问品已由「中国邮政」揽收发出，运单号 EMS9988776655443', DATE_SUB(NOW(), INTERVAL 1 DAY), 'system'),
(7, 'HZ20260914000007', 0,  '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 3 DAY), 'system'),
(8, 'HZ20260914000008', 0,  '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 3 DAY), 'system'),
(9, 'HZ20260915000009', 0,  '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 2 DAY), 'system'),
(12, 'HZ20260915000012', 0, '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 11 DAY), 'system'),
(2, 'HZ20260908000002', 0,  '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 10 DAY), 'system'),
(2, 'HZ20260908000002', 10, '您的慰问品已由「京东物流」揽收发出，运单号 JDVA00234567891', DATE_SUB(NOW(), INTERVAL 7 DAY), 'system'),
(2, 'HZ20260908000002', 30, '快件正在派送，配送员正在为您送货上门', DATE_SUB(NOW(), INTERVAL 3 DAY), 'carrier'),
(2, 'HZ20260908000002', 40, '您的快件已由本人签收，送达至门牌地址，感谢使用', DATE_SUB(NOW(), INTERVAL 3 DAY), 'carrier'),
(3, 'HZ20260910000003', 0,  '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 8 DAY), 'system'),
(3, 'HZ20260910000003', 10, '您的慰问品已由「圆通速递」揽收发出，运单号 YT7788990011223', DATE_SUB(NOW(), INTERVAL 5 DAY), 'system'),
(3, 'HZ20260910000003', 20, '快件已到达【保定集散中心】', DATE_SUB(NOW(), INTERVAL 3 DAY), 'carrier'),
(4, 'HZ20260912000004', 0,  '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 6 DAY), 'system'),
(4, 'HZ20260912000004', 10, '您的慰问品已由「中通快递」揽收发出，运单号 ZTO5566778899001', DATE_SUB(NOW(), INTERVAL 3 DAY), 'system'),
(4, 'HZ20260912000004', 20, '快件已从【保定中转场】发出，下一站【保定莲池集散点】', DATE_SUB(NOW(), INTERVAL 1 DAY), 'carrier');

SET FOREIGN_KEY_CHECKS = 1;
