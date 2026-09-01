// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.export;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** M2 验收：Java 类型 → 工具参数类型映射（对齐 KeelBase 生成器 mapProxyType 口径）。 */
class TypeMapperTest {

    enum Priority { LOW, MEDIUM, HIGH }

    @Test
    void mapsPrimitiveAndWrapperTypes() {
        assertEquals("string", TypeMapper.map(String.class));
        assertEquals("string", TypeMapper.map(UUID.class));
        assertEquals("string", TypeMapper.map(Character.class));
        assertEquals("boolean", TypeMapper.map(boolean.class));
        assertEquals("boolean", TypeMapper.map(Boolean.class));
        assertEquals("integer", TypeMapper.map(int.class));
        assertEquals("integer", TypeMapper.map(Integer.class));
        assertEquals("integer", TypeMapper.map(long.class));
        assertEquals("integer", TypeMapper.map(Long.class));
        assertEquals("integer", TypeMapper.map(BigInteger.class));
        assertEquals("integer", TypeMapper.map(short.class));
        assertEquals("number", TypeMapper.map(double.class));
        assertEquals("number", TypeMapper.map(Double.class));
        assertEquals("number", TypeMapper.map(BigDecimal.class));
    }

    @Test
    void mapsEnumToString() {
        assertEquals("string", TypeMapper.map(Priority.class));
    }

    @Test
    void mapsDateTypesToString() {
        assertEquals("string", TypeMapper.map(LocalDate.class));
        assertEquals("string", TypeMapper.map(LocalDateTime.class));
        assertEquals("string", TypeMapper.map(java.util.Date.class));
        assertEquals("string", TypeMapper.map(java.time.Instant.class));
    }

    @Test
    void mapsCollectionsAndNestedToString() {
        assertEquals("string", TypeMapper.map(List.class));
        assertEquals("string", TypeMapper.map(Map.class));
        assertEquals("string", TypeMapper.map(String[].class));
    }
}
