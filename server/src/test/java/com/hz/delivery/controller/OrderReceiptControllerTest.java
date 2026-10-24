package com.hz.delivery.controller;

import com.hz.delivery.common.Constants;
import com.hz.delivery.common.GlobalExceptionHandler;
import com.hz.delivery.config.AuthInterceptor;
import com.hz.delivery.entity.Admin;
import com.hz.delivery.mapper.AdminMapper;
import com.hz.delivery.service.*;
import com.hz.delivery.service.logistics.LogisticsTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderReceiptControllerTest {
    private final OrderReceiptService receipts = mock(OrderReceiptService.class);
    private final OperationLogService logs = mock(OperationLogService.class);
    private MockMvc mvc;
    private final OrderReceiptService.Confirmation confirmation = new OrderReceiptService.Confirmation(
            1L, "TEST-RECEIPT-1", 40, LocalDateTime.of(2026, 10, 24, 10, 0), true);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        var adminController = new AdminOrderController(mock(ShipmentService.class), mock(ShipmentBatchService.class),
                mock(AdminAuthService.class), logs, mock(LogisticsTrackingService.class), receipts);
        var clientController = new ClientController(mock(AuthService.class), mock(PackageService.class),
                mock(OrderService.class), mock(AddressService.class), mock(NoticeService.class),
                mock(ShipmentService.class), receipts);
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(Constants.KEY_ADMIN_TOKEN + "operator-token")).thenReturn("2");
        when(values.get(Constants.KEY_ADMIN_TOKEN + "viewer-token")).thenReturn("3");
        when(values.get(Constants.KEY_CLIENT_TOKEN + "client-token")).thenReturn("10");
        var admins = mock(AdminMapper.class);
        for (long id : new long[]{2, 3}) {
            var admin = new Admin(); admin.setId(id); admin.setStatus(1); admin.setMustChangePassword(0);
            admin.setRole(id == 2 ? "operator" : "viewer"); when(admins.selectById(id)).thenReturn(admin);
        }
        mvc = MockMvcBuilders.standaloneSetup(adminController, clientController)
                .addInterceptors(new AuthInterceptor(redis, admins))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(receipts.confirmForAdmin(eq(2L), eq(1L), nullable(String.class))).thenReturn(confirmation);
        when(receipts.confirmForClient(10L, 1L)).thenReturn(confirmation);
    }

    @Test
    void adminCanConfirmWithoutBodyAndAuditIncludesIdentity() throws Exception {
        mvc.perform(post("/api/admin/orders/1/confirm-receipt").header(Constants.HEADER_TOKEN, "operator-token"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.changed").value(true));
        verify(receipts).confirmForAdmin(2L, 1L, null);
        verify(logs).success(eq(2L), eq("order"), eq("confirm-receipt"), eq("TEST-RECEIPT-1"), anyString(), eq(1), any());
    }

    @Test
    void optionalRemarkIsPassedAndRepeatedConfirmationDoesNotRepeatAudit() throws Exception {
        when(receipts.confirmForAdmin(2L, 1L, "已电话核实")).thenReturn(
                new OrderReceiptService.Confirmation(1L, "TEST-RECEIPT-1", 40, confirmation.signTime(), false));
        mvc.perform(post("/api/admin/orders/1/confirm-receipt").header(Constants.HEADER_TOKEN, "operator-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"remark\":\"已电话核实\"}"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.changed").value(false));
        verifyNoInteractions(logs);
    }

    @Test
    void viewerAndMissingTokenCannotWrite() throws Exception {
        mvc.perform(post("/api/admin/orders/1/confirm-receipt").header(Constants.HEADER_TOKEN, "viewer-token"))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(post("/api/client/orders/1/confirm-receipt")).andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(receipts);
    }

    @Test
    void clientIdentityComesFromTokenRatherThanBody() throws Exception {
        mvc.perform(post("/api/client/orders/1/confirm-receipt").header(Constants.HEADER_TOKEN, "client-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"granteeId\":99,\"adminId\":2}"))
                .andExpect(jsonPath("$.code").value(0));
        verify(receipts).confirmForClient(10L, 1L);
        verifyNoInteractions(logs);
    }

    @Test
    void legacyEndpointCannotAdvanceToOtherStatesButAcceptsReceiptWithoutRemark() throws Exception {
        mvc.perform(post("/api/admin/orders/1/push-status").header(Constants.HEADER_TOKEN, "operator-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":30}"))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(receipts);
        mvc.perform(post("/api/admin/orders/1/push-status").header(Constants.HEADER_TOKEN, "operator-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":40}"))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void invalidStateMissingStateAndOverlongRemarksReturnParameterErrors() throws Exception {
        for (String content : new String[]{"{\"status\":\"invalid\"}", "{}",
                "{\"status\":40,\"description\":\"" + "x".repeat(201) + "\"}"}) {
            mvc.perform(post("/api/admin/orders/1/push-status").header(Constants.HEADER_TOKEN, "operator-token")
                            .contentType(MediaType.APPLICATION_JSON).content(content))
                    .andExpect(jsonPath("$.code").value(400));
        }
        mvc.perform(post("/api/admin/orders/1/confirm-receipt").header(Constants.HEADER_TOKEN, "operator-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"remark\":\"" + "x".repeat(201) + "\"}"))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(receipts);
    }
}
