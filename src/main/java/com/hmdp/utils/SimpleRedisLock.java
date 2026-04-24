package com.hmdp.utils;


import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;


import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    //构造方法
    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    //Spring Data Redis提供的脚本封装类。 这里的泛型是Long，表示“这个Lua脚本执行后的返回值，Java端按Long接”
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {   //类一加载，就把解锁脚本准备好
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        //ClassPathResource(包装成资源)表示去资源目录找unlock.lua文件;setLocation(接收一个资源)把脚本文件的位置告诉DefaultRedisScript
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        //执行这个Lua脚本后，结果按Long接收
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识
        String threadId =ID_PREFIX + Thread.currentThread().getId();   //这里Thread.currentThread().getId()在不同的tomcat中是有可能重复的，所以要加上UUID
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);   //如果success是null，不会抛出NullPointerException，而是返回false
    }

//    @Override
//    public void unLock() {
//        // 获取线程标识
//        String threadId =ID_PREFIX + Thread.currentThread().getId();
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        // / 判断当前线程的标识符是否与Redis中存储的标识符相同
//        //判断和释放锁是两个动作，如果由于某种原因导致判断成功后阻塞，锁达到了TTL被释放，那么此时其它线程可能获得锁，导致当前线程将其它线程的锁释放
//        if(threadId.equals(id)){
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }

    @Override
    public void unLock() {
        // 用lua脚本保证threadId.equals(id)判断与锁的释放的原子性
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),   //返回一个只包含一个元素的不可变列表
                ID_PREFIX + Thread.currentThread().getId());
    }
}
