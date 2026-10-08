package com.hz.delivery.service;

import com.hz.delivery.common.BizException;
import com.hz.delivery.entity.GoodsPackage;
import com.hz.delivery.mapper.GoodsPackageMapper;
import com.hz.delivery.mapper.PackageItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PackageServiceTest {

    @Mock
    private GoodsPackageMapper packageMapper;
    @Mock
    private PackageItemMapper itemMapper;

    private PackageService service;

    @BeforeEach
    void setUp() {
        service = new PackageService(packageMapper, itemMapper);
    }

    @Test
    void deletingPublishedPackageRequiresTakingItOfflineFirst() {
        GoodsPackage p = pkg(1);
        when(packageMapper.selectById(1L)).thenReturn(p);

        BizException error = assertThrows(BizException.class,
                () -> service.delete(1L, "停止供应", "admin"));

        assertTrue(error.getMessage().contains("先下架"));
        verify(packageMapper, never()).deleteById(isA(Serializable.class));
    }

    @Test
    void deletingPackageRequiresReason() {
        assertThrows(BizException.class, () -> service.delete(1L, " ", "admin"));
        verifyNoInteractions(packageMapper, itemMapper);
    }

    @Test
    void deletingOfflinePackageKeepsReasonAndRemovesItems() {
        GoodsPackage p = pkg(0);
        when(packageMapper.selectById(1L)).thenReturn(p);

        GoodsPackage deleted = service.delete(1L, "供应结束", "系统管理员");

        assertSame(p, deleted);
        assertTrue(p.getChangeReason().contains("系统管理员"));
        assertTrue(p.getChangeReason().contains("供应结束"));
        verify(packageMapper).updateById(p);
        verify(packageMapper).deleteById((Serializable) 1L);
        verify(itemMapper).delete(any());
    }

    private GoodsPackage pkg(int status) {
        GoodsPackage p = new GoodsPackage();
        p.setId(1L);
        p.setNo("PK-A");
        p.setName("测试套餐");
        p.setStatus(status);
        return p;
    }
}
