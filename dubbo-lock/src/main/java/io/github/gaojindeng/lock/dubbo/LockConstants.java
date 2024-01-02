package io.github.gaojindeng.lock.dubbo;

public interface LockConstants {
    String LOCK_KEY = "dubbo.lock.key";
    String REDIS_LOCK = "dubbo.lock.redis.flag";
    String HASH_NODES = "hash.nodes";
    String REDIS_LOCK_EXPIRE_SECOND = "redis.lock.expire.second";
}
