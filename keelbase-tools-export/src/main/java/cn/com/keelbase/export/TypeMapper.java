// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.export;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;

/**
 * Java 类型 → 工具参数 JSON schema 类型，对齐 KeelBase 生成器 mapProxyType 口径
 * （scripts/generator/import-openapi-proxy.mjs）：
 * integer / number / boolean / string；集合/数组/Map/嵌套对象 → string（Agent 传 JSON 文本）。
 */
public final class TypeMapper {

    private TypeMapper() {
    }

    public static String map(Class<?> t) {
        if (t == String.class || t == Character.class || t == char.class || t == UUID.class) {
            return "string";
        }
        if (t == boolean.class || t == Boolean.class) {
            return "boolean";
        }
        if (t == int.class || t == Integer.class || t == short.class || t == Short.class) {
            return "integer";
        }
        if (t == long.class || t == Long.class || t == BigInteger.class) {
            return "integer";
        }
        if (t == float.class || t == Float.class || t == double.class || t == Double.class
                || t == BigDecimal.class) {
            return "number";
        }
        if (t.isEnum()) {
            return "string";
        }
        if (t == Date.class || t == java.sql.Date.class || t == LocalDate.class
                || t == LocalDateTime.class || t == OffsetDateTime.class || t == Instant.class) {
            return "string";
        }
        // 集合 / 数组 / Map / 嵌套对象 → string（Agent 传 JSON 文本，服务端反序列化）
        return "string";
    }
}
