package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        // 配置
        Config config = new Config();
        //添加redis地址，这里添加了单点的地址，未来使用集群也可以用useClusterServers添加集群地址
        config.useSingleServer().setAddress("redis://192.168.6.128:6379").setPassword("lyy2628474445");
        // 创建RedissonClient对象
        return Redisson.create(config);
    }

//    @Bean
//    public RedissonClient redissonClient2(){
//        // 配置
//        Config config = new Config();
//        //添加redis地址，这里添加了单点的地址，未来使用集群也可以用useClusterServers添加集群地址
//        config.useSingleServer().setAddress("redis://192.168.6.128:6380").setPassword("lyy2628474445");
//        // 创建RedissonClient对象
//        return Redisson.create(config);
//    }
//
//    @Bean
//    public RedissonClient redissonClient3(){
//        // 配置
//        Config config = new Config();
//        //添加redis地址，这里添加了单点的地址，未来使用集群也可以用useClusterServers添加集群地址
//        config.useSingleServer().setAddress("redis://192.168.6.128:6381").setPassword("lyy2628474445");
//        // 创建RedissonClient对象
//        return Redisson.create(config);
//    }
}
