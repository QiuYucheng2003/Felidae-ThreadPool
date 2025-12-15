package ac.pool.checker;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;

import soot.*;
import soot.jimple.IfStmt;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;
import soot.jimple.IdentityStmt;
import soot.jimple.ParameterRef;
import soot.jimple.ReturnStmt;   // [新增]
import soot.jimple.ReturnVoidStmt;// [新增]
import soot.jimple.ThrowStmt;    // [新增]
import soot.toolkits.scalar.SimpleLocalDefs;
import soot.toolkits.graph.BriefUnitGraph;
import ac.pool.point.KeyPoint;

public class ILChecker {

	// 检查是否存在线程池关闭误用导致的无限循环
	public static boolean isShutDownMisuse(Set<KeyPoint> shutDowns, KeyPoint isShutDownPoint) {

		// 1. [基础检查] 检查是否在循环条件中
		LoopConditionAnalysis analysis = new LoopConditionAnalysis(isShutDownPoint);
		if (!analysis.isShutDownLoopCondition) {
			return false;
		}

		// 2. [新增 - 关键修复] 检查该判断条件是否直接导致了异常抛出或方法返回
		// 针对 SnowflakeIdGenerator：检测到中断后 throw exception，这不是无限循环。
		// 针对 AgentRunner：catch 块中有 return，这也算有效退出。
		if (analysis.leadsToExitOrThrow) {
			return false;
		}

		// 3. 检查是否有匹配的 shutdown 调用
		for (KeyPoint shutDown : shutDowns) {

			// 步骤 A: 简单同方法变量匹配 (新增，解决 AgentRunner 同变量问题)
			if (isSameMethodAndLocal(isShutDownPoint, shutDown)) {
				return false;
			}

			// 步骤 B: 标准别名/指针分析
			if (isShutDownPoint.isAliasCaller(shutDown)) {
				return false;
			}

			// 步骤 C: Lambda 模糊匹配
			if (isLambdaAlias(isShutDownPoint, shutDown)) {
				return false;
			}
		}

		// 确实没找到匹配的关闭操作，判定为误用
		return true;
	}

	/**
	 * [新增] 快速检查两个点是否在同一个方法内，且使用同一个 Local 变量
	 * 这比复杂的指针分析更准、更快，专门解决 AgentRunner 这类局部逻辑
	 */
	private static boolean isSameMethodAndLocal(KeyPoint p1, KeyPoint p2) {
		if (!p1.getMethod().equals(p2.getMethod())) {
			return false;
		}
		Value v1 = p1.getCaller();
		Value v2 = p2.getCaller();
		// 直接比较 Jimple 的 Local 对象引用
		return v1 != null && v2 != null && v1.equals(v2);
	}

	private static boolean isLambdaAlias(KeyPoint lambdaPoint, KeyPoint outerPoint) {
		// ... (保持你原有的代码不变) ...
		SootMethod lambdaMethod = lambdaPoint.getMethod();

		if (!lambdaMethod.getName().contains("lambda") && !lambdaMethod.getName().contains("$")) {
			return false;
		}

		Value outerCaller = outerPoint.getCaller();
		Value lambdaCaller = lambdaPoint.getCaller();

		if (!lambdaMethod.hasActiveBody()) return false;

		for (Unit u : lambdaMethod.getActiveBody().getUnits()) {
			if (u instanceof DefinitionStmt) {
				DefinitionStmt def = (DefinitionStmt) u;
				if (def.getLeftOp().equals(lambdaCaller)) {
					Value rightOp = def.getRightOp();

					if (rightOp instanceof FieldRef) {
						if (outerCaller instanceof Local) {
							String outerName = ((Local) outerCaller).getName();
							String fieldName = ((FieldRef) rightOp).getField().getName();
							if (fieldName.toLowerCase().contains(outerName.toLowerCase())) {
								return true;
							}
						}
					}

					if (rightOp instanceof ParameterRef) {
						Type paramType = rightOp.getType();
						Type outerType = outerCaller.getType();
						if (paramType.toString().equals(outerType.toString())) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	// -----------------------------------------------------------
	// 循环分析类 (增强版)
	// -----------------------------------------------------------
	static class LoopConditionAnalysis {

		boolean isShutDownLoopCondition = false;
		boolean leadsToExitOrThrow = false; // [新增] 标记是否导向退出

		public LoopConditionAnalysis(KeyPoint isShutDown) {
			analyze(isShutDown);
		}

		private void analyze(KeyPoint isShutDownPoint) {
			SootMethod method = isShutDownPoint.getMethod();
			Unit isShutDownUnit = isShutDownPoint.getStmt();

			if (!method.hasActiveBody()) return;
			BriefUnitGraph graph = new BriefUnitGraph(method.getActiveBody());
			SimpleLocalDefs localDefs = new SimpleLocalDefs(graph);

			for (Unit unit : graph) {
				if (unit instanceof IfStmt) {
					IfStmt ifStmt = (IfStmt) unit;
					Value condition = ifStmt.getCondition();

					for (ValueBox box : condition.getUseBoxes()) {
						Value value = box.getValue();
						if (value instanceof Local) {
							Local conditionLocal = (Local) value;
							List<Unit> defs = localDefs.getDefsOfAt(conditionLocal, ifStmt);

							if (defs.contains(isShutDownUnit)) {
								// 1. 确认该变量确实参与了循环条件
								if (isReachable(graph, ifStmt, isShutDownUnit)) {
									isShutDownLoopCondition = true;

									// 2. [新增] 检查这个 IfStmt 的分支是否会导致退出
									if (checkExitPaths(graph, ifStmt)) {
										leadsToExitOrThrow = true;
									}
									return;
								}
							}
						}
					}
				}
			}
		}

		// [新增] 检查 IfStmt 的任意分支是否能在有限步数内到达 Return 或 Throw
		private boolean checkExitPaths(BriefUnitGraph graph, IfStmt ifStmt) {
			// 检查 Target 分支 (跳转分支)
			if (leadsToExit(graph, ifStmt.getTarget())) return true;

			// 检查 Fallthrough 分支 (顺序执行分支)
			// 这里的逻辑是：如果检测到中断/关闭，通常会进入一个特定的分支（可能是target，也可能是fallthrough）
			// 只要这两个分支中有一个能迅速退出方法，我们就认为这是一个安全的检查。
			Unit succ = null;
			for(Unit u : graph.getSuccsOf(ifStmt)) {
				if (u != ifStmt.getTarget()) {
					succ = u;
					break;
				}
			}
			if (succ != null && leadsToExit(graph, succ)) return true;

			return false;
		}

		// [新增] 深度优先搜索寻找退出点 (限制深度以防性能问题)
		private boolean leadsToExit(BriefUnitGraph graph, Unit startUnit) {
			List<Unit> queue = new java.util.LinkedList<>();
			Set<Unit> visited = new HashSet<>();
			queue.add(startUnit);

			int maxDepth = 20; // 限制搜索深度，通常异常抛出或return离if很近
			int depth = 0;

			while (!queue.isEmpty() && depth < maxDepth) {
				int size = queue.size();
				for(int i=0; i<size; i++) {
					Unit current = queue.remove(0);
					if (visited.contains(current)) continue;
					visited.add(current);

					// 关键：检测退出语句
					if (current instanceof ThrowStmt ||
							current instanceof ReturnStmt ||
							current instanceof ReturnVoidStmt) {
						return true;
					}

					queue.addAll(graph.getSuccsOf(current));
				}
				depth++;
			}
			return false;
		}

		private boolean isReachable(BriefUnitGraph graph, Unit startNode, Unit targetNode) {
			List<Unit> queue = new java.util.LinkedList<>();
			Set<Unit> visited = new java.util.HashSet<>();
			queue.addAll(graph.getSuccsOf(startNode));

			while (!queue.isEmpty()) {
				Unit current = queue.remove(0);
				if (visited.contains(current)) continue;
				visited.add(current);

				if (current.equals(targetNode)) {
					return true;
				}
				queue.addAll(graph.getSuccsOf(current));
			}
			return false;
		}
	}
}