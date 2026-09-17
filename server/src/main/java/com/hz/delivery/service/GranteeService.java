package com.hz.delivery.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hz.delivery.common.BizException;
import com.hz.delivery.common.Constants;
import com.hz.delivery.common.ErrorCode;
import com.hz.delivery.common.PageResult;
import com.hz.delivery.entity.Grantee;
import com.hz.delivery.entity.ImportBatch;
import com.hz.delivery.entity.Order;
import com.hz.delivery.excel.GranteeExcelRow;
import com.hz.delivery.excel.GranteeExportRow;
import com.hz.delivery.mapper.GranteeMapper;
import com.hz.delivery.mapper.ImportBatchMapper;
import com.hz.delivery.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 教职工名单服务
 * 名单来源：甲方提供 Excel，后台导入；支持按条件筛选后导出。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GranteeService {

    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");
    private static final int MAX_ROWS = 5000;
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Map<Integer, String> ORDER_STATUS_NAME = new HashMap<>();

    static {
        ORDER_STATUS_NAME.put(0, "待发货");
        ORDER_STATUS_NAME.put(10, "已发货");
        ORDER_STATUS_NAME.put(20, "运输中");
        ORDER_STATUS_NAME.put(30, "派送中");
        ORDER_STATUS_NAME.put(40, "已签收");
        ORDER_STATUS_NAME.put(50, "已取消");
        ORDER_STATUS_NAME.put(60, "配送异常");
        ORDER_STATUS_NAME.put(70, "已退回");
    }

    private final GranteeMapper granteeMapper;
    private final OrderMapper orderMapper;
    private final ImportBatchMapper batchMapper;

    /* ==================== 查询 ==================== */

    public PageResult<Grantee> page(long page, long size, String keyword, String org, Integer claimStatus) {
        LambdaQueryWrapper<Grantee> w = new LambdaQueryWrapper<Grantee>().orderByDesc(Grantee::getCreateTime);
        if (StringUtils.hasText(org)) {
            w.eq(Grantee::getOrg, org);
        }
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.and(x -> x.like(Grantee::getName, k)
                    .or().like(Grantee::getPhone, k)
                    .or().like(Grantee::getEmpNo, k)
                    .or().like(Grantee::getDept, k));
        }
        if (claimStatus != null) {
            if (claimStatus == 1) {
                w.apply("used > 0");
            } else {
                w.apply("used = 0");
            }
        }
        Page<Grantee> p = granteeMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    /**
     * 不带分页的全量查询，供导出使用
     */
    public List<Grantee> listAll(String keyword, String org, Integer claimStatus) {
        LambdaQueryWrapper<Grantee> w = new LambdaQueryWrapper<Grantee>().orderByAsc(Grantee::getOrg).orderByAsc(Grantee::getEmpNo);
        if (StringUtils.hasText(org)) {
            w.eq(Grantee::getOrg, org);
        }
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.and(x -> x.like(Grantee::getName, k)
                    .or().like(Grantee::getPhone, k)
                    .or().like(Grantee::getEmpNo, k)
                    .or().like(Grantee::getDept, k));
        }
        if (claimStatus != null) {
            w.apply(claimStatus == 1 ? "used > 0" : "used = 0");
        }
        return granteeMapper.selectList(w);
    }

    public Grantee getById(Long id) {
        Grantee g = granteeMapper.selectById(id);
        if (g == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "该教职工不在名单内");
        }
        return g;
    }

    @Transactional(rollbackFor = Exception.class)
    public Grantee save(Grantee g) {
        if (!StringUtils.hasText(g.getName())) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "请填写姓名");
        }
        if (!StringUtils.hasText(g.getPhone()) || !PHONE.matcher(g.getPhone().trim()).matches()) {
            throw BizException.of(ErrorCode.PHONE_FORMAT_ERROR);
        }
        g.setPhone(g.getPhone().trim());

        Grantee dup = granteeMapper.selectByPhone(g.getPhone());
        if (dup != null && !dup.getId().equals(g.getId())) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "手机号 " + g.getPhone() + " 已存在");
        }
        if (g.getId() == null) {
            if (g.getQuota() == null) g.setQuota(1);
            if (g.getUsed() == null) g.setUsed(0);
            if (g.getStatus() == null) g.setStatus(1);
            granteeMapper.insert(g);
        } else {
            granteeMapper.updateById(g);
        }
        return granteeMapper.selectById(g.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Grantee g = granteeMapper.selectById(id);
        if (g == null) {
            return;
        }
        if (g.getUsed() != null && g.getUsed() > 0) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "该教职工已领取慰问品，不可删除；如确需移除请先停用");
        }
        granteeMapper.deleteById(id);
    }

    /**
     * 重置领取额度（用于误操作回退，会一并取消其待发货订单由运营确认）
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetQuota(Long id) {
        Grantee g = granteeMapper.selectById(id);
        if (g == null) {
            throw BizException.of(ErrorCode.NOT_FOUND);
        }
        g.setUsed(0);
        granteeMapper.updateById(g);
    }

    /* ==================== Excel 导入 ==================== */

    /**
     * 从甲方提供的 Excel 导入名单。
     * 同一手机号已存在时按 updateExists 决定覆盖还是跳过；全程记录导入批次，便于追溯。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importExcel(MultipartFile file, String operator, Boolean updateExists) {
        if (file == null || file.isEmpty()) {
            throw BizException.of(ErrorCode.EXCEL_EMPTY);
        }

        List<GranteeExcelRow> rows;
        try {
            rows = EasyExcel.read(file.getInputStream())
                    .head(GranteeExcelRow.class)
                    .sheet()
                    .headRowNumber(1)
                    .doReadSync();
        } catch (Exception e) {
            log.warn("名单文件解析失败", e);
            throw BizException.of(ErrorCode.EXCEL_FORMAT_ERROR);
        }

        if (rows == null || rows.isEmpty()) {
            throw BizException.of(ErrorCode.EXCEL_EMPTY);
        }
        if (rows.size() > MAX_ROWS) {
            throw BizException.of(ErrorCode.EXCEL_TOO_LARGE);
        }

        boolean overwrite = !Boolean.FALSE.equals(updateExists);

        // 预取已存在手机号，避免逐行查库
        Set<String> phones = rows.stream()
                .map(r -> trim(r.getPhone()))
                .filter(p -> StringUtils.hasText(p) && PHONE.matcher(p).matches())
                .collect(Collectors.toSet());
        Map<String, Grantee> existMap = new HashMap<>();
        if (!phones.isEmpty()) {
            List<Grantee> existing = granteeMapper.selectList(
                    new LambdaQueryWrapper<Grantee>().in(Grantee::getPhone, phones));
            existing.forEach(g -> existMap.put(g.getPhone(), g));
        }

        // 创建批次
        ImportBatch batch = new ImportBatch();
        batch.setBatchNo("IB" + System.currentTimeMillis());
        batch.setType("grantee");
        batch.setFileName(file.getOriginalFilename());
        batch.setTotalCount(rows.size());
        batch.setOperator(operator);
        batch.setCreateTime(LocalDateTime.now());
        batch.setSuccessCount(0);
        batch.setFailCount(0);
        batchMapper.insert(batch);

        List<String> fails = new ArrayList<>();
        List<Grantee> insertList = new ArrayList<>();
        List<Grantee> updateList = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            GranteeExcelRow r = rows.get(i);
            int lineNo = i + 2; // Excel 首行为表头

            String name = trim(r.getName());
            String phone = trim(r.getPhone());
            String org = trim(r.getOrg());
            String dept = trim(r.getDept());
            String empNo = trim(r.getEmpNo());

            if (!StringUtils.hasText(name)) {
                fails.add("第 " + lineNo + " 行：姓名不能为空");
                continue;
            }
            if (!StringUtils.hasText(phone)) {
                fails.add("第 " + lineNo + " 行：手机号不能为空");
                continue;
            }
            if (!PHONE.matcher(phone).matches()) {
                fails.add("第 " + lineNo + " 行：手机号 " + phone + " 格式不正确");
                continue;
            }
            if (!seen.add(phone)) {
                fails.add("第 " + lineNo + " 行：手机号 " + phone + " 在文件内重复");
                continue;
            }

            Grantee exist = existMap.get(phone);
            if (exist != null) {
                if (!overwrite) {
                    fails.add("第 " + lineNo + " 行：手机号 " + phone + " 已存在，已按设置跳过");
                    continue;
                }
                exist.setName(name);
                exist.setEmpNo(empNo);
                exist.setOrg(org);
                exist.setDept(dept);
                exist.setRemark(trim(r.getRemark()));
                exist.setStatus(1);
                exist.setBatchId(batch.getId());
                updateList.add(exist);
            } else {
                Grantee g = new Grantee();
                g.setEmpNo(empNo);
                g.setName(name);
                g.setPhone(phone);
                g.setOrg(org);
                g.setDept(dept);
                g.setRemark(trim(r.getRemark()));
                g.setQuota(1);
                g.setUsed(0);
                g.setStatus(1);
                g.setBatchId(batch.getId());
                insertList.add(g);
            }
        }

        for (Grantee g : insertList) {
            granteeMapper.insert(g);
        }
        for (Grantee g : updateList) {
            granteeMapper.updateById(g);
        }

        batch.setSuccessCount(insertList.size() + updateList.size());
        batch.setFailCount(fails.size());
        batch.setFailDetail(fails.isEmpty() ? null : String.join("\n", fails.subList(0, Math.min(fails.size(), 200))));
        batchMapper.updateById(batch);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchNo", batch.getBatchNo());
        result.put("total", rows.size());
        result.put("insertCount", insertList.size());
        result.put("updateCount", updateList.size());
        result.put("successCount", insertList.size() + updateList.size());
        result.put("failCount", fails.size());
        result.put("failList", fails.size() > 200 ? fails.subList(0, 200) : fails);
        log.info("名单导入完成 batch={} 新增={} 更新={} 失败={}", batch.getBatchNo(),
                insertList.size(), updateList.size(), fails.size());
        return result;
    }

    /* ==================== Excel 导出 ==================== */

    /**
     * 组装导出数据：名单基础信息 + 领取情况 + 履约情况
     */
    public List<GranteeExportRow> buildExportRows(String keyword, String org, Integer claimStatus) {
        List<Grantee> list = listAll(keyword, org, claimStatus);
        if (list.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> ids = list.stream().map(Grantee::getId).collect(Collectors.toList());
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .in(Order::getGranteeId, ids)
                .orderByDesc(Order::getCreateTime));
        // 每人取最新一单
        Map<Long, Order> latestOrder = new LinkedHashMap<>();
        for (Order o : orders) {
            latestOrder.putIfAbsent(o.getGranteeId(), o);
        }

        List<GranteeExportRow> rows = new ArrayList<>(list.size());
        for (Grantee g : list) {
            GranteeExportRow r = new GranteeExportRow();
            r.setEmpNo(g.getEmpNo());
            r.setName(g.getName());
            r.setPhone(g.getPhone());
            r.setOrg(g.getOrg());
            r.setDept(g.getDept());
            r.setQuota(g.getQuota());
            r.setUsed(g.getUsed());
            r.setClaimStatus(claimStatusText(g));
            r.setRemark(g.getRemark());

            Order o = latestOrder.get(g.getId());
            if (o != null) {
                r.setPackageName(o.getPackageName());
                r.setOrderNo(o.getOrderNo());
                r.setOrderStatus(ORDER_STATUS_NAME.getOrDefault(o.getStatus(), "未知"));
                r.setAddress(fullAddress(o));
                r.setReceiver(o.getReceiver());
                r.setReceiverPhone(o.getPhone());
                r.setCarrierName(o.getCarrierName());
                r.setWaybillNo(o.getWaybillNo());
                r.setCreateTime(o.getCreateTime() == null ? null : o.getCreateTime().format(DT));
                r.setShipTime(o.getShipTime() == null ? null : o.getShipTime().format(DT));
                r.setAllowStation(Boolean.TRUE.equals(o.getAllowStation()) ? "是" : "否");
            }
            rows.add(r);
        }

        // 若按领取状态筛选，需在关联订单后二次过滤
        if (claimStatus != null) {
            rows.removeIf(r -> claimStatus == 1
                    ? r.getUsed() == null || r.getUsed() == 0
                    : r.getUsed() != null && r.getUsed() > 0);
        }
        return rows;
    }

    public List<ImportBatch> batches(String type) {
        LambdaQueryWrapper<ImportBatch> w = new LambdaQueryWrapper<ImportBatch>()
                .orderByDesc(ImportBatch::getCreateTime)
                .last("LIMIT 100");
        if (StringUtils.hasText(type)) {
            w.eq(ImportBatch::getType, type);
        }
        return batchMapper.selectList(w);
    }

    public void recordBatch(ImportBatch b) {
        batchMapper.insert(b);
    }

    public void updateBatch(ImportBatch b) {
        batchMapper.updateById(b);
    }

    /* ==================== 工具 ==================== */

    public static String claimStatusText(Grantee g) {
        int quota = g.getQuota() == null ? 1 : g.getQuota();
        int used = g.getUsed() == null ? 0 : g.getUsed();
        if (used <= 0) {
            return "未领取";
        }
        return used >= quota ? "已领取" : "部分领取";
    }

    public static String fullAddress(Order o) {
        StringBuilder sb = new StringBuilder();
        if (o.getProvince() != null) sb.append(o.getProvince());
        if (o.getCity() != null) sb.append(o.getCity());
        if (o.getDistrict() != null) sb.append(o.getDistrict());
        if (o.getDetail() != null) sb.append(o.getDetail());
        return sb.toString();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    public static String orderStatusName(Integer status) {
        return ORDER_STATUS_NAME.getOrDefault(status, "未知");
    }

    /**
     * 供其它服务引用的状态常量
     */
    public static final int STATUS_PENDING = Constants.ORDER_PENDING;
}
