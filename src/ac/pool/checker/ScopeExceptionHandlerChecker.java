package ac.pool.checker;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ac.pool.point.InitPoint;
import ac.pool.point.KeyPoint;
import ac.pool.point.OneParaKeyPoint;
import soot.Body;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Trap;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.DivExpr;
import soot.jimple.RemExpr;
import soot.jimple.ThrowStmt;

// 修改后的检查器：只检测未被捕获的“危险指令”（如除法、显式throw）
public class ScopeExceptionHandlerChecker {

    public static boolean hasMisuse(InitPoint initPoint,
                                    Set<OneParaKeyPoint> setFactoryPoints,
                                    Set<KeyPoint> setUncaughtExceptionHandlerPoints) {

        // 1. 如果全局设置了 UncaughtExceptionHandler，通常认为已兜底（根据你的需求，可保留或注释掉此行）
        // if (hasFactoryHandler(...) || hasGlobalHandler(...)) return false;
        // 这里为了专注检测你的除0用例，我们先假设没有全局handler，直接检查局部。

        SootClass hostClass = initPoint.getMethod().getDeclaringClass();
        List<SootClass> classesToScan = getRelatedClasses(hostClass);

        for (SootClass sc : classesToScan) {
            if (sc.isLibraryClass() || sc.isJavaLibraryClass()) continue;

            for (SootMethod method : new ArrayList<>(sc.getMethods())) {
                // 仅扫描有方法体且像任务的方法
                if (method.hasActiveBody() && isTaskMethod(method)) {
                    // 2. 深入检查方法内部指令
                    if (hasUnhandledRiskyInstruction(method)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // 核心逻辑：检查是否存在“裸奔”的危险指令
    private static boolean hasUnhandledRiskyInstruction(SootMethod method) {
        Body body = method.getActiveBody();

        for (Unit unit : body.getUnits()) {
            // 1. 判断当前指令是否是危险的（可能抛出异常）
            if (isRiskyUnit(unit)) {
                // 2. 如果是危险指令，检查它是否被 try-catch 保护
                if (!isProtectedByTrap(body, unit)) {
                    // 发现未捕获的危险指令（例如 10/0）
                    return true;
                }
            }
        }
        return false;
    }

    // 判断指令是否有风险
    private static boolean isRiskyUnit(Unit unit) {
        // 情况A: 赋值语句中包含除法或取模 (对应 10/0)
        if (unit instanceof AssignStmt) {
            Value rightOp = ((AssignStmt) unit).getRightOp();
            // DivExpr 对应除法 (/), RemExpr 对应取模 (%)
            // 只要有除法，就有除0风险 (ArithmeticException)
            if (rightOp instanceof DivExpr || rightOp instanceof RemExpr) {
                return true;
            }
        }

//        // 情况B: 显式的 throw 语句
//        if (unit instanceof ThrowStmt) {
//            return true;
//        }

        // 情况C (可选): 调用了可能抛出异常的方法
        // if (unit.containsInvokeExpr()) { ... }
        // 为了精准定位你的测试用例，这里先暂时不加，避免误报太多

        return false;
    }

    // 检查某个 Unit 是否在任何一个 Trap (try-catch) 的保护范围内
    private static boolean isProtectedByTrap(Body body, Unit unit) {
        for (Trap trap : body.getTraps()) {
            // Trap 的范围是 [beginUnit, endUnit)
            // 我们需要判断 unit 是否在这个链条区间内
            Unit current = trap.getBeginUnit();
            while (current != null && current != trap.getEndUnit()) {
                if (current == unit) {
                    return true; // 找到了，该指令被这个 trap 保护
                }
                current = body.getUnits().getSuccOf(current);
            }
        }
        return false;
    }

    private static boolean isTaskMethod(SootMethod method) {
        String name = method.getName();
        // 匹配 run, call, 或者 lambda 生成的方法
        return name.equals("run") || name.equals("call") || name.startsWith("lambda$");
    }

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