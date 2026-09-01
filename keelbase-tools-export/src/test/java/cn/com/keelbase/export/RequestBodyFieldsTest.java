// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.export;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 参数提取加固：继承 / @JsonIgnore / static·transient / record / @JsonProperty required。 */
class RequestBodyFieldsTest {

    private Map<String, RequestBodyFields.BodyField> byName(List<RequestBodyFields.BodyField> fields) {
        return fields.stream().collect(Collectors.toMap(RequestBodyFields.BodyField::name, f -> f));
    }

    // ---- 场景 1：继承 + @JsonIgnore + static + @JsonProperty ----

    static class BaseQuery {
        private Integer page;
        private Integer limit;
    }

    static class FollowupQuery extends BaseQuery {
        @JsonProperty(required = true)
        private String keyword;

        @JsonIgnore
        private String serverHint;

        private static String CACHE_KEY;

        private transient String skipMe;
    }

    @Test
    void inheritance_jsonIgnore_static_transient() {
        List<RequestBodyFields.BodyField> fields = RequestBodyFields.of(FollowupQuery.class);
        Map<String, RequestBodyFields.BodyField> m = byName(fields);

        // 继承字段也应收集
        assertTrue(m.containsKey("page"), "父类字段 page 应收集");
        assertEquals("integer", m.get("page").type());
        assertTrue(m.containsKey("limit"));
        // @JsonProperty(required=true)
        assertTrue(m.containsKey("keyword"));
        assertTrue(m.get("keyword").required());
        assertEquals("string", m.get("keyword").type());
        // 排除项
        assertFalse(m.containsKey("serverHint"), "@JsonIgnore 字段不应导出");
        assertFalse(m.containsKey("CACHE_KEY"), "static 字段不应导出");
        assertFalse(m.containsKey("skipMe"), "transient 字段不应导出");
        // 结果：keyword + 父类 page + limit = 3
        assertEquals(3, fields.size());
    }

    // ---- 场景 2：record + enum 描述 ----

    enum Priority { LOW, MEDIUM, HIGH }

    record FollowupCreate(
            @JsonProperty(required = true) String content,
            Long customerId,
            Priority priority) {
    }

    @Test
    void record_components_and_enumDescription() {
        List<RequestBodyFields.BodyField> fields = RequestBodyFields.of(FollowupCreate.class);
        Map<String, RequestBodyFields.BodyField> m = byName(fields);

        assertTrue(m.get("content").required(), "@JsonProperty(required=true) → required");
        assertEquals("integer", m.get("customerId").type());
        assertEquals("string", m.get("priority").type());
        assertEquals("可选: LOW/MEDIUM/HIGH", m.get("priority").description());
        assertEquals(3, fields.size());
    }

    // ---- 场景 3：类级 @JsonIgnoreProperties ----

    @JsonIgnoreProperties({"internal", "debug"})
    static class IgnoredDto {
        private String visible;
        private String internal;
        private String debug;
    }

    @Test
    void classLevelJsonIgnoreProperties() {
        List<RequestBodyFields.BodyField> fields = RequestBodyFields.of(IgnoredDto.class);
        Map<String, RequestBodyFields.BodyField> m = byName(fields);
        assertTrue(m.containsKey("visible"));
        assertFalse(m.containsKey("internal"));
        assertFalse(m.containsKey("debug"));
        assertEquals(1, fields.size());
    }

    // ---- 场景 4：嵌套复杂对象 → string（与生成器口径一致） ----

    static class NestedDto {
        private Map<String, Object> extra;
    }

    @Test
    void nestedComplex_typeIsString() {
        RequestBodyFields.BodyField f = RequestBodyFields.of(NestedDto.class).get(0);
        assertEquals("string", f.type(), "Map/嵌套对象 → string（Agent 传 JSON 文本）");
    }
}
