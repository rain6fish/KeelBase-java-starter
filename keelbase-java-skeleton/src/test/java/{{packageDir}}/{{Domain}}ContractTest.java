// SPDX-License-Identifier: Apache-2.0

package {{packagePath}};

import cn.com.keelbase.testsupport.KeelbaseContractTest;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 接入合规契约测试（keelbase-test-support）：本地 {@code mvn test} 即守护——
 * ① 导出契约（工具非空 / audience 一致 / 存在带 revokePath 的写工具）
 * ② 受保护补偿路径：无 token → 401（fail-closed）
 * ③ 委托 JWT：共享 secret 签 → 补偿端点 200 幂等（验签通过）
 * 与 ci-template.yml 配合：新项目从第一天就在 CI 内建接入合规验证。
 */
@SpringBootTest
class {{Domain}}ContractTest extends KeelbaseContractTest {
    // 无额外逻辑——继承的 3 个断言即覆盖接入合规核心；按需加你自己的工具契约断言
}
