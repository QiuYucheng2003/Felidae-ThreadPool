package Mytest;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// 创建一个自定义的 ThreadFactory 来设置线程名称
public class SetName_ThreadPool2 {
    public static void main(String[] args) {

        // 创建一个自定义的线程工厂，设置线程名称
        ThreadFactory namedThreadFactory = new ThreadFactory() {
            private final AtomicInteger threadCount = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("Qyc-thread-" + threadCount.getAndIncrement());
                return thread;
            }
        };

        // 使用自定义线程工厂创建线程池
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                5,
                10,  // 最大线程数
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(100),
                namedThreadFactory,  // 设置线程工厂
                new ThreadPoolExecutor.AbortPolicy()
        );

        // 提交任务
        threadPoolExecutor.execute(() -> {
            try {
                Thread.currentThread().setName("qyc-thread1");
                Thread.sleep(1000);
                System.out.println("This is SetNameThreadPool");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        threadPoolExecutor.shutdown();
    }
}
