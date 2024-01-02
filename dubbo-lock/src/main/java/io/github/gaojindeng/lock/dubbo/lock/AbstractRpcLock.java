package io.github.gaojindeng.lock.dubbo.lock;

import io.github.gaojindeng.lock.dubbo.filter.RpcLockConsumerFilter;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class AbstractRpcLock implements Lock {
    protected String lockKey;

    /**
     * 是否使用redis进行加锁
     */
    protected volatile boolean redisLockSwitch;
    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final ConcurrentLinkedQueue<String> workerQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean active = true;


    public AbstractRpcLock(String lockKey, boolean redisLockSwitch) {
        this.lockKey = lockKey;
        this.redisLockSwitch = redisLockSwitch;
    }


    public void setRedisLockSwitch(boolean redisLockSwitch) {
        this.redisLockSwitch = redisLockSwitch;
    }

    public Lock addWorker() {
        ReentrantReadWriteLock.ReadLock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            if (active) {
                workerQueue.add(Thread.currentThread().getName());
                return this;
            } else {
                //说明已经被移除了
                return null;
            }
        } finally {
            readLock.unlock();
        }
    }

    public void releaseWorker() {
        workerQueue.remove(Thread.currentThread().getName());
        if (workerQueue.size() > 0) {
            return;
        }
        ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();
        writeLock.lock();

        try {
            if (workerQueue.size() < 1) {
                active = false;
                //移除自己
                RpcLockConsumerFilter.lockMap.remove(lockKey);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Condition newCondition() {
        return null;
    }
}
