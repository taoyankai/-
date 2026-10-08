package com.hz.delivery.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** 业务编号生成工具。数据库唯一约束仍作为并发下的最终防线。 */
public final class BusinessNoUtil {

    private static final DateTimeFormatter BATCH_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private BusinessNoUtil() {
    }

    /** 2 + 14 + 8 = 24 位，小于 t_import_batch.batch_no 的 32 位上限。 */
    public static String importBatchNo() {
        String random = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase(Locale.ROOT);
        return "IB" + LocalDateTime.now().format(BATCH_TIME) + random;
    }
}
