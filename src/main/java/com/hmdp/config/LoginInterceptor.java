package com.hmdp.config;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * 一个请求通常由 Tomcat 线程池里的一个线程来处理
 * 用户A请求到达
 *         ↓
 * Tomcat分配线程1
 *         ↓
 * LoginInterceptor.preHandle()    ← ThreadLocal.set(userA) 在线程1中存储
 *         ↓
 * UserController.me()    ← ThreadLocal.get() 从线程1中获取（拿到userA）
 *         ↓
 * LoginInterceptor.afterCompletion()    ← ThreadLocal.remove() 清理线程1的数据
 */
//登录拦截器，检查用户是否登录，阻止未授权访问，以及传递用户信息
@Component
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.判断是否需要拦截（Threadlocal中是否有用户）
        if(UserHolder.getUser() == null){
            // 拦截，设置状态码
            response.setStatus(401);
            // 返回401状态码
            return false;
        }
        // 2.有用户，则放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户，避免内存泄露
        UserHolder.removeUser();
    }
}
