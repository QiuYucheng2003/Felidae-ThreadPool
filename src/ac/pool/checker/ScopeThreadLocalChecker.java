package ac.pool.checker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ac.pool.point.InitPoint;
import soot.Body;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.NullConstant;
import soot.jimple.Stmt;

// 新思路：基于作用域的ThreadLocal检查器
// 逻辑：检查定义了线程池的类（及其内部类/Lambda）中，是否存在只set不remove的情况
public class ScopeThreadLocalChecker {

    // [新增] 白名单包/类前缀
    // 这些框架通常使用 try-finally 或拦截器模式跨方法管理 ThreadLocal，不适用单方法检查
    private static final List<String> WHITELIST_PREFIXES = Arrays.asList(
            "org.eclipse.jetty.",        // Jetty 服务器核心
            "org.springframework.",      // Spring 框架
            "org.apache.tomcat.",        // Tomcat 容器
            "io.netty.",                 // Netty 网络框架
            "java.util.logging."         // Java 日志
    );

    public static boolean hasThreadLocalMisuse(InitPoint point) {
        // 1. 获取初始化线程池的方法所属的类
        SootClass hostClass = point.getMethod().getDeclaringClass();

        // [新增] 0. 白名单检查：如果宿主类属于已知框架，直接跳过
        if (isWhitelisted(hostClass)) {
            return false;
        }

        // 2. 收集需要扫描的所有相关类（宿主类 + 内部类/Lambda类）
        List<SootClass> classesToScan = getRelatedClasses(hostClass);

        // 3. 遍历这些类中的所有方法
        for (SootClass sc : classesToScan) {
            // 避免扫描库类，只扫应用类
            if (sc.isLibraryClass() || sc.isJavaLibraryClass()) continue;

            // [新增] 再次确保扫描的内部类也不在白名单中
            if (isWhitelisted(sc)) continue;

            for (SootMethod method : new ArrayList<>(sc.getMethods())) {
                // 如果方法有方法体，进行检查
                if (method.hasActiveBody()) {
                    // [新增] 跳过构造函数和静态初始化块
                    // ThreadLocal 通常在业务方法中 set/remove，在构造中 set 通常是初始化行为
                    if (method.isConstructor() || method.isStaticInitializer()) {
                        continue;
                    }

                    if (hasSetButNoRemove(method)) {
                        // 只要发现一个方法有问题，就报告
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // [新增] 检查类是否在白名单中
    private static boolean isWhitelisted(SootClass sc) {
        String name = sc.getName();
        for (String prefix : WHITELIST_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    // 收集宿主类及其所有的内部类（包括匿名内部类和Lambda生成的类）
    private static List<SootClass> getRelatedClasses(SootClass hostClass) {
        List<SootClass> list = new ArrayList<>();
        list.add(hostClass);

        String hostName = hostClass.getName();

        // 遍历所有应用类，寻找名字包含 hostName$ 的类
        for (SootClass sc : Scene.v().getApplicationClasses()) {
            if (sc.getName().startsWith(hostName + "$")) {
                list.add(sc);
            }
        }
        return list;
    }

    // 检查一个方法体内是否 set 了 ThreadLocal 却没 remove
    private static boolean hasSetButNoRemove(SootMethod method) {
        boolean hasSet = false;
        boolean hasRemove = false;

        // [新增] 强制获取 Body，增加健壮性
        if (!method.hasActiveBody()) {
            try {
                method.retrieveActiveBody();
            } catch (Exception e) {
                return false;
            }
        }

        Body body = method.getActiveBody();
        for (Unit unit : body.getUnits()) {
            if (unit instanceof Stmt) {
                Stmt stmt = (Stmt) unit;
                if (stmt.containsInvokeExpr()) {
                    SootMethod invokedMethod = stmt.getInvokeExpr().getMethod();
                    String methodSig = invokedMethod.getSignature();

                    // 1. 检查 ThreadLocal.set
                    if (methodSig.contains("java.lang.ThreadLocal: void set(java.lang.Object)")) {
                        hasSet = true;

                        // [新增] 检查 set(null) 的情况
                        // set(null) 在语义上等同于 remove，很多代码会用 set(null) 来清理
                        if (stmt.getInvokeExpr() instanceof InstanceInvokeExpr) {
                            Value arg = stmt.getInvokeExpr().getArg(0);
                            if (arg instanceof NullConstant) {
                                hasRemove = true; // 视为已清理
                            }
                        }
                    }

                    // 2. 检查 ThreadLocal.remove
                    if (methodSig.contains("java.lang.ThreadLocal: void remove()")) {
                        hasRemove = true;
                    }
                }
            }
        }

        // 只有 set 没有 remove，判定为误用
        return hasSet && !hasRemove;
    }
}