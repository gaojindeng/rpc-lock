package io.github.gaojindeng.lock.dubbo.context;

import io.github.gaojindeng.lock.dubbo.LockConstants;
import io.github.gaojindeng.lock.dubbo.filter.RpcLockConsumerFilter;
import org.apache.dubbo.rpc.RpcContext;

import java.util.concurrent.locks.Lock;

public class LockContext {

    public static void setLockKey(String key) {
        RpcContext.getServiceContext().setAttachment(LockConstants.LOCK_KEY, key);
    }

    public static String getLockKey() {
        return RpcContext.getServiceContext().getAttachment(LockConstants.LOCK_KEY);
    }

    public static void setRedisLock() {
        RpcContext.getServiceContext().setAttachment(LockConstants.REDIS_LOCK, true);
    }

    public static boolean isRedisLock() {
        Object isRedisLock = RpcContext.getServiceContext().getObjectAttachment(LockConstants.REDIS_LOCK);
        return isRedisLock != null;
    }

    public static Lock getLock() {
        return RpcLockConsumerFilter.lockMap.get(getLockKey());
    }
}
