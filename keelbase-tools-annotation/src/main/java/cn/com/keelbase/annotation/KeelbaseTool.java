package cn.com.keelbase.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明 Spring MVC handler 为 KeelBase 可用的 AI 工具。
 *
 * <p>可标注在<b>方法</b>（配合 {@code @GetMapping/@PostMapping/...} 映射注解）或
 * <b>类</b>（{@code @RestController}）上：
 * <ul>
 *   <li><b>方法级</b>：仅该方法工具化；name/description/riskLevel/revokePath 可覆盖类级。</li>
 *   <li><b>类级</b>：整个 controller 的<b>所有映射方法</b>一键工具化（名称 = 方法名
 *       camelCase → snake_case）；个别方法用 {@code @KeelbaseTool(enabled=false)} 排除
 *       （如补偿端点/辅助端点）。</li>
 * </ul>
 *
 * <p>KeelBase 会把这些端点暴露为 AI 工具：读方法（GET）默认 R1 自动执行，写方法
 * （POST/PUT/PATCH/DELETE）默认 R3 需人工确认，全部经委托身份 + 审计。
 *
 * <p>工具声明通过 {@code /keelbase/proxy-tools/export} 导出为 {@code ai_proxy_tools}
 * 配置，写入 KeelBase Settings 后（重启生效）即注册为 AI 工具。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface KeelbaseTool {

    /**
     * 工具名。缺省 = 方法名 camelCase → snake_case（与 KeelBase 生成器 sanitizeToolName 同口径）。
     */
    String name() default "";

    /** 工具描述（给 LLM 的说明，建议必填，缺省导出时告警）。 */
    String description() default "";

    /** 风险级：AUTO 自动推断（GET→R1 / 写→R3），可显式覆盖 R0-R5。 */
    KeelbaseRiskLevel riskLevel() default KeelbaseRiskLevel.AUTO;

    /**
     * Java 端补偿端点路径（相对 baseUrl），如 {@code "DELETE /api/compensation/followups/{id}"}。
     * AI 写副作用撤销时 KeelBase 会调用该端点；缺省表示无本地撤销（诚实语义）。
     */
    String revokePath() default "";

    /**
     * per-tool audience 覆盖。缺省用全局 {@code keelbase.delegation.audience}。
     * 注意：当前 KeelBase 单个 {@code ai_proxy_tools} 配置只有一个顶层 audience，
     * 若设置此值必须等于顶层 audience，否则委托验签失败。
     */
    String audience() default "";

    /**
     * 工具化开关。类级标注时用 {@code enabled=false} 排除个别方法（补偿端点/辅助端点）；
     * 缺省 true。
     */
    boolean enabled() default true;
}
