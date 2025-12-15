package ac.pool.checker;

import ac.pool.point.KeyPoint;
import soot.*;
import soot.jimple.*;
// 修正 Import：Loop 类位于 annotation.logic 包中
import soot.jimple.toolkits.annotation.logic.Loop;
import soot.toolkits.graph.LoopNestTree;

import java.util.HashSet;
import java.util.Set;

/**
 * ScopeILChecker (Final Corrected)
 * 修正了 Loop 类的引用错误。
 */
public class ScopeILChecker {

    /**
     * 检测是否存在 IL (Infinite Loop) 误用
     * @return true = 存在误用 (Bug); false = 安全 (Safe)
     */
    public static boolean isShutDownMisuse(Set<KeyPoint> globalShutdowns, KeyPoint isShutDownPoint) {

        // Step 1: 必须先确定 isShutdown 真的在循环里
        if (!isInLoop(isShutDownPoint)) {
            return false; // 不在循环里，直接跳过
        }

        // Step 2: 准备匹配信息 (Name & Class)
        // 回溯 Lambda 中的变量来源 ($r1 -> val$tpe)
        String checkVarName = getTraceableVariableName(isShutDownPoint);
        if (checkVarName == null) return true; // 无法追踪，保守报错

        SootClass checkClass = isShutDownPoint.getMethod().getDeclaringClass();

        // Step 3: 在全局 shutdown 集合中寻找“解药”
        for (KeyPoint shutDownPoint : globalShutdowns) {
            SootClass shutdownClass = shutDownPoint.getMethod().getDeclaringClass();

            // A. 作用域亲和性检查 (Inner <-> Outer)
            if (!isScopeRelated(checkClass, shutdownClass)) {
                continue;
            }

            // B. 变量名模糊匹配
            String closeVarName = getTraceableVariableName(shutDownPoint);

            // 只要名字存在包含关系 (val$tpe vs tpe)，就认为是同一个对象
            if (isNameFuzzyMatch(checkVarName, closeVarName)) {
                return false; // 找到了匹配的关闭操作 -> 安全
            }
        }

        // Step 4: 循环结束仍未找到匹配 -> 确认为误用
        return true;
    }

    // =========================================================================
    // 关键修复：使用正确的 Loop 类路径进行循环检测
    // =========================================================================

    private static boolean isInLoop(KeyPoint point) {
        SootMethod method = point.getMethod();
        if (!method.hasActiveBody()) return false;

        Body body = method.getActiveBody();
        Stmt targetStmt = point.getStmt();

        // 直接通过 Body 构建 LoopNestTree，Soot 会自动处理控制流图
        LoopNestTree loopNestTree = new LoopNestTree(body);

        // 检查 targetStmt 是否属于任何一个循环
        for (Loop loop : loopNestTree) {
            // getLoopStatements 返回循环内的所有语句
            if (loop.getLoopStatements().contains(targetStmt)) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // 变量回溯与匹配逻辑
    // =========================================================================

    private static boolean isScopeRelated(SootClass c1, SootClass c2) {
        if (c1.equals(c2)) return true;
        if (c1.hasOuterClass() && c1.getOuterClass().equals(c2)) return true;
        if (c2.hasOuterClass() && c2.getOuterClass().equals(c1)) return true;
        return false;
    }

    private static boolean isNameFuzzyMatch(String name1, String name2) {
        if (name1 == null || name2 == null) return false;
        String n1 = simplify(name1);
        String n2 = simplify(name2);
        return n1.contains(n2) || n2.contains(n1);
    }

    private static String simplify(String name) {
        return name.replace("val$", "")
                .replace("access$", "")
                .replace("this.", "")
                .toLowerCase().trim();
    }

    private static String getTraceableVariableName(KeyPoint point) {
        Unit unit = point.getStmt();
        SootMethod method = point.getMethod();
        Value baseValue = null;

        if (unit instanceof Stmt) {
            Stmt stmt = (Stmt) unit;
            if (stmt.containsInvokeExpr()) {
                if (stmt.getInvokeExpr() instanceof InstanceInvokeExpr) {
                    baseValue = ((InstanceInvokeExpr) stmt.getInvokeExpr()).getBase();
                }
            }
        }

        if (baseValue == null) return null;

        if (baseValue instanceof Local) {
            return traceOrigin(method, (Local) baseValue, new HashSet<>());
        }
        return baseValue.toString();
    }

    private static String traceOrigin(SootMethod method, Local local, Set<Local> visited) {
        if (!method.hasActiveBody() || visited.contains(local)) return local.toString();
        visited.add(local);

        for (Unit u : method.getActiveBody().getUnits()) {
            if (u instanceof DefinitionStmt) {
                DefinitionStmt def = (DefinitionStmt) u;
                if (def.getLeftOp().equals(local)) {
                    Value right = def.getRightOp();
                    if (right instanceof FieldRef) {
                        return ((FieldRef) right).getField().getName();
                    }
                    if (right instanceof Local) {
                        return traceOrigin(method, (Local) right, visited);
                    }
                }
            }
        }
        return local.toString();
    }
}