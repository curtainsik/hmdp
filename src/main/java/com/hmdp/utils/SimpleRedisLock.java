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
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识
        String threadId =ID_PREFIX + Thread.currentThread().getId();   //这里Thread.currentThread().getId()在不同的tomcat中是有可能重复的，所以要加上UUID
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
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
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }
}
