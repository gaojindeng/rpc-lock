package io.github.gaojindeng.lock.dubbo.listener;

import io.github.gaojindeng.lock.dubbo.loadbalance.LockLoadBalance;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.InvokerListener;

import java.util.HashMap;
import java.util.Map;

@Activate(group = CommonConstants.PROVIDER)
public class RpcLockInvokerListener implements InvokerListener {
    public static final Map<String, Long> FIRST_REFRESH_TIME_MAP = new HashMap<>();

    @Override
    public void referred(Invoker<?> invoker) {
        String invokerKey = LockLoadBalance.getInvokerKey(invoker);
        FIRST_REFRESH_TIME_MAP.put(invokerKey, System.currentTimeMillis());
    }

    @Override
    public void destroyed(Invoker<?> invoker) {
        String invokerKey = LockLoadBalance.getInvokerKey(invoker);
        FIRST_REFRESH_TIME_MAP.remove(invokerKey);
    }
}