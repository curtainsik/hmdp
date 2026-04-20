package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import cn.hutool.core.lang.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;


/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码
     * @param phone
     * @return
     */
    @Override
    public Result sendCode(String phone) {
        // 1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            // 2.如果不合则返回错误
            return Result.fail("手机号格式错误");
        }
        // 3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);   //随机生成6位数字验证码
        // 4.保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 5.发送验证码(模拟)
        log.debug("发送验证码成功：{}",code);
        // 6.返回结果
        return Result.ok();
    }
    /**
     * 短信验证码登录、注册
     * @param loginForm
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            // 如果不符合则返回错误
            return Result.fail("手机号格式错误");
        }
        // 2.校验验证码
        String cachCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if(cachCode == null || !cachCode.equals(code)){
            // 3.不一致，报错
            return Result.fail("验证码错误");
        }
        // 4.一致，按手机号查询用户
        User user = query().eq("phone", phone).one();   //query返回链式查询包装器，eq等价于where phone = ？，one意思是查询一条记录
        // 5.判断用户是否存在
        if(user == null){
            // 6.不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }
        // 7.保存用户信息到redis
        // 7.1.随机生成token，作为登录令牌
        String token =UUID.randomUUID().toString(true);
        // 7.2.将用户转为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //把 userDTO 对象转成 Map<String,Object>，忽略 null 字段，并把所有字段值转成字符串
        Map<String, Object> userMap =BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue)->fieldValue.toString()));
        // 7.3.存储
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        // 7.4.设置token有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 8.返回token
        return  Result.ok(token);
    }

    /**
     * 注册阶段根据手机号创建的用户实体类
     *
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return null;
    }
}
