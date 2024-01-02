/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.gaojindeng.lock.demo.dubbo.provider;


import io.github.gaojindeng.lock.demo.dubbo.interfaces.ProviderService;
import io.github.gaojindeng.lock.demo.dubbo.provider.dao.entity.LockRecord;
import io.github.gaojindeng.lock.demo.dubbo.provider.dao.mapper.LockRecordMapper;
import io.github.gaojindeng.lock.dubbo.context.LockContext;
import io.github.gaojindeng.lock.dubbo.lock.RpcLock;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.rpc.RpcContext;
import org.springframework.boot.autoconfigure.web.ServerProperties;

import javax.annotation.Resource;
import java.util.Random;
import java.util.concurrent.locks.Lock;

@DubboService
public class ProviderServiceImpl implements ProviderService {

    @Resource
    private LockRecordMapper lockRecordMapper;

    @Resource
    private ServerProperties serverProperties;


    @Override
    public String sayHello(String name) {
        Lock lock = LockContext.getLock();
        LockRecord entity = new LockRecord();
        lock.lock();
        try {
            entity.setKey(LockContext.getLockKey());
            entity.setServerName(RpcContext.getClientAttachment().getRemoteAddress().getHostString() + " " + RpcContext.getClientAttachment().getRemotePort());
            entity.setServerName(entity.getServerName()+"|||"+serverProperties.getPort());
            entity.setLockType(LockContext.isRedisLock() + "");
            entity.setThreadName(Thread.currentThread().getName());
            entity.setRemark("getLock"+lock.toString());
            lockRecordMapper.insert(entity);
            Random random = new Random();
            try {
                Thread.sleep(random.nextInt(10));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Hello " + name + ", request from consumer: " + RpcContext.getContext().getRemoteAddress());
            return "Hello " + name;
        } finally {
            entity.setRemark("unLock");
            lockRecordMapper.insert(entity);
            lock.unlock();

        }

    }


}
