// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.export;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reflective Javadoc extraction against the real {@code therapi-runtime-javadoc} runtime and
 * annotation processor (test scope only).
 *
 * <p>对真实 {@code therapi-runtime-javadoc} 运行时与注解处理器（仅测试作用域）验证反射式 Javadoc 提取。
 */
class JavadocDocExtractorTest {

    private final JavadocDocExtractor extractor = new JavadocDocExtractor();

    @Test
    void runtimeJavadoc_isAvailableInThisTest() {
        assertTrue(extractor.available(),
                "therapi runtime should be on the test classpath; the extractor discovers it reflectively");
    }

    @Test
    void extractsMethodSummaryAndParamDescriptions() throws Exception {
        Method method = Documented.class.getMethod("listOverdueOrders", long.class, int.class);

        assertEquals("List overdue orders for the risk review.",
                extractor.toolDescription(method),
                "method summary should come from the Javadoc comment");
        assertEquals("customer identifier", extractor.paramDescription(method, "customerId"),
                "@param customerId should be readable");
        assertEquals("max rows", extractor.paramDescription(method, "limit"),
                "@param limit should be readable");
        assertEquals("", extractor.paramDescription(method, "absent"),
                "an unknown parameter yields an empty string, never a fabricated description");
    }

    @Test
    void extractsRecordComponentDescriptions() throws Exception {
        Field content = DocumentedNote.class.getDeclaredField("content");
        Field dueDate = DocumentedNote.class.getDeclaredField("dueDate");

        assertEquals("note text", extractor.fieldDescription(content),
                "record @param tags should be exposed as record components");
        assertEquals("optional due date", extractor.fieldDescription(dueDate));
    }

    @Test
    void undocumentedMethod_yieldsEmptyStrings() throws Exception {
        Method method = Documented.class.getMethod("undocumented");

        assertEquals("", extractor.toolDescription(method));
        assertEquals("", extractor.paramDescription(method, "anything"));
    }

    /**
     * Fixture: documented so the scribe embeds the Javadoc into the test class file.
     *
     * <p>夹具：写有 Javadoc，让 scribe 把它嵌入测试 class 文件。下面的 Javadoc 文本是断言数据，
     * 故保持单一语言。
     */
    static class Documented {

        /**
         * List overdue orders for the risk review.
         *
         * @param customerId customer identifier
         * @param limit      max rows
         */
        public List<String> listOverdueOrders(long customerId, int limit) {
            return List.of();
        }

        public String undocumented() {
            return "";
        }
    }

    /**
     * Note request.
     *
     * @param content note text
     * @param dueDate optional due date
     */
    record DocumentedNote(String content, String dueDate) {
    }
}
