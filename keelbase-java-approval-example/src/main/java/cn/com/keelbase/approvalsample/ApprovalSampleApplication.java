// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.approvalsample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Integrator Kit Reference Project：传统 Java 审批流 → AI Approval 真实 Java 样板。
 *
 * <p>模拟一个存量 Java 审批系统（差旅报销/采购申请），用 keelbase-java-starter 把 REST 端点
 * 声明为治理 AI 工具（读 R1 自动 / 审批决定 R3 确认 / 撤销补偿），与 KeelBase AI Approval
 * 旗舰（AI 预审 + 人工复核）对齐。
 */
@SpringBootApplication
public class ApprovalSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApprovalSampleApplication.class, args);
    }
}
