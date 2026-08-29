package cn.com.keelbase.export;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 从 {@code @RequestBody} DTO 收集工具参数的字段工具。
 *
 * <p>相比裸 {@code getDeclaredFields()}，本工具对齐 Jackson 序列化语义：
 * <ul>
 *   <li>沿继承链向上收集（含父类 DTO 字段，至 Object 止）——子类优先，同名子类覆盖父类；</li>
 *   <li>跳过 {@code static} / {@code transient} / synthetic 字段；</li>
 *   <li>跳过 {@code @JsonIgnore} 字段与类级 {@code @JsonIgnoreProperties} 声明的字段；</li>
 *   <li>尊重 {@code @JsonProperty}（参数名 + required）；record 天然支持（组件即字段）。</li>
 * </ul>
 */
final class RequestBodyFields {

    private RequestBodyFields() {
    }

    /** DTO 中一个可导出为工具参数的业务字段。 */
    record BodyField(String name, String type, String description, boolean required) {
    }

    static List<BodyField> of(Class<?> type) {
        List<BodyField> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            JsonIgnoreProperties classIgnores = c.getAnnotation(JsonIgnoreProperties.class);
            for (Field f : c.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isSynthetic()) {
                    continue;
                }
                if (f.isAnnotationPresent(JsonIgnore.class)) {
                    continue;
                }
                if (classIgnores != null && Arrays.asList(classIgnores.value()).contains(f.getName())) {
                    continue;
                }
                JsonProperty jp = f.getAnnotation(JsonProperty.class);
                String name = jp != null && !jp.value().isBlank() ? jp.value() : f.getName();
                boolean required = jp != null && jp.required()
                        || f.isAnnotationPresent(NotNull.class)
                        || f.isAnnotationPresent(NotBlank.class)
                        || f.isAnnotationPresent(NotEmpty.class);
                fields.add(new BodyField(name, TypeMapper.map(f.getType()), fieldDescription(f), required));
            }
        }
        return fields;
    }

    private static String fieldDescription(Field f) {
        if (f.getType().isEnum()) {
            Object[] constants = f.getType().getEnumConstants();
            return "可选: " + Arrays.stream(constants)
                    .map(Object::toString).collect(Collectors.joining("/"));
        }
        return "";
    }
}
