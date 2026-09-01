// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 显式启用 KeelBase 工具能力（委托验签过滤器 + 代理工具导出 + 补偿脚手架）。
 *
 * <p>通常无需手动添加——{@code keelbase-spring-boot-starter} 通过 Spring Boot
 * AutoConfiguration 自动装配。此注解仅用于需要显式控制装配顺序/开关的场景。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnableKeelbaseTools {
}
