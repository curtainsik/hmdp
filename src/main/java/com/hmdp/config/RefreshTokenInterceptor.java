package com.hmdp.config;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
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
//token刷新拦截器，不做实际的拦截作用，处于最外层，拦截所有请求，只负责刷新token有效期，刷新成功后，将用户信息存储到ThreadLocal中，方便后续使用
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取请求头中的token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            return true;
        }
        // 2.获取token获取redis中的用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        // 3.判断用户是否存在
        if (userMap.isEmpty()){
            return true;
        }
        // 4.将查询到的hash数据转为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 5.存在，保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);
        // 6.刷新token有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 7.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户，避免内存泄露
        UserHolder.removeUser();
    }
}
