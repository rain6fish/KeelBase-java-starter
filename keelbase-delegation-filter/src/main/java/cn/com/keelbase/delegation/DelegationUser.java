// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.delegation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 控制器方法参数注解：注入验签通过后的 {@link DelegationPrincipal}。
 *
 * <pre>{@code
 * @RestController
 * public class FollowupController {
 *   @DeleteMapping("/api/compensation/followups/{id}")
 *   public void revoke(@PathVariable Long id, @DelegationUser DelegationPrincipal principal) {
 *       // principal.identity() → 映射后的本地用户标识
 *   }
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface DelegationUser {
}
