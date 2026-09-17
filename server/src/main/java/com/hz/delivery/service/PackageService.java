package com.hz.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hz.delivery.common.BizException;
import com.hz.delivery.common.ErrorCode;
import com.hz.delivery.dto.PackageSaveDTO;
import com.hz.delivery.entity.GoodsPackage;
import com.hz.delivery.entity.PackageItem;
import com.hz.delivery.mapper.GoodsPackageMapper;
import com.hz.delivery.mapper.PackageItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 套餐服务
 * 招标要求「不允许私自更改方案内容」：后台每次改动必须填写原因，并记录到 change_reason。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PackageService {

    private final GoodsPackageMapper packageMapper;
    private final PackageItemMapper itemMapper;

    /**
     * C 端套餐列表：只返回已上架，并带上商品明细
     */
    public List<GoodsPackage> listForClient() {
        List<GoodsPackage> list = packageMapper.selectList(new LambdaQueryWrapper<GoodsPackage>()
                .eq(GoodsPackage::getStatus, 1)
                .orderByAsc(GoodsPackage::getSort));
        list.forEach(this::fillItems);
        return list;
    }

    /**
     * 后台套餐列表：含已下架
     */
    public List<GoodsPackage> listForAdmin(String keyword) {
        LambdaQueryWrapper<GoodsPackage> w = new LambdaQueryWrapper<GoodsPackage>()
                .orderByAsc(GoodsPackage::getSort);
        if (StringUtils.hasText(keyword)) {
            w.and(x -> x.like(GoodsPackage::getName, keyword).or().like(GoodsPackage::getNo, keyword));
        }
        List<GoodsPackage> list = packageMapper.selectList(w);
        list.forEach(this::fillItems);
        return list;
    }

    public GoodsPackage detail(Long id) {
        GoodsPackage p = packageMapper.selectById(id);
        if (p == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "套餐不存在");
        }
        fillItems(p);
        return p;
    }

    @Transactional(rollbackFor = Exception.class)
    public GoodsPackage save(PackageSaveDTO dto, String operator) {
        // 招标要求：任何方案变更都要留痕，因此强制填写变更原因
        if (dto.getId() != null && !StringUtils.hasText(dto.getChangeReason())) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "修改套餐必须填写变更原因（招标要求：不允许私自更改方案内容）");
        }

        GoodsPackage p = new GoodsPackage();
        if (dto.getId() != null) {
            p = packageMapper.selectById(dto.getId());
            if (p == null) {
                throw BizException.of(ErrorCode.NOT_FOUND, "套餐不存在");
            }
        } else {
            p.setNo(StringUtils.hasText(dto.getNo()) ? dto.getNo() : "PK" + System.currentTimeMillis() % 100000);
            p.setStock(0);
            p.setStatus(1);
            p.setSort(99);
        }

        p.setName(dto.getName());
        p.setSub(dto.getSub());
        if (dto.getPrice() != null) {
            p.setPrice(dto.getPrice());
        }
        if (dto.getStock() != null) {
            p.setStock(dto.getStock());
        }
        if (dto.getWarn() != null) {
            p.setWarn(dto.getWarn());
        }
        if (dto.getSort() != null) {
            p.setSort(dto.getSort());
        }
        if (dto.getStatus() != null) {
            p.setStatus(dto.getStatus());
        }
        p.setChangeReason(StringUtils.hasText(dto.getChangeReason())
                ? (operator + "：" + dto.getChangeReason())
                : null);

        if (dto.getId() == null) {
            packageMapper.insert(p);
        } else {
            packageMapper.updateById(p);
            // 明细全量替换
            itemMapper.delete(new LambdaQueryWrapper<PackageItem>().eq(PackageItem::getPackageId, p.getId()));
        }

        if (dto.getGoods() != null) {
            int sort = 0;
            for (PackageSaveDTO.Item it : dto.getGoods()) {
                PackageItem pi = new PackageItem();
                pi.setPackageId(p.getId());
                pi.setName(it.getName());
                pi.setSpec(it.getSpec());
                pi.setQty(it.getQty() == null ? 1 : it.getQty());
                pi.setUnit(it.getUnit());
                pi.setSort(sort++);
                itemMapper.insert(pi);
            }
        }
        return detail(p.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        GoodsPackage p = packageMapper.selectById(id);
        if (p == null) {
            return;
        }
        packageMapper.deleteById(id);
        itemMapper.delete(new LambdaQueryWrapper<PackageItem>().eq(PackageItem::getPackageId, id));
    }

    private void fillItems(GoodsPackage p) {
        p.setGoods(itemMapper.selectList(new LambdaQueryWrapper<PackageItem>()
                .eq(PackageItem::getPackageId, p.getId())
                .orderByAsc(PackageItem::getSort)));
    }
}
