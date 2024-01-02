package io.github.gaojindeng.lock.demo.dubbo.provider;

import io.github.gaojindeng.lock.demo.dubbo.provider.dao.entity.LockRecord;
import io.github.gaojindeng.lock.demo.dubbo.provider.dao.mapper.LockRecordMapper;
import org.junit.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
public class TestController {

    @Resource
    private LockRecordMapper lockRecordMapper;

    @GetMapping("/test")
    public void test(@RequestParam("value") String value) {
        List<LockRecord> test0 = lockRecordMapper.getByKey(value);
        for (int i = 0; i < test0.size() - 1; i++) {
            LockRecord lockRecord = test0.get(i);
            LockRecord lockRecord1 = test0.get(++i);
            if (lockRecord.getServerName().substring(lockRecord.getServerName().indexOf("|")).equals(lockRecord1.getServerName().substring(lockRecord1.getServerName().indexOf("|")))
                    && lockRecord.getThreadName().equals(lockRecord1.getThreadName())
                    && lockRecord.getRemark().startsWith("getLock") && lockRecord1.getRemark().startsWith("unLock")) {
            } else {
                System.out.println(lockRecord.getId());
            }
        }
        System.out.println("success");
    }
}
