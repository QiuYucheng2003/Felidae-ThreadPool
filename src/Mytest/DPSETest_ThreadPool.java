package Mytest;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DPSETest_ThreadPool {
    public static void main(String[] args) {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                5,
                10,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(100),
                new ThreadPoolExecutor.AbortPolicy());

        threadPoolExecutor.execute(() -> {
            while(!threadPoolExecutor.isShutdown()) {
                try {
                    Thread.sleep(1000);
                    System.out.println("This is threadPoolExecutor...");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
//        主线程做
        System.out.println("Main Thread is to SetRejectedExecutionHandler...");
        threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
//        threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());

        threadPoolExecutor.shutdown();
    }
}
