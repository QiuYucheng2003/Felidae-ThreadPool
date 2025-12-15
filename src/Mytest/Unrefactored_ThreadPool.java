package Mytest;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
//分析问题：
//submitPoint.getParaLocalPossiableTypes()返回的是空的！

//Soot 的 指针分析（Pointer Analysis） 或者 变量类型传播（VTA）。 是指针分析的问题！；
// 日志显示这个列表是空的，这意味着分析框架没能计算出传递给 submit 方法的参数到底指向哪个具体的类。
public class Unrefactored_ThreadPool {
    public static void main(String[] args) {
        ThreadLocal<Integer> currentUserId = new ThreadLocal<>();
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                5,
                10,  // 直接使用 Integer.MAX_VALUE
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(100),
                new ThreadPoolExecutor.AbortPolicy());

        threadPoolExecutor.submit(()->{
            currentUserId.set(1);
            System.out.println("The number has been set is "+currentUserId.get());
//            currentUserId.remove();
        });

        threadPoolExecutor.shutdown();
    }
}
