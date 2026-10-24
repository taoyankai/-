package com.hz.delivery.service;

import com.hz.delivery.common.BizException;
import com.hz.delivery.common.Constants;
import com.hz.delivery.entity.Admin;
import com.hz.delivery.entity.Order;
import com.hz.delivery.entity.OrderTrack;
import com.hz.delivery.mapper.AdminMapper;
import com.hz.delivery.mapper.OrderMapper;
import com.hz.delivery.mapper.OrderTrackMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderReceiptServiceTest {
    private final OrderMapper orders = mock(OrderMapper.class);
    private final OrderTrackMapper tracks = mock(OrderTrackMapper.class);
    private final AdminMapper admins = mock(AdminMapper.class);
    private final OrderReceiptService service = new OrderReceiptService(orders, tracks, admins);
    private Order order;
    private Admin admin;

    @BeforeEach
    void setup() {
        order = new Order();
        order.setId(1L); order.setOrderNo("TEST-RECEIPT-1"); order.setGranteeId(10L);
        order.setStatus(Constants.ORDER_DELIVERING);
        order.setWaybillNo("TEST-WAYBILL-1"); order.setShipTime(LocalDateTime.now().minusDays(1));
        when(orders.selectByIdForUpdate(1L)).thenReturn(order);
        when(orders.confirmReceipt(eq(1L), nullable(Long.class), any(LocalDateTime.class))).thenReturn(1);
        admin = new Admin(); admin.setId(2L); admin.setUsername("operator");
        admin.setRealName("测试运营"); admin.setStatus(1); admin.setRole("operator");
        when(admins.selectById(2L)).thenReturn(admin);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 20, 30})
    void clientCanConfirmOnlyShippedInTransitOrDeliveringOrders(int status) {
        order.setStatus(status);
        var result = service.confirmForClient(10L, 1L);
        assertTrue(result.changed());
        assertEquals(40, result.status());
        assertNotNull(result.signTime());
        verify(orders).confirmReceipt(1L, 10L, result.signTime());
        var captor = ArgumentCaptor.forClass(OrderTrack.class);
        verify(tracks).insert(captor.capture());
        assertEquals("client", captor.getValue().getSource());
        assertEquals("收货人确认收货", captor.getValue().getDescription());
        assertEquals(40, captor.getValue().getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "实际已收到商品"})
    void adminRemarkIsOptionalAndOperatorIsRecorded(String remark) {
        var result = service.confirmForAdmin(2L, 1L, remark);
        assertTrue(result.changed());
        var captor = ArgumentCaptor.forClass(OrderTrack.class);
        verify(tracks).insert(captor.capture());
        assertEquals("manual", captor.getValue().getSource());
        assertTrue(captor.getValue().getDescription().startsWith("后台代确认收货（测试运营）"));
        if (!remark.trim().isEmpty()) assertTrue(captor.getValue().getDescription().endsWith(remark));
    }

    @Test
    void missingAdminRemarkAndAdminRoleAlsoWork() {
        admin.setRole("admin");
        admin.setRealName(null);
        assertTrue(service.confirmForAdmin(2L, 1L, null).changed());
        verify(tracks).insert(argThat((OrderTrack t) -> "后台代确认收货（operator）".equals(t.getDescription())));
    }

    @Test
    void anyExistingSignatureCompletesOrderWithoutChangingTimeOrAddingTracks() {
        var signedAt = LocalDateTime.of(2026, 10, 23, 10, 0);
        order.setStatus(40); order.setSignTime(signedAt);
        assertFalse(service.confirmForClient(10L, 1L).changed());
        var result = service.confirmForAdmin(2L, 1L, "不应追加");
        assertFalse(result.changed()); assertEquals(signedAt, result.signTime());
        verify(orders, never()).confirmReceipt(anyLong(), nullable(Long.class), any());
        verifyNoInteractions(tracks);
    }

    @Test
    void wrongOwnerCannotConfirmEvenAnAlreadySignedOrder() {
        order.setStatus(40);
        assertEquals(1303, assertThrows(BizException.class, () -> service.confirmForClient(99L, 1L)).getCode());
        verifyNoInteractions(tracks);
    }

    @Test
    void missingIdentityOrOrderIsRejected() {
        assertEquals(401, assertThrows(BizException.class, () -> service.confirmForClient(null, 1L)).getCode());
        assertEquals(400, assertThrows(BizException.class, () -> service.confirmForClient(10L, null)).getCode());
        assertEquals(1301, assertThrows(BizException.class, () -> service.confirmForClient(10L, 99L)).getCode());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 50, 60, 70, 999})
    void otherStatesCannotBeManuallySigned(int status) {
        order.setStatus(status);
        assertEquals(1302, assertThrows(BizException.class, () -> service.confirmForClient(10L, 1L)).getCode());
        assertEquals(1302, assertThrows(BizException.class, () -> service.confirmForAdmin(2L, 1L, null)).getCode());
        verifyNoInteractions(tracks);
    }

    @Test
    void incompleteShipmentIsRejected() {
        order.setWaybillNo(" ");
        assertThrows(BizException.class, () -> service.confirmForClient(10L, 1L));
        order.setWaybillNo("TEST-WAYBILL"); order.setShipTime(null);
        assertThrows(BizException.class, () -> service.confirmForAdmin(2L, 1L, ""));
        verifyNoInteractions(tracks);
    }

    @ParameterizedTest
    @ValueSource(strings = {"viewer", "unknown", ""})
    void readOnlyAndUnknownRolesCannotConfirm(String role) {
        admin.setRole(role);
        assertEquals(403, assertThrows(BizException.class, () -> service.confirmForAdmin(2L, 1L, null)).getCode());
        verifyNoInteractions(tracks);
    }

    @Test
    void disabledOrMissingAdminIsRejected() {
        admin.setStatus(0);
        assertEquals(401, assertThrows(BizException.class, () -> service.confirmForAdmin(2L, 1L, null)).getCode());
        assertEquals(401, assertThrows(BizException.class, () -> service.confirmForAdmin(null, 1L, null)).getCode());
    }

    @Test
    void remarkTooLongAndFailedUpdateDoNotWriteTrack() {
        assertEquals(400, assertThrows(BizException.class,
                () -> service.confirmForAdmin(2L, 1L, "x".repeat(201))).getCode());
        when(orders.confirmReceipt(eq(1L), nullable(Long.class), any())).thenReturn(0);
        assertEquals(1302, assertThrows(BizException.class, () -> service.confirmForClient(10L, 1L)).getCode());
        verifyNoInteractions(tracks);
    }
}
