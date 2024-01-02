package io.github.gaojindeng.lock.dubbo.filter;

import io.github.gaojindeng.lock.dubbo.RpcLockConfiguration;
import io.github.gaojindeng.lock.dubbo.context.LockContext;
import io.github.gaojindeng.lock.dubbo.lock.RpcLock;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Activate(group = CommonConstants.PROVIDER)
public class RpcLockConsumerFilter implements Filter {
    public static final Map<String, RpcLock> lockMap = new ConcurrentHashMap<>();

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // 1.获取需要加锁的key
        String lockKey = LockContext.getLockKey();
        if (lockKey == null || lockKey.isEmpty()) {
            return invoker.invoke(invocation);
        }

        // 2.根据key获取对应的RpcLock
        RpcLock rpcLock = getLock(lockKey, LockContext.isRedisLock());
        try {
            // 3.具体接口调用
            return invoker.invoke(invocation);
        } finally {
            // 4.释放线程占用，如果都没有线程占用，则从map中移除该rpcLock对象
            rpcLock.releaseWorker();
        }
    }

    private RpcLock getLock(String lockKey, boolean isRedisLock) {
        // 从map中获取锁对象，如果不存在则创建
        RpcLock rpcLock = lockMap.computeIfAbsent(lockKey, k -> createLock(lockKey, isRedisLock));

        // addWorker就是添加线程栈用，如果添加失败，说明被其他线程移除了，需要重新创建
        while ((rpcLock = (RpcLock) rpcLock.addWorker()) == null) {
            rpcLock = lockMap.computeIfAbsent(lockKey, k -> createLock(lockKey, isRedisLock));
            rpcLock.setRedisLockSwitch(isRedisLock);
        }
        return rpcLock;
    }

    public RpcLock createLock(String lockKey, boolean isRedisLock) {
        return new RpcLock(RpcLockConfiguration.redissonClient, lockKey, isRedisLock);
    }


}