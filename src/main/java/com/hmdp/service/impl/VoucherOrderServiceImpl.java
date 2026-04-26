package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.*;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {   //类一加载，就把lua脚本准备好
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        //ClassPathResource(包装成资源)表示去资源目录找unlock.lua文件;setLocation(接收一个资源)把脚本文件的位置告诉DefaultRedisScript
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        //执行这个Lua脚本后，结果按Long接收
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);

    // 线程池
    private static ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct   //类初始化完成时执行
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 获取用户
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {   // 这里的锁机制不是必要的，因为秒杀资格已经完全由Lua保证，createVoucherOrder()只是把Lua已经确认过的结果落库
            log.error("不允许重复下单");
            return;
        }
        try {
//            // 当前为子线程，无法通过Thraedlocal获取代理对象，要去主线程获取
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();   //获取当前数据的代理对象，只有代理对象才能让事务生效
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    private IVoucherOrderService proxy;
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        //通过lua脚本判断是否有库存，是否一人一单
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),   //返回一个只包含一个元素的不可变列表
                voucherId.toString(), userId.toString(),String.valueOf(orderId)
        );
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 为0，有购买资格，将订单信息保存到阻塞队列中
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 放入阻塞队列
        orderTasks.add(voucherOrder);
        return Result.ok(orderId);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        if (seckillVoucher == null) {
//            return Result.fail("秒杀券不存在");
//        }
//
//        LocalDateTime now = LocalDateTime.now();
//        if (seckillVoucher.getBeginTime().isAfter(now)) {
//            return Result.fail("秒杀尚未开始");
//        }
//        if (seckillVoucher.getEndTime().isBefore(now)) {
//            return Result.fail("秒杀已经结束");
//        }
//        if (seckillVoucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
////        //该锁在分布式系统中不能保证一人一单
////        synchronized(userId.toString().intern()) {//intern把字符串放进“字符串常量池”，并返回池里的唯一引用
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();   //获取当前数据的代理对象，只有代理对象才能让事务生效
////            return proxy.createVoucherOrder(voucherId);
////        }
//
/// /        // 使用自己编写的分布式锁
/// /        //创建锁对象
/// /        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
/// /        boolean isLock = lock.tryLock(1200L);
/// /        if (!isLock) {
/// /            return Result.fail("不允许重复下单");
/// /        }
/// /        try {
/// /            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();   //获取当前数据的代理对象，只有代理对象才能让事务生效
/// /            return proxy.createVoucherOrder(voucherId);
/// /        } finally {
/// /            lock.unLock();
/// /        }
//
//        // 使用Redisson分布式锁
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        boolean isLock = lock.tryLock();
//        if (!isLock) {
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();   //获取当前数据的代理对象，只有代理对象才能让事务生效
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//    }

    // 判断用户是否已经购买秒杀卷，未购买则创建订单
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
//        如果在此处加锁，在后面的代码执行完后，事务提交前；此时锁被释放，但是因为事务未被提交，数据库中没有购买信息，如果此时有别的线程过来执行，会导致超卖
//        synchronized(userId.toString().intern())
        int count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherOrder.getVoucherId())
                .count();
        if (count > 0) {
            log.error("用户已经购买过一次");
            return;
        }

        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足");
            return;
        }

        save(voucherOrder);

    }
}
