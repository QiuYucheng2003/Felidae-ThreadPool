package Mytest;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

// IL存在误报！！！还是指针分析的问题；
//这个测试用例中，有shutdown，没有shutdown
public class ILTest_ThreadPool {
    public static void main(String[] args) {
//        构建线程池对象，使用 execute() 提交 Runnable，submit() 提交 Callable
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                5,
                10,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(100),
                new ThreadPoolExecutor.AbortPolicy());

        threadPoolExecutor.execute(()->{
            // ILChecker会检测到这种情况：
            while (!threadPoolExecutor.isShutdown()) {  // 触发检测
                System.out.println("Running..,");
            }
        });

//        threadPoolExecutor.shutdown();
    }
}

