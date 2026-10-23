-- 已建表空业务库：一次性演示数据，不是重建数据库脚本。
-- 执行前备份并暂停后端写入；mysql 必须不带 --force，出错须断开连接回滚。
-- 保留后台账号、密码、承运商和既有审计日志；任何业务表非空即拒绝。
-- 4 套餐、13 明细、37 停用人员、3 公告、12 订单、23 轨迹。
-- 名单号码为 00000000001 等不可收码占位值，禁止改为真实短信测试名单。
-- 使用显式人员/订单 ID 保证关联，不依赖 AUTO_INCREMENT 起始值。
-- 所有业务写入及结果断言处于同一事务，不关闭外键检查。

SET NAMES utf8mb4;
START TRANSACTION;
CREATE TEMPORARY TABLE `_hz_demo_seed_guard` (
    allowed TINYINT NOT NULL,
    CONSTRAINT `demo_requires_empty_business_tables_and_valid_result` CHECK (allowed = 1)
) ENGINE=InnoDB;
INSERT INTO `_hz_demo_seed_guard` (allowed)
SELECT IF(
    (SELECT COUNT(*) FROM `t_package`) = 0
    AND (SELECT COUNT(*) FROM `t_package_item`) = 0
    AND (SELECT COUNT(*) FROM `t_grantee`) = 0
    AND (SELECT COUNT(*) FROM `t_order`) = 0
    AND (SELECT COUNT(*) FROM `t_order_track`) = 0
    AND (SELECT COUNT(*) FROM `t_notice`) = 0
    AND (SELECT COUNT(*) FROM `t_address`) = 0
    AND (SELECT COUNT(*) FROM `t_import_batch`) = 0
    AND (SELECT COUNT(*) FROM `t_sla_alert`) = 0, 1, 0);

-- 套餐
INSERT INTO `t_package` (`id`, `no`, `name`, `sub`, `price`, `stock`, `warn`, `sort`, `status`) VALUES
(1, 'DEMO-PKA2026', '【演示】温暖关怀套餐 A', '米面粮油 · 家庭日常必备', 328.00, 1280, 200, 1, 1),
(2, 'DEMO-PKB2026', '【演示】品质生活套餐 B', '高端食油 · 有机杂粮组合', 458.00,  860, 150, 2, 1),
(3, 'DEMO-PKC2026', '【演示】健康养生套餐 C', '滋补养生 · 关怀长辈首选', 398.00,  640, 120, 3, 1),
(4, 'DEMO-PKD2026', '【演示】团圆礼遇套餐 D', '坚果茶饮 · 节庆走亲必备', 368.00,    0, 100, 4, 1);

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
(2, '【演示】配送说明',
 '【演示内容，不构成实际配送承诺】本项目慰问品由华中商贸配送中心统一供货，全国范围包邮配送（港澳台地区除外）。收到您提交的配送信息后 10 日内安排发货。为保证送达质量，全部订单均要求快递送达至您填写的门牌地址，默认不允许投放至快递代收点；如您确实不便当面签收，可在提交时开启「允许放代收点」授权。',
 1, 1),
(3, '【演示】偏远地区配送时效说明',
 '【演示内容，不构成实际配送承诺】新疆、西藏、内蒙古部分区域、青海、甘肃部分地区因地理位置及物流资源限制，配送时效较全国其他地区延长 3-5 天，但仍在收到配送信息后 10 日发货承诺范围内，敬请理解。',
 1, 2),
(1, '【演示】关于慰问品领取的常见问题',
 '【演示内容，不构成实际配送承诺】1. 每人限领 1 份，提交后不可修改套餐；2. 如填写信息有误，可在「我的订单」中取消后重新提交；3. 快递将送货上门并要求送达至门牌地址，请务必填写到楼栋、单元、门牌号；4. 如遇配送问题，请联系本单位工会或拨打配送中心服务电话。',
 1, 3);

-- 教职工名单（演示数据，正式环境请通过后台 Excel 导入）
INSERT INTO `t_grantee` (`id`, `emp_no`, `name`, `phone`, `org`, `dept`, `quota`, `used`, `status`) VALUES
(1, 'DEMO-20180356', '【演示】张明远', '00000000001', '演示单位', '计算机科学与技术学院', 1, 0, 0),
(2, 'DEMO-20190012', '【演示】李思远', '00000000002', '演示单位', '计算机科学与技术学院', 1, 0, 0),
(3, 'DEMO-20200187', '【演示】王雨薇', '00000000003', '演示单位', '计算机科学与技术学院', 1, 0, 0),
(4, 'DEMO-20170553', '【演示】刘嘉怡', '00000000004', '演示单位', '质量技术监督学院', 1, 0, 0),
(5, 'DEMO-20210234', '【演示】陈子涵', '00000000005', '演示单位', '质量技术监督学院', 1, 0, 0),
(6, 'DEMO-20160298', '【演示】杨宇航', '00000000006', '演示单位', '质量技术监督学院', 1, 0, 0),
(7, 'DEMO-20220311', '【演示】黄静怡', '00000000007', '演示单位', '临床医学院', 1, 0, 0),
(8, 'DEMO-20180445', '【演示】赵文博', '00000000008', '演示单位', '临床医学院', 1, 0, 0),
(9, 'DEMO-20190672', '【演示】周晨曦', '00000000009', '演示单位', '临床医学院', 1, 0, 0),
(10, 'DEMO-20200419', '【演示】吴浩然', '00000000010', '演示单位', '外国语学院', 1, 0, 0),
(11, 'DEMO-20170886', '【演示】徐雅雯', '00000000011', '演示单位', '外国语学院', 1, 0, 0),
(12, 'DEMO-20210528', '【演示】孙志强', '00000000012', '演示单位', '电子信息工程学院', 1, 0, 0),
(13, 'DEMO-20160374', '【演示】马欣怡', '00000000013', '演示单位', '电子信息工程学院', 1, 0, 0),
(14, 'DEMO-20220763', '【演示】朱建国', '00000000014', '演示单位', '电子信息工程学院', 1, 0, 0),
(15, 'DEMO-20180921', '【演示】胡晓萌', '00000000015', '演示单位', '文学院', 1, 0, 0),
(16, 'DEMO-20191055', '【演示】林泽宇', '00000000016', '演示单位', '文学院', 1, 0, 0),
(17, 'DEMO-20200276', '【演示】郭婉如', '00000000017', '演示单位', '文学院', 1, 0, 0),
(18, 'DEMO-20170638', '【演示】何立新', '00000000018', '演示单位', '数学与信息科学学院', 1, 0, 0),
(19, 'DEMO-20210694', '【演示】高慧敏', '00000000019', '演示单位', '数学与信息科学学院', 1, 0, 0),
(20, 'DEMO-20160517', '【演示】罗承志', '00000000020', '演示单位', '教育学院', 1, 0, 0),
(21, 'DEMO-20220832', '【演示】郑嘉豪', '00000000021', '演示单位', '教育学院', 1, 0, 0),
(22, 'DEMO-20180079', '【演示】梁悦琪', '00000000022', '演示单位', '教育学院', 1, 0, 0),
(23, 'DEMO-20191163', '【演示】谢子轩', '00000000023', '演示单位', '化学与环境科学学院', 1, 0, 0),
(24, 'DEMO-20200348', '【演示】宋雨桐', '00000000024', '演示单位', '化学与环境科学学院', 1, 0, 0),
(25, 'DEMO-20170725', '【演示】唐嘉宁', '00000000025', '演示单位', '化学与环境科学学院', 1, 0, 0),
(26, 'DEMO-20210781', '【演示】许志远', '00000000026', '演示单位', '管理学院', 1, 0, 0),
(27, 'DEMO-20160446', '【演示】韩雅静', '00000000027', '演示单位', '管理学院', 1, 0, 0),
(28, 'DEMO-20220915', '【演示】冯浩宇', '00000000028', '演示单位', '管理学院', 1, 0, 0),
(29, 'DEMO-20180267', '【演示】邓思琪', '00000000029', '演示单位', '电子信息工程学院', 1, 0, 0),
(30, 'DEMO-20191328', '【演示】曹宇航', '00000000030', '演示单位', '电子信息工程学院', 1, 0, 0),
(31, 'DEMO-20200594', '【演示】彭雨欣', '00000000031', '演示单位', '电子信息工程学院', 1, 0, 0),
(32, 'DEMO-20170961', '【演示】曾子墨', '00000000032', '演示单位', '计算机科学与技术学院', 1, 0, 0),
(33, 'DEMO-20210847', '【演示】肖俊杰', '00000000033', '演示单位', '临床医学院', 1, 0, 0),
(34, 'DEMO-20160683', '【演示】田雅琳', '00000000034', '演示单位', '文学院', 1, 0, 0),
(35, 'DEMO-20220729', '【演示】董鑫磊', '00000000035', '演示单位', '管理学院', 1, 0, 0),
(36, 'DEMO-20181034', '【演示】袁梦洁', '00000000036', '演示单位', '电子信息工程学院', 1, 0, 0),
(37, 'DEMO-10000', '【演示】张冲', '00000000037', '演示单位', '后勤处', 1, 0, 0);

-- 演示订单（正式环境可清空）
INSERT INTO `t_order`
(`id`, `order_no`, `grantee_id`, `emp_no`, `grantee_name`, `org`, `dept`, `package_id`, `package_name`, `quantity`, `goods_snapshot`,
 `receiver`, `phone`, `province`, `city`, `district`, `detail`, `allow_station`, `remark`, `status`,
 `carrier`, `carrier_name`, `waybill_no`, `create_time`, `sla_deadline`, `ship_time`, `sign_time`,
 `expect_carrier`, `expect_carrier_name`) VALUES
(1, 'DEMO20260901000001', 1, 'DEMO-20180356', '【演示】张明远', '演示单位', '计算机科学与技术学院', 1, '【演示】温暖关怀套餐 A', 1,
 '[{"name":"五常稻花香大米","spec":"5kg/袋","qty":1,"unit":"袋"},{"name":"一级压榨菜籽油","spec":"5L/桶","qty":1,"unit":"桶"},{"name":"新疆和田红枣","spec":"500g/袋","qty":2,"unit":"袋"}]',
 '【演示】张明远', '00000000001', '河北省', '保定市', '莲池区', '【演示地址】五四东路180号河北大学南院3栋502室', 0, '工作日 9:00-18:00 在家', 40,
 'SF', '顺丰速运', 'DEMO-SF1345678901234',
 DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), 'SF', '顺丰速运'),
(2, 'DEMO20260908000002', 2, 'DEMO-20190012', '【演示】李思远', '演示单位', '计算机科学与技术学院', 2, '【演示】品质生活套餐 B', 1,
 '[{"name":"特级初榨橄榄油","spec":"750ml/瓶","qty":2,"unit":"瓶"},{"name":"有机杂粮礼盒","spec":"2.4kg/盒","qty":1,"unit":"盒"},{"name":"东北黑木耳","spec":"250g/袋","qty":2,"unit":"袋"}]',
 '【演示】李思远', '00000000002', '河北省', '保定市', '莲池区', '【演示地址】五四东路180号河北大学东院12栋801室', 0, '', 40,
 'JD', '京东物流', 'DEMO-JDVA00234567891',
 DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 0 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 'JD', '京东物流'),
(3, 'DEMO20260910000003', 3, 'DEMO-20200187', '【演示】王雨薇', '演示单位', '计算机科学与技术学院', 3, '【演示】健康养生套餐 C', 1,
 '[{"name":"多功能养生壶","spec":"1.5L/台","qty":1,"unit":"台"},{"name":"宁夏枸杞原浆","spec":"300ml/盒","qty":2,"unit":"盒"},{"name":"洋槐花蜂蜜","spec":"500g/瓶","qty":1,"unit":"瓶"},{"name":"九蒸九晒黑芝麻丸","spec":"200g/罐","qty":1,"unit":"罐"}]',
 '【演示】王雨薇', '00000000003', '河北省', '保定市', '莲池区', '【演示地址】五四东路180号河北大学家属院B座3栋1602室', 0, '放门口即可，谢谢', 20,
 'YTO', '圆通速递', 'DEMO-YT7788990011223',
 DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, 'YTO', '圆通速递'),
(4, 'DEMO20260912000004', 16, 'DEMO-20191055', '【演示】林泽宇', '演示单位', '文学院', 4, '【演示】团圆礼遇套餐 D', 1,
 '[{"name":"每日坚果混合装","spec":"750g/盒","qty":1,"unit":"盒"},{"name":"恩施玉露茶叶礼盒","spec":"250g/盒","qty":1,"unit":"盒"},{"name":"新疆葡萄干","spec":"500g/袋","qty":2,"unit":"袋"}]',
 '【演示】林泽宇', '00000000016', '河北省', '保定市', '莲池区', '【演示地址】七一东路2666号河北大学坤舆园1栋201室', 0, '', 20,
 'ZTO', '中通快递', 'DEMO-ZTO5566778899001',
 DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_ADD(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, 'ZTO', '中通快递'),
(5, 'DEMO20260913000005', 5, 'DEMO-20210234', '【演示】陈子涵', '演示单位', '质量技术监督学院', 2, '【演示】品质生活套餐 B', 1,
 '[{"name":"特级初榨橄榄油","spec":"750ml/瓶","qty":2,"unit":"瓶"},{"name":"有机杂粮礼盒","spec":"2.4kg/盒","qty":1,"unit":"盒"},{"name":"东北黑木耳","spec":"250g/袋","qty":2,"unit":"袋"}]',
 '【演示】陈子涵', '00000000005', '河北省', '保定市', '莲池区', '【演示地址】七一东路2666号河北大学工学部9栋704室', 0, '', 10,
 'SF', '顺丰速运', 'DEMO-SF2468013579246',
 DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, 'SF', '顺丰速运'),
(6, 'DEMO20260913000006', 6, 'DEMO-20160298', '【演示】杨宇航', '演示单位', '质量技术监督学院', 3, '【演示】健康养生套餐 C', 1,
 '[{"name":"多功能养生壶","spec":"1.5L/台","qty":1,"unit":"台"},{"name":"宁夏枸杞原浆","spec":"300ml/盒","qty":2,"unit":"盒"},{"name":"洋槐花蜂蜜","spec":"500g/瓶","qty":1,"unit":"瓶"},{"name":"九蒸九晒黑芝麻丸","spec":"200g/罐","qty":1,"unit":"罐"}]',
 '【演示】杨宇航', '00000000006', '河北省', '保定市', '莲池区', '【演示地址】五四东路180号河北大学紫园7栋1101室', 0, '周末全天在家', 10,
 'EMS', '中国邮政', 'DEMO-EMS9988776655443',
 DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_ADD(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 'EMS', '中国邮政'),
(7, 'DEMO20260914000007', 17, 'DEMO-20200276', '【演示】郭婉如', '演示单位', '文学院', 1, '【演示】温暖关怀套餐 A', 1,
 '[{"name":"五常稻花香大米","spec":"5kg/袋","qty":1,"unit":"袋"},{"name":"一级压榨菜籽油","spec":"5L/桶","qty":1,"unit":"桶"},{"name":"新疆和田红枣","spec":"500g/袋","qty":2,"unit":"袋"}]',
 '【演示】郭婉如', '00000000017', '河北省', '保定市', '莲池区', '【演示地址】五四东路180号河北大学家属院12号楼2单元601室', 0, '', 0,
 NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), NULL, NULL, 'YTO', '圆通速递'),
(8, 'DEMO20260914000008', 18, 'DEMO-20170638', '【演示】何立新', '演示单位', '数学与信息科学学院', 2, '【演示】品质生活套餐 B', 1,
 '[{"name":"特级初榨橄榄油","spec":"750ml/瓶","qty":2,"unit":"瓶"},{"name":"有机杂粮礼盒","spec":"2.4kg/盒","qty":1,"unit":"盒"},{"name":"东北黑木耳","spec":"250g/袋","qty":2,"unit":"袋"}]',
 '【演示】何立新', '00000000018', '河北省', '保定市', '莲池区', '【演示地址】七一东路2666号河北大学九栋505室', 0, '', 0,
 NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), NULL, NULL, 'ZTO', '中通快递'),
(9, 'DEMO20260915000009', 23, 'DEMO-20191163', '【演示】谢子轩', '演示单位', '化学与环境科学学院', 3, '【演示】健康养生套餐 C', 1,
 '[{"name":"多功能养生壶","spec":"1.5L/台","qty":1,"unit":"台"},{"name":"宁夏枸杞原浆","spec":"300ml/盒","qty":2,"unit":"盒"},{"name":"洋槐花蜂蜜","spec":"500g/瓶","qty":1,"unit":"瓶"},{"name":"九蒸九晒黑芝麻丸","spec":"200g/罐","qty":1,"unit":"罐"}]',
 '【演示】谢子轩', '00000000023', '河北省', '保定市', '莲池区', '【演示地址】五四东路180号河北大学教师公寓4栋202室', 0, '', 0,
 NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 8 DAY), NULL, NULL, 'SF', '顺丰速运'),
(10, 'DEMO20260915000010', 24, 'DEMO-20200348', '【演示】宋雨桐', '演示单位', '化学与环境科学学院', 1, '【演示】温暖关怀套餐 A', 1,
 '[{"name":"五常稻花香大米","spec":"5kg/袋","qty":1,"unit":"袋"},{"name":"一级压榨菜籽油","spec":"5L/桶","qty":1,"unit":"桶"},{"name":"新疆和田红枣","spec":"500g/袋","qty":2,"unit":"袋"}]',
 '【演示】宋雨桐', '00000000024', '河北省', '保定市', '莲池区', '【演示地址】裕华西路342号河北大学医学部3栋303室', 0, '', 0,
 NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 180 HOUR), DATE_ADD(NOW(), INTERVAL 60 HOUR), NULL, NULL, 'JD', '京东物流'),
(11, 'DEMO20260915000011', 25, 'DEMO-20170725', '【演示】唐嘉宁', '演示单位', '化学与环境科学学院', 2, '【演示】品质生活套餐 B', 1,
 '[{"name":"特级初榨橄榄油","spec":"750ml/瓶","qty":2,"unit":"瓶"},{"name":"有机杂粮礼盒","spec":"2.4kg/盒","qty":1,"unit":"盒"},{"name":"东北黑木耳","spec":"250g/袋","qty":2,"unit":"袋"}]',
 '【演示】唐嘉宁', '00000000025', '河北省', '保定市', '莲池区', '【演示地址】七一东路2666号河北大学专家公寓2栋1503室', 1, '可放驿站', 0,
 NULL, NULL, NULL,
 DATE_ADD(DATE_SUB(NOW(), INTERVAL 10 DAY), INTERVAL 3 HOUR), DATE_ADD(NOW(), INTERVAL 3 HOUR), NULL, NULL, 'YTO', '圆通速递'),
(12, 'DEMO20260915000012', 27, 'DEMO-20160446', '【演示】韩雅静', '演示单位', '管理学院', 3, '【演示】健康养生套餐 C', 1,
 '[{"name":"多功能养生壶","spec":"1.5L/台","qty":1,"unit":"台"},{"name":"宁夏枸杞原浆","spec":"300ml/盒","qty":2,"unit":"盒"},{"name":"洋槐花蜂蜜","spec":"500g/瓶","qty":1,"unit":"瓶"},{"name":"九蒸九晒黑芝麻丸","spec":"200g/罐","qty":1,"unit":"罐"}]',
 '【演示】韩雅静', '00000000027', '河北省', '保定市', '莲池区', '【演示地址】七一东路2666号河北大学德明园7栋904室', 0, '', 0,
 NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL, 'EMS', '中国邮政');

-- 同步演示订单的领取状态与轨迹
UPDATE `t_grantee` SET `used` = 1 WHERE `id` IN (1,2,3,5,6,16,17,18,23,24,25,27);

INSERT INTO `t_order_track` (`order_id`, `order_no`, `status`, `description`, `track_time`, `source`) VALUES
(1, 'DEMO20260901000001', 0,  '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 12 DAY), 'system'),
(1, 'DEMO20260901000001', 10, '您的慰问品已由「顺丰速运」揽收发出，运单号 DEMO-SF1345678901234，我们将全程跟踪直至送达', DATE_SUB(NOW(), INTERVAL 9 DAY), 'system'),
(1, 'DEMO20260901000001', 20, '快件已从【保定中转场】发出，下一站【保定莲池集散点】', DATE_SUB(NOW(), INTERVAL 7 DAY), 'carrier'),
(1, 'DEMO20260901000001', 30, '快件正在派送，配送员【李师傅 138****6621】正在为您送货上门', DATE_SUB(NOW(), INTERVAL 4 DAY), 'carrier'),
(1, 'DEMO20260901000001', 40, '您的快件已由本人签收，送达至门牌地址，感谢使用', DATE_SUB(NOW(), INTERVAL 4 DAY), 'carrier'),
(5, 'DEMO20260913000005', 0,  '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 5 DAY), 'system'),
(5, 'DEMO20260913000005', 10, '您的慰问品已由「顺丰速运」揽收发出，运单号 DEMO-SF2468013579246', DATE_SUB(NOW(), INTERVAL 2 DAY), 'system'),
(6, 'DEMO20260913000006', 0,  '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 4 DAY), 'system'),
(6, 'DEMO20260913000006', 10, '您的慰问品已由「中国邮政」揽收发出，运单号 DEMO-EMS9988776655443', DATE_SUB(NOW(), INTERVAL 1 DAY), 'system'),
(7, 'DEMO20260914000007', 0,  '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 3 DAY), 'system'),
(8, 'DEMO20260914000008', 0,  '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 3 DAY), 'system'),
(9, 'DEMO20260915000009', 0,  '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 2 DAY), 'system'),
(12, 'DEMO20260915000012', 0, '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 11 DAY), 'system'),
(2, 'DEMO20260908000002', 0,  '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 10 DAY), 'system'),
(2, 'DEMO20260908000002', 10, '您的慰问品已由「京东物流」揽收发出，运单号 DEMO-JDVA00234567891', DATE_SUB(NOW(), INTERVAL 7 DAY), 'system'),
(2, 'DEMO20260908000002', 30, '快件正在派送，配送员正在为您送货上门', DATE_SUB(NOW(), INTERVAL 3 DAY), 'carrier'),
(2, 'DEMO20260908000002', 40, '您的快件已由本人签收，送达至门牌地址，感谢使用', DATE_SUB(NOW(), INTERVAL 3 DAY), 'carrier'),
(3, 'DEMO20260910000003', 0,  '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 8 DAY), 'system'),
(3, 'DEMO20260910000003', 10, '您的慰问品已由「圆通速递」揽收发出，运单号 DEMO-YT7788990011223', DATE_SUB(NOW(), INTERVAL 5 DAY), 'system'),
(3, 'DEMO20260910000003', 20, '快件已到达【保定集散中心】', DATE_SUB(NOW(), INTERVAL 3 DAY), 'carrier'),
(4, 'DEMO20260912000004', 0,  '您的配送信息已提交成功，我们将于 10 日内安排发货', DATE_SUB(NOW(), INTERVAL 6 DAY), 'system'),
(4, 'DEMO20260912000004', 10, '您的慰问品已由「中通快递」揽收发出，运单号 DEMO-ZTO5566778899001', DATE_SUB(NOW(), INTERVAL 3 DAY), 'system'),
(4, 'DEMO20260912000004', 20, '快件已从【保定中转场】发出，下一站【保定莲池集散点】', DATE_SUB(NOW(), INTERVAL 1 DAY), 'carrier');


-- 导入后断言；任一失败均须让 mysql 退出，整个事务回滚。
INSERT INTO `_hz_demo_seed_guard` (allowed)
SELECT IF(
    (SELECT COUNT(*) FROM t_package) = 4
    AND (SELECT COUNT(*) FROM t_package_item) = 13
    AND (SELECT COUNT(*) FROM t_grantee) = 37
    AND (SELECT COUNT(*) FROM t_grantee WHERE status = 0 AND phone LIKE '000000000%') = 37
    AND (SELECT COUNT(*) FROM t_notice) = 3
    AND (SELECT COUNT(*) FROM t_order) = 12
    AND (SELECT COUNT(*) FROM t_order_track) = 23
    AND NOT EXISTS (SELECT 1 FROM t_order o LEFT JOIN t_grantee g ON g.id=o.grantee_id
                    LEFT JOIN t_package p ON p.id=o.package_id
                    WHERE g.id IS NULL OR p.id IS NULL OR o.phone<>g.phone
                       OR o.grantee_name<>g.name OR o.package_name<>p.name)
    AND NOT EXISTS (SELECT 1 FROM t_order_track t LEFT JOIN t_order o ON o.id=t.order_id
                    WHERE o.id IS NULL OR t.order_no<>o.order_no)
    AND NOT EXISTS (SELECT 1 FROM t_package_item i LEFT JOIN t_package p ON p.id=i.package_id
                    WHERE p.id IS NULL)
    AND NOT EXISTS (SELECT 1 FROM t_grantee g
                    WHERE g.used<>(SELECT COUNT(*) FROM t_order o
                                   WHERE o.grantee_id=g.id AND o.status<>50 AND o.deleted=0))
    AND NOT EXISTS (SELECT 1 FROM t_order WHERE JSON_VALID(goods_snapshot)=0), 1, 0);
DROP TEMPORARY TABLE `_hz_demo_seed_guard`;
COMMIT;
