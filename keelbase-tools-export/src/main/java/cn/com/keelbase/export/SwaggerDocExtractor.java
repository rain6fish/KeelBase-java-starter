// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.export;

import org.springframework.core.MethodParameter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 从 Springdoc/OpenAPI 注解提取工具与参数描述（反射探测，classpath 无 swagger 时静默跳过）。
 *
 * <p>Java 团队用 springdoc 给端点写文档时，工具描述/参数描述可从
 * {@code @Operation(summary/description)} / {@code @Parameter(description)} 自动提取，
 * 不必在 {@code @KeelbaseTool} 里重复写。仅探测，不引入 swagger 编译依赖。
 */
final class SwaggerDocExtractor {

    private static final String OP_CLASS = "io.swagger.v3.oas.annotations.Operation";
    private static final String PARAM_CLASS = "io.swagger.v3.oas.annotations.Parameter";
    private static final String SCHEMA_CLASS = "io.swagger.v3.oas.annotations.media.Schema";

    /**
     * {@code @RequestBody} 字段描述：{@code @Schema(description)} 优先，否则 fallback
     * （枚举可选值等）。静态便于 {@link RequestBodyFields} 复用。
     */
    static String fieldDescription(Field field, String fallback) {
        Annotation schema = findAnnotation(field.getAnnotations(), SCHEMA_CLASS);
        if (schema != null) {
            String d = invokeString(schema, "description");
            if (d != null && !d.isBlank()) {
                return d;
            }
        }
        return fallback;
    }

    /**
     * 工具描述：{@code @Operation.summary} 优先，其次 {@code description}，无则 fallback
     * （{@code @KeelbaseTool.description} 未显式时使用）。
     */
    String toolDescription(Method method, String fallback) {
        Annotation op = findAnnotation(method.getAnnotations(), OP_CLASS);
        if (op != null) {
            String summary = invokeString(op, "summary");
            if (summary != null && !summary.isBlank()) {
                return summary;
            }
            String description = invokeString(op, "description");
            if (description != null && !description.isBlank()) {
                return description;
            }
        }
        return fallback;
    }

    /**
     * 参数描述：{@code @Parameter(description)} 放前面，现有描述（枚举可选值/默认值）追加在后——
     * 业务描述优先（如 {@code 页码（从 1 起）；默认: 1}），现有为空则直接用 @Parameter。
     */
    String paramDescription(MethodParameter mp, String existing) {
        for (Annotation a : mp.getParameterAnnotations()) {
            if (a.annotationType().getName().equals(PARAM_CLASS)) {
                String d = invokeString(a, "description");
                if (d != null && !d.isBlank()) {
                    return existing == null || existing.isBlank() ? d : d + "；" + existing;
                }
            }
        }
        return existing;
    }

    private static Annotation findAnnotation(Annotation[] annotations, String typeName) {
        for (Annotation a : annotations) {
            if (a.annotationType().getName().equals(typeName)) {
                return a;
            }
        }
        return null;
    }

    private static String invokeString(Annotation annotation, String attribute) {
        try {
            return (String) annotation.annotationType().getMethod(attribute).invoke(annotation);
        } catch (Exception e) {
            return null;
        }
    }
}
