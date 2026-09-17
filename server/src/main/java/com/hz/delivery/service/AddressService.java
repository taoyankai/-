package com.hz.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hz.delivery.common.BizException;
import com.hz.delivery.common.ErrorCode;
import com.hz.delivery.entity.Address;
import com.hz.delivery.mapper.AddressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressMapper addressMapper;

    public List<Address> list(Long granteeId) {
        return addressMapper.selectList(new LambdaQueryWrapper<Address>()
                .eq(Address::getGranteeId, granteeId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getUpdateTime));
    }

    @Transactional(rollbackFor = Exception.class)
    public Address save(Long granteeId, Address a) {
        String err = AddressValidator.validate(a.getName(), a.getPhone(),
                a.getProvince(), a.getCity(), a.getDistrict(), a.getDetail());
        if (err != null) {
            throw BizException.of(ErrorCode.ADDRESS_INCOMPLETE, err);
        }
        a.setGranteeId(granteeId);
        if (a.getId() == null) {
            Long total = addressMapper.selectCount(new LambdaQueryWrapper<Address>()
                    .eq(Address::getGranteeId, granteeId));
            if (a.getIsDefault() == null) {
                a.setIsDefault(total == null || total == 0);
            }
            addressMapper.insert(a);
        } else {
            Address old = addressMapper.selectById(a.getId());
            if (old == null || !granteeId.equals(old.getGranteeId())) {
                throw BizException.of(ErrorCode.FORBIDDEN);
            }
            addressMapper.updateById(a);
        }
        if (Boolean.TRUE.equals(a.getIsDefault())) {
            setDefault(granteeId, a.getId());
        }
        return addressMapper.selectById(a.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long granteeId, Long id) {
        addressMapper.update(null, new LambdaUpdateWrapper<Address>()
                .eq(Address::getGranteeId, granteeId)
                .set(Address::getIsDefault, false));
        addressMapper.update(null, new LambdaUpdateWrapper<Address>()
                .eq(Address::getId, id)
                .eq(Address::getGranteeId, granteeId)
                .set(Address::getIsDefault, true));
    }

    public void delete(Long granteeId, Long id) {
        Address old = addressMapper.selectById(id);
        if (old == null || !granteeId.equals(old.getGranteeId())) {
            throw BizException.of(ErrorCode.FORBIDDEN);
        }
        addressMapper.deleteById(id);
    }
}
