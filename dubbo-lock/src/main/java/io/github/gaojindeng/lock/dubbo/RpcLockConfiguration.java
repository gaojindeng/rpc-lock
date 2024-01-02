package io.github.gaojindeng.lock.dubbo;

import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RpcLockConfiguration {

    public static RedissonClient redissonClient;

    @Autowired
    public RpcLockConfiguration(RedissonClient redissonClient) {
        RpcLockConfiguration.redissonClient = redissonClient;
    }

}
