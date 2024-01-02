package io.github.gaojindeng.lock.dubbo.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class RpcLock extends AbstractRpcLock {
    private final ReentrantLock reentrantLock = new ReentrantLock();
    private final RLock rLock;


    private volatile boolean openRedisLock;

    public RpcLock(RedissonClient redissonClient, String lockKey, boolean isRedisLock) {
        super(lockKey, isRedisLock);
        rLock = redissonClient.getLock("rpc:lock:" + lockKey);
    }

    public RLock getRedisLock() {
        if (!redisLockSwitch) {
            return null;
        }
        return rLock;
    }

    @Override
    public void lock() {
        System.out.println("开始获取锁" + Thread.currentThread().getName());
        reentrantLock.lock();
        RLock redisLock = getRedisLock();
        if (redisLock != null) {
            System.out.println("获取锁成功-redis" + Thread.currentThread().getName());
            redisLock.lock();
            openRedisLock = true;
        }
        System.out.println("获取锁成功-总" + Thread.currentThread().getName());

    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        reentrantLock.lockInterruptibly();
        RLock redisLock = getRedisLock();
        if (redisLock != null) {
            redisLock.lockInterruptibly();
            openRedisLock = true;
        }
    }

    @Override
    public boolean tryLock() {
        boolean tryLock = reentrantLock.tryLock();
        RLock redisLock = getRedisLock();
        if (tryLock && redisLock != null) {
            boolean tryLock1 = redisLock.tryLock();
            if (!tryLock1) {
                reentrantLock.unlock();
            }
            openRedisLock = true;
            return tryLock1;
        }
        return tryLock;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        boolean tryLock = reentrantLock.tryLock(time, unit);
        RLock redisLock = getRedisLock();
        if (tryLock && redisLock != null) {
            boolean tryLock1 = redisLock.tryLock(time, unit);
            if (!tryLock1) {
                reentrantLock.unlock();
            }
            openRedisLock = true;
            return tryLock1;
        }
        return tryLock;
    }

    @Override
    public void unlock() {
        if (openRedisLock) {
            try {
                rLock.unlock();
                System.out.println("释放锁成功-redis" + Thread.currentThread().getName());

            } catch (Exception ignore) {
            }
        }
        reentrantLock.unlock();
        System.out.println("释放锁成功-总" + Thread.currentThread().getName());

    }

}
