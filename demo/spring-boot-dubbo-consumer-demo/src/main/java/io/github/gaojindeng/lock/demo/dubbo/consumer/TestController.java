package io.github.gaojindeng.lock.demo.dubbo.consumer;

import io.github.gaojindeng.lock.demo.dubbo.interfaces.ConsumerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Random;

@RestController
public class TestController {

    @Resource
    private ConsumerService consumerService;

    public final String key = "test";

    public volatile boolean open = false;

    private Random random = new Random();


    @GetMapping("/start")
    public String start() {
        open = true;
        return "success";
    }

    @GetMapping("/stop")
    public String stop() {
        open = false;
        return "success";
    }

    @GetMapping("/test")
    public String test() {
        consumerService.sayHello(key);
        return "success";
    }

    // 每隔一分钟执行一次任务
    @Scheduled(fixedRate = 1000)
    public void myTask() {
        if (!open) {
            return;
        }
        for (int i = 0; i < 15; i++) {
            new Thread(() -> {
                consumerService.sayHello(key + random.nextInt(3));
            }).start();
        }
    }
}
