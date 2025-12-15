package Mytest;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CoreMax_ThreadPool {
    public static void main(String[] args) {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                Integer.MAX_VALUE,
                10,  // 直接使用 Integer.MAX_VALUE
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(100),
                new ThreadPoolExecutor.AbortPolicy());

        threadPoolExecutor.execute(() -> {
            while(!threadPoolExecutor.isShutdown()) {
                try {
                    Thread.sleep(1000);
                    System.out.println("This is CoreMax_ThreadPool...");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        //        主线程做
        System.out.println("Main Thread is to Set_Max_Value...");
//        threadPoolExecutor.setMaximumPoolSize(Integer.MAX_VALUE);

        try {
            Thread.sleep(3000);
            threadPoolExecutor.shutdown();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }
}
