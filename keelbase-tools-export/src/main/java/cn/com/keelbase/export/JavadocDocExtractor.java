// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.export;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Reads runtime-retained Javadoc through {@code therapi-runtime-javadoc}, discovered
 * reflectively: the starter takes no compile dependency and silently supplies nothing when
 * the consumer has not opted in (same pattern as {@link SwaggerDocExtractor}).
 *
 * <p>通过 {@code therapi-runtime-javadoc} 反射读取运行时保留的 Javadoc：starter 不引入编译依赖；
 * 消费方未接入时静默不提供内容（与 {@link SwaggerDocExtractor} 同一模式）。
 *
 * <p>Javadoc is not part of the class file by default. A consumer must add
 * {@code therapi-runtime-javadoc-scribe} as an annotation processor (and the
 * {@code therapi-runtime-javadoc} runtime) for any of this to activate.
 *
 * <p>Javadoc 缺省不在 class 文件里。消费方需把 {@code therapi-runtime-javadoc-scribe} 作为注解处理器
 * 接入（并带上 {@code therapi-runtime-javadoc} 运行时），这些提取才会生效。
 */
final class JavadocDocExtractor {

    private static final String RUNTIME_CLASS = "com.github.therapi.runtimejavadoc.RuntimeJavadoc";

    private static final Class<?> RUNTIME;
    private static final Method GET_METHOD;
    private static final Method GET_FIELD;
    private static final Method GET_CLASS;

    static {
        Class<?> runtime = null;
        Method getMethod = null;
        Method getField = null;
        Method getClass = null;
        try {
            runtime = Class.forName(RUNTIME_CLASS);
            getMethod = runtime.getMethod("getJavadoc", Method.class);
            getField = runtime.getMethod("getJavadoc", Field.class);
            getClass = runtime.getMethod("getJavadoc", Class.class);
        } catch (Throwable ignored) {
            runtime = null;
        }
        RUNTIME = runtime;
        GET_METHOD = getMethod;
        GET_FIELD = getField;
        GET_CLASS = getClass;
    }

    /**
     * Whether runtime Javadoc is available on the classpath.
     *
     * <p>运行时 Javadoc 是否在 classpath 上可用。
     */
    boolean available() {
        return RUNTIME != null;
    }

    /**
     * Method summary (the main Javadoc comment). Whitespace is collapsed so the exported
     * schema is stable across source formatting.
     *
     * <p>方法摘要（主 Javadoc 注释）。空白被折叠，导出 schema 不随源码排版变化。
     */
    String toolDescription(Method method) {
        return text(call(invoke(GET_METHOD, method), "getComment"));
    }

    /**
     * {@code @param} description for one parameter, or an empty string when absent.
     *
     * <p>某个参数的 {@code @param} 描述；缺失时返回空串。
     */
    String paramDescription(Method method, String paramName) {
        if (paramName == null || paramName.isBlank()) {
            return "";
        }
        Object methodJavadoc = invoke(GET_METHOD, method);
        for (Object param : listOf(methodJavadoc, "getParams")) {
            if (paramName.equals(stringOf(param, "getName"))) {
                return text(call(param, "getComment"));
            }
        }
        return "";
    }

    /**
     * Field description. For record components, falls back to the record class's
     * {@code @param} tags (the scribe exposes them as record components, not fields).
     *
     * <p>字段描述。record 组件回退到 record 类的 {@code @param} 标签
     * （scribe 把它们暴露为 record components，而非字段）。
     */
    String fieldDescription(Field field) {
        if (field == null) {
            return "";
        }
        String direct = text(call(invoke(GET_FIELD, field), "getComment"));
        if (!direct.isBlank()) {
            return direct;
        }
        if (field.getDeclaringClass().isRecord()) {
            Object classJavadoc = invoke(GET_CLASS, field.getDeclaringClass());
            for (Object component : listOf(classJavadoc, "getRecordComponents")) {
                if (field.getName().equals(stringOf(component, "getName"))) {
                    return text(call(component, "getComment"));
                }
            }
        }
        return "";
    }

    private static Object invoke(Method method, Object target) {
        if (method == null || target == null) {
            return null;
        }
        try {
            return method.invoke(null, target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<?> listOf(Object target, String getter) {
        Object value = call(target, getter);
        return value instanceof List<?> list ? list : List.of();
    }

    private static String stringOf(Object target, String getter) {
        Object value = call(target, getter);
        return value instanceof String s ? s : null;
    }

    private static Object call(Object target, String getter) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(getter).invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String text(Object comment) {
        if (comment == null) {
            return "";
        }
        try {
            String rendered = comment.toString();
            return rendered == null ? "" : rendered.replaceAll("\\s+", " ").trim();
        } catch (Throwable ignored) {
            return "";
        }
    }
}
