// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.delegation;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 解析 {@code @DelegationUser DelegationPrincipal} 参数：从 request attribute 读取
 * {@link DelegationAuthFilter#PRINCIPAL_ATTR}。无验签通过的身份时返回 {@code null}。
 */
public class DelegationUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(DelegationUser.class)
                && DelegationPrincipal.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object attr = webRequest.getAttribute(DelegationAuthFilter.PRINCIPAL_ATTR,
                NativeWebRequest.SCOPE_REQUEST);
        return attr instanceof DelegationPrincipal ? attr : null;
    }
}
