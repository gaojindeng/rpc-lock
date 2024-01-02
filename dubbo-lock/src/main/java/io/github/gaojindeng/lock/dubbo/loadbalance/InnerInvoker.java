package io.github.gaojindeng.lock.dubbo.loadbalance;

import io.github.gaojindeng.lock.dubbo.LockConstants;
import org.apache.dubbo.rpc.Invoker;

public class InnerInvoker<T> {

    private final String invokerKey;

    private final Invoker<T> invoker;
    /**
     * 可用时间
     */
    public volatile Long activeTime;

    /**
     * 锁降级时间
     */
    public volatile Long lockDegradedTime;

    public long lockDegradedMillisecond;

    public InnerInvoker(String invokerKey, String methodName, Invoker<T> invoker, Long firstRefreshTime) {
        this.invokerKey = invokerKey;
        this.invoker = invoker;
        this.lockDegradedMillisecond = invoker.getUrl().getMethodParameter(methodName, LockConstants.REDIS_LOCK_EXPIRE_SECOND, 20) * 1000L;
        if (firstRefreshTime != null) {
            setActiveTime(firstRefreshTime + this.lockDegradedMillisecond);
        }
    }

    public Invoker<T> getInvoker() {
        return invoker;
    }

    public Long getActiveTime() {
        return activeTime;
    }

    public Long getLockDegradedTime() {
        return lockDegradedTime;
    }

    public String getInvokerKey() {
        return invokerKey;
    }

    public long getLockDegradedMillisecond() {
        return lockDegradedMillisecond;
    }

    public void setActiveTime(long activeTime) {
        this.activeTime = activeTime;
        this.lockDegradedTime = activeTime + lockDegradedMillisecond;
    }
}
