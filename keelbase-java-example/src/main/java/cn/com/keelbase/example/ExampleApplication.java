package cn.com.keelbase.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 示例 Spring Boot 应用：模拟一个「存量 Java 系统」接入 KeelBase。
 *
 * <p>暴露三类能力：
 * <ul>
 *   <li>读端点（GET）—— AI 读工具，R1 自动；</li>
 *   <li>写端点（POST）—— AI 写工具，R3 需人工确认，撤销走补偿端点；</li>
 *   <li>补偿端点（DELETE）—— KeelBase 撤销 AI 副作用时调用（幂等）。</li>
 * </ul>
 *
 * <p>启动后用 {@code GET /keelbase/proxy-tools/export} 导出 ai_proxy_tools 配置，
 * 写入 KeelBase Settings（重启生效）即完成接入。
 */
@SpringBootApplication
public class ExampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}
