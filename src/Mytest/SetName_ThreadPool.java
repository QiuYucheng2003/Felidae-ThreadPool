package Mytest;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

//静态分析时机问题：您的检测器进行的是静态代码分析，它只能分析代码的结构和调用关系
//无法知道运行时execute()方法内部的setName()调用。
//不要再线程运行的时候设置 execute
public class SetName_ThreadPool {
    public static void main(String[] args) {

        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                5,
                10,  // 直接使用 Integer.MAX_VALUE
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(100),
                new ThreadPoolExecutor.AbortPolicy());
        threadPoolExecutor.execute(() -> {
                try {
                    Thread.sleep(1000);
                    System.out.println("This is SetNameThreadPool");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

        });
        threadPoolExecutor.shutdown();
    }
}
