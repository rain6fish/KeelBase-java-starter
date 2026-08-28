package cn.com.keelbase.delegation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 委托验签过滤器配置（前缀 {@code keelbase.delegation}）。
 *
 * <ul>
 *   <li>{@code keelbase.delegation.secret}：与 KeelBase 共享的 {@code DELEGATION_SECRET}
 *       （HS256，至少 32 字节）。<b>必填，缺省启动即失败。</b></li>
 *   <li>{@code keelbase.delegation.audience}：目标系统标识，必须与 KeelBase 侧
 *       {@code ai_proxy_tools} 顶层 {@code audience} 一致。<b>必填。</b></li>
 *   <li>{@code keelbase.delegation.issuer}：可选，缺省 {@code keelbase}。</li>
 *   <li>{@code keelbase.delegation.paths}：受保护路径前缀列表。匹配到的请求在<b>无</b>
 *       Authorization 头时也拒绝（fail-closed）；未列路径无头则放行（fail-open）。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "keelbase.delegation")
public class DelegationProperties {

    /** 必填：与 KeelBase 共享的委托密钥（HS256，≥32 字节）。 */
    private String secret;

    /** 必填：目标系统 audience（必须等于 KeelBase ai_proxy_tools 顶层 audience）。 */
    private String audience;

    /** 可选：iss 校验，缺省 keelbase。 */
    private String issuer = "keelbase";

    /** 受保护路径前缀（fail-closed 的路径）；未列路径无头时 fail-open 放行。 */
    private List<String> paths = new ArrayList<>();

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public List<String> getPaths() { return paths; }
    public void setPaths(List<String> paths) { this.paths = paths; }
}
