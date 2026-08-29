package cn.com.keelbase.example;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 跟进任务查询 DTO（继承 {@link FollowupQueryBase}）。
 *
 * <p>演示 {@code @KeelbaseTool} 参数提取的进阶语义：
 * <ul>
 *   <li><b>继承字段</b>（{@code page}/{@code limit} 来自父类）也会导出为工具参数；</li>
 *   <li>{@code @JsonProperty(required = true)} → 工具参数必填；</li>
 *   <li>{@code @JsonIgnore} 字段（{@code serverHint}）不导出。</li>
 * </ul>
 */
public class FollowupQuery extends FollowupQueryBase {

    @JsonProperty(required = true)
    private String keyword;

    @JsonIgnore
    private String serverHint;

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getServerHint() { return serverHint; }
    public void setServerHint(String serverHint) { this.serverHint = serverHint; }
}
