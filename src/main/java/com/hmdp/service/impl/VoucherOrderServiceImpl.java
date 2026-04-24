package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher == null) {
            return Result.fail("秒杀券不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        if (seckillVoucher.getBeginTime().isAfter(now)) {
            return Result.fail("秒杀尚未开始");
        }
        if (seckillVoucher.getEndTime().isBefore(now)) {
            return Result.fail("秒杀已经结束");
        }
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
//        //该锁在分布式系统中不能保证一人一单
//        synchronized(userId.toString().intern()) {//intern把字符串放进“字符串常量池”，并返回池里的唯一引用
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();   //获取当前数据的代理对象，只有代理对象才能让事务生效
//            return proxy.createVoucherOrder(voucherId);
//        }

        // 使用分布式锁
        //创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        boolean isLock = lock.tryLock(1200L);
        if (!isLock) {
            return Result.fail("不允许重复下单");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();   //获取当前数据的代理对象，只有代理对象才能让事务生效
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unLock();
        }
    }

    // 判断用户是否已经购买秒杀卷，未购买则创建订单
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
//        如果在此处加锁，在后面的代码执行完后，事务提交前；此时锁被释放，但是因为事务未被提交，数据库中没有购买信息，如果此时有别的线程过来执行，会导致超卖
//        synchronized(userId.toString().intern())
        int count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0) {
            return Result.fail("该用户已经购买过一次");
        }

        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        return Result.ok(orderId);

    }
}
