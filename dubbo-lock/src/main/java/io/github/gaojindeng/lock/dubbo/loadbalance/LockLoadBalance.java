package io.github.gaojindeng.lock.dubbo.loadbalance;

import io.github.gaojindeng.lock.dubbo.LockConstants;
import io.github.gaojindeng.lock.dubbo.context.LockContext;
import io.github.gaojindeng.lock.dubbo.listener.RpcLockInvokerListener;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.io.Bytes;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.LoadBalance;
import org.apache.dubbo.rpc.support.RpcUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Activate(order = Integer.MAX_VALUE)
public class LockLoadBalance implements LoadBalance {
    private final LoadBalance loadBalance;
    private final Map<String, ConsistentHashSelector<?>> oldSelectors = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConsistentHashSelector<?>> currentSelectors = new ConcurrentHashMap<>();

    public LockLoadBalance(LoadBalance loadBalance) {
        this.loadBalance = loadBalance;
    }

    @Override
    public <T> Invoker<T> select(List<Invoker<T>> invokers, URL url, Invocation invocation) throws RpcException {
        String lockKey = LockContext.getLockKey();
        if (lockKey == null || lockKey.isEmpty()) {
            return loadBalance.select(invokers, url, invocation);
        }
        String methodName = RpcUtils.getMethodName(invocation);
        String key = invokers.get(0).getUrl().getServiceKey() + "." + methodName;
        int invokersHashCode = invokers.hashCode();
        ConsistentHashSelector<T> selector = (ConsistentHashSelector<T>) currentSelectors.get(key);
        if (selector == null) {
            currentSelectors.put(key, new ConsistentHashSelector<T>(invokers, methodName, invokersHashCode));
        } else if (selector.identityHashCode != invokersHashCode) {
            synchronized (this) {
                selector = (ConsistentHashSelector<T>) currentSelectors.get(key);
                if (selector.identityHashCode != invokersHashCode) {
                    oldSelectors.put(key, currentSelectors.remove(key));
                    currentSelectors.put(key, new ConsistentHashSelector<T>(invokers, methodName, invokersHashCode));
                }
            }
        }
        return handleSelector(lockKey, key);
    }

    private <T> Invoker<T> handleSelector(String lockKey, String key) {
        ConsistentHashSelector<T> selector = (ConsistentHashSelector<T>) currentSelectors.get(key);
        ConsistentHashSelector<T> oldSelector = (ConsistentHashSelector<T>) oldSelectors.get(key);
        InnerInvoker<T> currentInvoker = selector.select(lockKey);

        //如果旧的选择器不存在，直接返回
        if (oldSelector == null) {
            return getInvoker(currentInvoker);
        }

        //如果旧的和新的一致，则返回
        InnerInvoker<T> oldInvoker = oldSelector.select(lockKey);
        if (oldInvoker.getInvokerKey().equals(currentInvoker.getInvokerKey())) {
            return getInvoker(currentInvoker);
        }

        long currentTimeMillis = System.currentTimeMillis();
        Long activeTime = currentInvoker.getActiveTime();

        //如果新的是首次访问的话，则路由旧的invoker
        if (activeTime == null) {
            InnerInvoker<T> tInnerInvoker = selector.get(oldInvoker.getInvokerKey());
            //存在老的，则路由到老的，且设置新的活跃时间
            if (tInnerInvoker != null) {
                currentInvoker.setActiveTime(currentTimeMillis + currentInvoker.getLockDegradedMillisecond());
                return tInnerInvoker.getInvoker();
            }

            //不存在老的，则路由到新的，说明老的已经下线了
            currentInvoker.setActiveTime(currentTimeMillis);
            return currentInvoker.getInvoker();
        }

        //新的还没到活跃时间，则继续路由旧的invoker
        if (activeTime > currentTimeMillis) {
            LockContext.setRedisLock();
            InnerInvoker<T> tInnerInvoker = selector.get(oldInvoker.getInvokerKey());
            return (tInnerInvoker != null) ? tInnerInvoker.getInvoker() : currentInvoker.getInvoker();
        }

        // 在锁降级时间内，继续续约 Redis 锁
        if (currentInvoker.getLockDegradedTime() > currentTimeMillis) {
            LockContext.setRedisLock();
        }

        return currentInvoker.getInvoker();
    }

    public <T> Invoker<T> getInvoker(InnerInvoker<T> invoker) {
        long currentTimeMillis = System.currentTimeMillis();

        // 未设置访问时间，则设置分布式锁标识并更新活跃时间
        if (invoker.getActiveTime() == null) {
            invoker.setActiveTime(currentTimeMillis);
        } else if (currentTimeMillis <= invoker.getLockDegradedTime()) {
            // 在锁降级时间内，续约 Redis 锁
            LockContext.setRedisLock();
        }
        return invoker.getInvoker();
    }

    private static final class ConsistentHashSelector<T> {

        private final TreeMap<Long, InnerInvoker<T>> virtualInvokers;

        private final Map<String, InnerInvoker<T>> map = new HashMap<>();

        private final int identityHashCode;

        ConsistentHashSelector(List<Invoker<T>> invokers, String methodName, int identityHashCode) {
            this.virtualInvokers = new TreeMap<>();
            this.identityHashCode = identityHashCode;
            URL url = invokers.get(0).getUrl();
            int replicaNumber = url.getMethodParameter(methodName, LockConstants.HASH_NODES, 160);
            for (Invoker<T> invoker : invokers) {
                String invokerKey = getInvokerKey(invoker);
                InnerInvoker<T> innerInvoker = new InnerInvoker<>(invokerKey, methodName, invoker, RpcLockInvokerListener.FIRST_REFRESH_TIME_MAP.get(invokerKey));
                map.put(invokerKey, innerInvoker);
                for (int i = 0; i < replicaNumber / 4; i++) {
                    byte[] digest = Bytes.getMD5(invokerKey + i);
                    for (int h = 0; h < 4; h++) {
                        long m = hash(digest, h);
                        virtualInvokers.put(m, innerInvoker);
                    }
                }
            }
        }

        public InnerInvoker<T> get(String invokerKey) {
            return map.get(invokerKey);
        }

        public InnerInvoker<T> select(String lockKey) {
            byte[] digest = Bytes.getMD5(lockKey);
            return selectForKey(hash(digest, 0));
        }


        private InnerInvoker<T> selectForKey(long hash) {
            Map.Entry<Long, InnerInvoker<T>> entry = virtualInvokers.ceilingEntry(hash);
            if (entry == null) {
                entry = virtualInvokers.firstEntry();
            }
            return entry.getValue();
        }

        private long hash(byte[] digest, int number) {
            return (((long) (digest[3 + number * 4] & 0xFF) << 24)
                    | ((long) (digest[2 + number * 4] & 0xFF) << 16)
                    | ((long) (digest[1 + number * 4] & 0xFF) << 8)
                    | (digest[number * 4] & 0xFF))
                    & 0xFFFFFFFFL;
        }
    }

    public static <T> String getInvokerKey(Invoker<T> invoker) {
        String address = invoker.getUrl().getAddress();
        int port = invoker.getUrl().getPort();
        return invoker.toString();
    }
}