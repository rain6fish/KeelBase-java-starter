package cn.com.keelbase.example;

// SPDX-License-Identifier: Apache-2.0

import cn.com.keelbase.testsupport.KeelbaseContractTest;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 接入合规契约测试：继承 {@link KeelbaseContractTest} 即验证导出契约、受保护路径门控、
 * 委托验签访问——Java 团队在自己项目里照此把接入合规内建进 CI。
 */
@SpringBootTest
class ContractComplianceTest extends KeelbaseContractTest {
}
