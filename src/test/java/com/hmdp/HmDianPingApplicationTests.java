package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private CacheClient cacheClient;
    @Resource
    private ShopServiceImpl shopService;

    // 手动添加热点key，同时会添加逻辑过期时间
    @Test
    void testSaveShop() throws InterruptedException {
        shopService.saveShopToRedis(1L,10L);
    }
}
