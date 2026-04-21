package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    // 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    // 利用泛型，传递id与id的前缀，返回值的类型，以及数据库的查询方法，过期时间参数；任意类型都可以使用此方法解决缓存穿透
    public <R,ID> R queryWithPenetration(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis中查询商铺缓存
        String Json =stringRedisTemplate.opsForValue().get(key);
        // 2.判断redis中是否存在
        if(StrUtil.isNotBlank(Json)){
            // 3.存在则直接返回,(把JSON字符串转成Java对象)
            return JSONUtil.toBean(Json, type);
        }
        //判断命中是否是空值
        if(Json != null){
            return null;
        }
        // 4.不存在则根据id查询mysql数据库
        R r = dbFallback.apply(id);
        // 5.mysql不存在，返回错误
        if (r == null) {
            //将空值写入redis，解决缓存穿透
            stringRedisTemplate.opsForValue().set(key, "",CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        // 6.mysql中存在，写入redis
        this.set(key, r, time, unit);
        // 7.返回数据
        return r;
    }

    //创建一个固定大小的线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    // 根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
    public <R,ID> R queryWithBreakdownByLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis中查询商铺缓存
        String Json =stringRedisTemplate.opsForValue().get(key);
        // 2.判断redis中是否存在
        if(StrUtil.isBlank(Json)){
            // 3.不存在则直接返回null
            return null;
        }
        // 4.命中，需要先把json反序列化
        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject)redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断逻辑过期时间是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 5.1.未过期直接返回店铺信息
            return r;
        }
        // 5.2.已过期,需要缓存重建
        // 6.重建缓存
        // 6.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2判断是否获取锁成功
        if(isLock){
            // 6.3.成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {   //异步执行任务，把任务交给线程池，由线程池中的某个线程去执行
                try {
                    // 重建缓存
                    // 查询数据库
                    R r1 = dbFallback.apply(id);
                    // 写入redis
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally{
                    // 释放锁
                    unLock(lockKey);
                }
            });
        }
        // 6.4返回过期的店铺信息
        return r;
    }

    // 实现互斥锁，获取锁成功则返回true，获取锁失败则返回false
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);   //将包装类转换成基本类型，避免空指针null，null会被当成false返回
    }

    // 释放锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}

