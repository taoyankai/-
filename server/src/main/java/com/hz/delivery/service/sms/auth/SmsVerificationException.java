package com.hz.delivery.service.sms.auth;

import com.hz.delivery.common.BizException;
import com.hz.delivery.common.ErrorCode;

public class SmsVerificationException extends BizException {
    private final boolean deliveryUncertain;

    public SmsVerificationException(boolean deliveryUncertain) {
        super(ErrorCode.SMS_AUTH_UNAVAILABLE);
        this.deliveryUncertain = deliveryUncertain;
    }

    public boolean isDeliveryUncertain() { return deliveryUncertain; }
}
