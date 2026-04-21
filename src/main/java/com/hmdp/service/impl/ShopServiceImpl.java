package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
        // 互斥锁解决缓存穿透+解决缓存击穿
//        Shop shop = queryWithBreakdownByMutex(id);

        // 解决缓存穿透
//        Shop shop = cacheClient.queryWithPenetration(
//                CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 逻辑过期解决缓存穿透
        Shop shop = cacheClient.queryWithBreakdownByLogicalExpire(
                CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);   //this::getById为方法的引用，即现成的方法直接拿来用；id1->getById(id1)为Lambda，自己写一个匿名函数；它们在这里的作用是等价的
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除redis缓存；Cache Aside Pattern解决数据一致性问题
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

//    //创建一个固定大小的线程池
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
//    // 通过逻辑过期解决缓存击穿
//    public Shop queryWithBreakdownByLogicalExpire(Long id) {
//        // 1.从redis中查询商铺缓存
//        String shopJson =stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        // 2.判断redis中是否存在
//        if(StrUtil.isBlank(shopJson)){
//            // 3.不存在则直接返回null
//            return null;
//        }
//        // 4.命中，需要先把json反序列化
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject)redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        // 5.判断逻辑过期时间是否过期
//        if(expireTime.isAfter(LocalDateTime.now())){
//            // 5.1.未过期直接返回店铺信息
//            return shop;
//        }
//        // 5.2.已过期,需要缓存重建
//        // 6.重建缓存
//        // 6.1.获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        // 6.2判断是否获取锁成功
//        if(isLock){
//            // 6.3.成功，开启独立线程，实现缓存重建
//            CACHE_REBUILD_EXECUTOR.submit(() -> {   //异步执行任务，把任务交给线程池，由线程池中的某个线程去执行
//                try {
//                    // 重建缓存
//                    this.saveShopToRedis(id,20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }finally{
//                    // 释放锁
//                    unLock(lockKey);
//                }
//            });
//        }
//        // 6.4返回过期的店铺信息
//        return shop;
//    }

    // 通过互斥锁解决缓存击穿
//    public Shop queryWithBreakdownByMutex(Long id) {
//        // 1.从redis中查询商铺缓存
//        String shopJson =stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        // 2.判断redis中是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            // 3.存在则直接返回,(把JSON字符串转成Java对象)
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//
//        if(shopJson != null){
//            return null;
//        }
//        // 4.判断命中是否是空值
//        Shop shop = null;
//        try {
//            // 4.1.获取互斥锁
//            boolean isLock = tryLock(LOCK_SHOP_KEY + id);
//            // 4.2.判断是否获取成功
//            if(!isLock){
//                // 4.3失败，则休眠并重试
//                Thread.sleep(50);
//                return queryWithBreakdownByMutex(id);
//            }
//            // 4.4不存在则根据id查询mysql数据库
//            shop = getById(id);
//            // 模拟重建的时延，模拟缓存击穿的情景下的缓存业务重建较复杂的key，用一个时延模拟
//            Thread.sleep(200);
//            // 5.mysql不存在，返回错误
//            if (shop == null) {
//                //将空值写入redis，解决缓存穿透
//                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "",CACHE_NULL_TTL, TimeUnit.MINUTES);
//                //返回错误信息
//                return null;
//            }
//            // 6.mysql中存在，写入redis,(把Java对象转成JSON格式的字符串)
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally{
//            // 7.释放互斥锁
//            unLock(LOCK_SHOP_KEY + id);
//        }
//        // 8.返回数据
//        return shop;
//    }

    // 解决缓存穿透
//    public Shop queryWithPenetration(Long id) {
//        // 1.从redis中查询商铺缓存
//        String shopJson =stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        // 2.判断redis中是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            // 3.存在则直接返回,(把JSON字符串转成Java对象)
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //判断命中是否是空值
//        if(shopJson != null){
//            return null;
//        }
//        // 4.不存在则根据id查询mysql数据库
//        Shop shop = getById(id);
//        // 5.mysql不存在，返回错误
//        if (shop == null) {
//            //将空值写入redis，解决缓存穿透
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "",CACHE_NULL_TTL, TimeUnit.MINUTES);
//            //返回错误信息
//            return null;
//        }
//        // 6.mysql中存在，写入redis,(把Java对象转成JSON格式的字符串)
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        // 7.返回数据
//        return shop;
//    }

    // 实现互斥锁，获取锁成功则返回true，获取锁失败则返回false
//    private boolean tryLock(String key) {
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);   //将包装类转换成基本类型，避免空指针null，null会被当成false返回
//    }

    // 释放锁
//    private void unLock(String key) {
//        stringRedisTemplate.delete(key);
//    }

    // 手动添加热点key缓存，设置逻辑过期时间，以解决缓存击穿
    public void saveShopToRedis(Long id, Long expireSeconds) throws InterruptedException {
        // 1.查询店铺数据
        Shop shop = getById(id);
        // 模拟缓存击穿的情景下的缓存业务重建较复杂的key，用一个时延模拟
        Thread.sleep(200);
        // 2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));   //在当前的过期时间增加指定的秒时间
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }


}
