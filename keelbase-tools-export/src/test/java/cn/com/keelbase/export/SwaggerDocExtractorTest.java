package cn.com.keelbase.export;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** SwaggerDocExtractor：@Operation/@Parameter 描述自动提取（反射探测，classpath 无 swagger 时静默跳过）。 */
class SwaggerDocExtractorTest {

    private final SwaggerDocExtractor extractor = new SwaggerDocExtractor();

    static class DocController {
        @Operation(summary = "列出客户", description = "按关键字查询客户")
        public String list(@Parameter(description = "名称/公司关键字") String keyword,
                           @Parameter String noDesc) {
            return null;
        }

        @Operation(description = "仅有 description 的端点")
        public String descOnly() {
            return null;
        }

        public String noAnnotation() {
            return null;
        }
    }

    @Test
    void operationSummary_wins_overDescription() throws Exception {
        assertEquals("列出客户",
                extractor.toolDescription(DocController.class.getMethod("list", String.class, String.class), "fallback"));
    }

    @Test
    void operationDescription_fallbackWhenNoSummary() throws Exception {
        assertEquals("仅有 description 的端点",
                extractor.toolDescription(DocController.class.getMethod("descOnly"), "fallback"));
    }

    @Test
    void noOperation_usesFallback() throws Exception {
        assertEquals("GET /x",
                extractor.toolDescription(DocController.class.getMethod("noAnnotation"), "GET /x"));
    }

    @Test
    void parameterDescription_appendedToExisting() throws Exception {
        MethodParameter mp = new MethodParameter(
                DocController.class.getMethod("list", String.class, String.class), 0);
        assertEquals("可选: A/B；名称/公司关键字", extractor.paramDescription(mp, "可选: A/B"),
                "@Parameter(description) 应附加到枚举可选值之后");
        assertEquals("名称/公司关键字", extractor.paramDescription(mp, ""),
                "现有描述为空时直接用 @Parameter 描述");
    }

    @Test
    void parameterWithoutDescription_keepsExisting() throws Exception {
        MethodParameter mp = new MethodParameter(
                DocController.class.getMethod("list", String.class, String.class), 1);
        assertEquals("默认: x", extractor.paramDescription(mp, "默认: x"),
                "无 description 的 @Parameter 不应改动现有描述");
    }
}
