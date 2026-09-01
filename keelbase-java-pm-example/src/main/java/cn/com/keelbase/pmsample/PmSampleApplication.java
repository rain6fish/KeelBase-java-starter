// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.pmsample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Integrator Kit Reference Project：传统 Java PM → AI Project 真实 Java 样板。
 *
 * <p>模拟一个存量 Java 项目管理（项目/里程碑/任务），用 keelbase-java-starter 把 REST 端点
 * 声明为治理 AI 工具（读 R1 自动 / 写 R3 确认 / 撤销补偿），与 KeelBase AI Project 旗舰
 * （项目延期风险分析）对齐。
 */
@SpringBootApplication
public class PmSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(PmSampleApplication.class, args);
    }
}
