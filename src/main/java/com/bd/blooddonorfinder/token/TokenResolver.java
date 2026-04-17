package com.bd.blooddonorfinder.token;

import com.bd.blooddonorfinder.utils.constants.CookieConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class TokenResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(Token.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        Class<?> clazz = parameter.getParameterType();
        if(! clazz.isAssignableFrom(BDFPortalToken.class)){
            return null;
        }
        HttpServletRequest httpServletRequest = webRequest.getNativeRequest(HttpServletRequest.class);

        BDFPortalToken portalToken = new BDFPortalToken();

        String accessToken = (String) (httpServletRequest != null ? httpServletRequest.getSession().getAttribute(CookieConstants.ACCESS_TOKEN) : null);
        if(!ObjectUtils.isEmpty(accessToken)) portalToken.setAccessToken(accessToken);

        String refreshToken = (String) (httpServletRequest != null ? httpServletRequest.getSession().getAttribute(CookieConstants.REFRESH_TOKEN) : null);
        if (org.apache.commons.lang3.ObjectUtils.isNotEmpty(refreshToken)) portalToken.setRefreshToken(refreshToken);

        return portalToken;
    }
}
