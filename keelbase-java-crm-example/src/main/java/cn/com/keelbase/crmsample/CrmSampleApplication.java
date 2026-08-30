package cn.com.keelbase.crmsample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Integrator Kit Reference Project：传统 Java CRM → AI CRM 真实 Java 样板。
 *
 * <p>模拟一个存量 Java CRM（客户/订单/跟进任务），用 keelbase-java-starter 把 REST 端点
 * 声明为治理 AI 工具（读 R1 自动 / 写 R3 确认 / 撤销补偿），域与
 * {@code specs/external-crm.openapi.json}（B 路径 OpenAPI 代理）完全对齐。
 */
@SpringBootApplication
public class CrmSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmSampleApplication.class, args);
    }
}
