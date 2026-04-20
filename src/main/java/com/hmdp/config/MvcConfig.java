package com.hmdp.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration   //配置类，spring启动时会自动加载这个类，执行里面的配置
public class MvcConfig implements WebMvcConfigurer {
    @Autowired
    private LoginInterceptor loginInterceptor;
    @Autowired
    private RefreshTokenInterceptor refreshTokenInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //token刷新拦截器
        registry.addInterceptor(refreshTokenInterceptor).addPathPatterns("/**").order(0);
        //登录拦截器
        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/blog/hot",
                        "/shop/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/voucher/**"
                ).order(1);
    }
}
