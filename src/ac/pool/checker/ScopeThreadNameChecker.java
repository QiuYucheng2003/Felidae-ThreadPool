package ac.pool.checker;

import java.util.ArrayList;
import java.util.List;
import ac.pool.point.InitPoint;
import soot.Body;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;

// 线程命名检查器 (Scope-Based)
public class ScopeThreadNameChecker {

    // 线程.setName() 方法的签名
    private static final String THREAD_SET_NAME_SIG = "<java.lang.Thread: void setName(java.lang.String)>";
    // ThreadFactory.newThread() 方法的子签名
    private static final String THREAD_FACTORY_NEW_THREAD_SUBSIG = "java.lang.Thread newThread(java.lang.Runnable)";

    public static boolean hasMisuse(InitPoint initPoint) {
        SootClass hostClass = initPoint.getMethod().getDeclaringClass();
        List<SootClass> classesToScan = getRelatedClasses(hostClass);

        // 1. 检查是否存在命名线程工厂（ThreadFactory）
        if (isNamingThreadFactoryDefined(classesToScan)) {
            return false; // 找到了命名工厂，认为命名机制存在
        }

        // 2. 检查是否存在任务内部的命名调用 (Thread.currentThread().setName())
        if (isNamingDoneInTask(classesToScan)) {
            return false; // 找到了任务内的命名调用，认为命名机制存在
        }

        // 如果两种命名机制都不存在，则判定为 UNT 误用
        return true;
    }

    // 检查作用域内是否有实现 ThreadFactory 且在 newThread 中调用了 setName 的类
    private static boolean isNamingThreadFactoryDefined(List<SootClass> classesToScan) {
        SootClass threadFactoryClass = Scene.v().getSootClassUnsafe("java.util.concurrent.ThreadFactory");
        if (threadFactoryClass == null) return false;

        for (SootClass sc : classesToScan) {
            // 检查当前类是否实现了 ThreadFactory 接口
            if (sc.implementsInterface(String.valueOf(threadFactoryClass))) {
                SootMethod newThreadMethod = sc.getMethodUnsafe(THREAD_FACTORY_NEW_THREAD_SUBSIG);

                if (newThreadMethod != null && newThreadMethod.hasActiveBody()) {
                    // 检查 newThread 方法体中是否有 setName 调用
                    if (methodContainsSetName(newThreadMethod)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // 检查作用域内的任务方法（run/call/lambda）中是否有 setName 调用
    private static boolean isNamingDoneInTask(List<SootClass> classesToScan) {
        for (SootClass sc : classesToScan) {
            for (SootMethod method : new ArrayList<>(sc.getMethods())) {
                String name = method.getName();
                // 检查任务型方法 (run/call/lambda$)
                if ((name.equals("run") || name.equals("call") || name.startsWith("lambda$"))
                        && method.hasActiveBody()) {

                    if (methodContainsSetName(method)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // 检查方法体内是否包含 Thread.setName() 的调用
    private static boolean methodContainsSetName(SootMethod method) {
        if (!method.hasActiveBody()) return false;
        Body body = method.getActiveBody();
        for (Unit unit : body.getUnits()) {
            if (unit instanceof Stmt) {
                Stmt stmt = (Stmt) unit;
                if (stmt.containsInvokeExpr()) {
                    if (stmt.getInvokeExpr().getMethod().getSignature().equals(THREAD_SET_NAME_SIG)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // 收集宿主类及其所有的内部类（包括匿名内部类和Lambda生成的类）
    private static List<SootClass> getRelatedClasses(SootClass hostClass) {
        List<SootClass> list = new ArrayList<>();
        list.add(hostClass);
        String hostName = hostClass.getName();

        for (SootClass sc : Scene.v().getApplicationClasses()) {
            if (sc.getName().startsWith(hostName + "$")) {
                list.add(sc);
            }
        }
        return list;
    }
}