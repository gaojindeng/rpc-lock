# rpc-lock
最强性能分布式锁实现方案
### 说明
源码地址：[https://github.com/gaojindeng/rpc-lock](https://github.com/gaojindeng/rpc-lock)

在RPC-Lock中，利用Dubbo进行RPC调用时，根据特定的key进行哈希路由，将请求路由到同一个节点，并在该节点内部使用ReentrantLock来实现分布式锁的功能。

在服务提供者节点扩容时，可能会导致相同key路由到多个节点。为了应对这种情况，我设计了一种机制：在扩容时会对锁进行升级，从最初的ReentrantLock本地锁升级到Redis的分布式锁。升级的具体步骤是先获取本地ReentrantLock锁，然后再获取Redis分布式锁，从而确保在锁升级过程中保持资源访问的安全性。

为了避免各个消费节点在服务提供者新节点上线时可能出现的并发问题，扩容阶段的路由策略并不会立即将请求路由到新节点，而是继续将请求路由到旧节点，并同时升级锁的级别为ReentrantLock与Redis分布式锁的组合。只有当经过一定时间的阀值后，才会将请求路由到新节点，这样确保了对服务提供者新节点的监听过程不会引发并发问题。

这种设计保障了在RPC-Lock中服务提供者扩容时的安全性，通过在扩容阶段暂时维持旧节点的路由与锁升级方式，来避免潜在的并发问题，最终实现了对新节点的平稳监听和过渡。
##### 扩容时路由切换示意图：

1. 初始时，消费者针对同一个key消息都是路由到01节点使用轻量级本地锁

![image.png](https://cdn.nlark.com/yuque/0/2024/png/324847/1704208834036-38f35db1-a7ff-499e-814e-7a4159c783e7.png#averageHue=%23434555&clientId=uc0995387-4d1e-4&from=paste&height=127&id=u31be4e32&originHeight=127&originWidth=588&originalType=binary&ratio=1&rotation=0&showTitle=false&size=9170&status=done&style=none&taskId=u1ec297c8-9311-46ca-b935-4e61dcdf771&title=&width=588)

2. 生产者新增一个节点02，hash路由虽然路由到新的节点，但是新节点还处于一个保护期，进行锁升级继续路由到旧的节点

![image.png](https://cdn.nlark.com/yuque/0/2024/png/324847/1704209014271-d9e973ff-1918-413a-a586-c15dbf3f4ded.png#averageHue=%23444656&clientId=uc0995387-4d1e-4&from=paste&height=151&id=u69067aa6&originHeight=151&originWidth=628&originalType=binary&ratio=1&rotation=0&showTitle=false&size=13248&status=done&style=none&taskId=uf1012467-b426-4e0d-93b8-418e05f090c&title=&width=628)

3. 旧节点的保护期结束后，则使用重量级锁路由到新的节点

![image.png](https://cdn.nlark.com/yuque/0/2024/png/324847/1704209248609-3fd09ed5-858c-4088-9ee9-59d6cad8a43b.png#averageHue=%23454757&clientId=uc0995387-4d1e-4&from=paste&height=139&id=u9b7e536c&originHeight=139&originWidth=624&originalType=binary&ratio=1&rotation=0&showTitle=false&size=12910&status=done&style=none&taskId=u585b1949-ccfc-4426-b3dc-6a0b4b21ccf&title=&width=624)

4. 新节点的保护期结束后，则进行锁降级为轻量级锁进行路由到新节点

![image.png](https://cdn.nlark.com/yuque/0/2024/png/324847/1704209297415-6aaeb042-0e60-4f7f-aaf1-4ef1561717a2.png#averageHue=%23464857&clientId=uc0995387-4d1e-4&from=paste&height=130&id=u72f9aebb&originHeight=130&originWidth=579&originalType=binary&ratio=1&rotation=0&showTitle=false&size=12081&status=done&style=none&taskId=u01f3746e-0a7d-4499-a656-f350e32513c&title=&width=579)

### 测试demo
消费者：
```java
@DubboReference
private ProviderService providerService;

public String sayHello(String name) {
    //添加lockKey
    LockContext.setLockKey(name);
    return providerService.sayHello(name);
}
```
生产者：
```java
@Override
public String sayHello(String name) {
    Lock lock = LockContext.getLock();

    //加锁
    lock.lock();
    try {
        //业务逻辑处理
        System.out.println("Hello " + name + ", request from consumer: " + RpcContext.getContext().getRemoteAddress());
        return "Hello " + name;
    } finally {
        //释放锁
        lock.unlock();
    }

}
```
### 源码实现
代码实现主要有三个难点：

1. 如何刷新和保存provider节点信息，要确保在一定时间内，所有的消费者拿到的远程服务列表是一致的。
2. 路由选择，要维护新旧两套远程服务列表，且需要告诉服务提供者要不要进行锁升级
3. 服务提供者provider收到请求后，如何针对同一个key创建同一个RpcLock对象，且没有其他线程在使用时如何销毁

我这里只讲下第三个点的实现逻辑，其他的可以去看源码。
```java
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
```
使用读锁去添加线程占用；移除占用时如果发现需要删除rpcLock对象则会使用写锁
```java
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
```
### 风险点

- 由单个节点扩容到多个节点会有问题，dubbo源码写死了只有1个服务提供者时，不会执行自定义的balance的逻辑，这个只能去修改源码。
- 如果不同的消费者监听到服务提供者上线/下线时间长时间都不一致时，就会有并发问题。
- 新节点保护期的时间不好去设定，控制的不好会有并发问题，特别是所有服务雪崩时，dubbo线程被占满。







