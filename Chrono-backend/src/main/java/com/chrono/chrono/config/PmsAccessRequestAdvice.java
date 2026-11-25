package com.chrono.chrono.config;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import java.lang.reflect.Type;

@ControllerAdvice(basePackages = "com.chrono.chrono.controller.pms")
public class PmsAccessRequestAdvice extends RequestBodyAdviceAdapter {
    private final PmsAccessPolicy policy;
    public PmsAccessRequestAdvice(PmsAccessPolicy policy) { this.policy = policy; }
    @Override public boolean supports(MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) { return true; }
    @Override public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter parameter,
                                          Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) policy.authorize(attributes.getRequest(), body);
        return body;
    }
}
