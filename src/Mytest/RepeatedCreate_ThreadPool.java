package Mytest;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RepeatedCreate_ThreadPool {
    public static void main(String[] args) {

        ThreadPoolUtils threadPoolUtils = new ThreadPoolUtils();
        ThreadPoolExecutor myPool = threadPoolUtils.createMyPool();
        myPool.execute(()->{
            System.out.println("mypool is running");
        });
        myPool.shutdown();
        ThreadPoolExecutor myPool1 = threadPoolUtils.createMyPool();
        myPool1.execute(()->{
            System.out.println("mypool1 is running");
        });
        myPool1.shutdown();


//     循环误用模式检测！
//        int i=0;
//        while (i<10){
//            ThreadPoolExecutor myPool = ThreadPoolUtils.createMyPool();
//            myPool.execute(()->{
//                System.out.println("running...");
//            });
//            i++;
//            myPool.shutdown();
//
//        }

    }

}

class ThreadPoolUtils {
    // 这是一个创建点 (InitPoint)
    public static ThreadPoolExecutor createMyPool() {
        return new ThreadPoolExecutor(
                5,
                10,  // 直接使用 Integer.MAX_VALUE
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(100),
                new ThreadPoolExecutor.AbortPolicy());
    }
}