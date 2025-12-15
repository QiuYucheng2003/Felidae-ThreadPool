package Mytest;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Excp_ThreadPool {
    public static void main(String[] args) {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                5,
                10,  // 直接使用 Integer.MAX_VALUE
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(100),
                new ThreadPoolExecutor.AbortPolicy());

        threadPoolExecutor.submit(()->{
                int result= 10/0;
//                异常未处理
//                System.out.println("This is Excp_ThreadPool");
        });

        threadPoolExecutor.shutdown();
    }
}
