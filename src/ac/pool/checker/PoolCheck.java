package ac.pool.checker;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import soot.jimple.*;
import soot.*;
import ac.component.PointCollectorExecutor;
import ac.pool.ThreadErrorRecord;
import ac.pool.point.InitPoint;
import ac.pool.point.KeyPoint;
import ac.pool.point.OneParaKeyPoint;
import ac.pool.point.OneParaValueKeyPoint;
import ac.pool.point.PointCollector;
import ac.util.Log;
import soot.jimple.toolkits.annotation.logic.Loop;

import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.LoopNestTree;
import soot.toolkits.graph.UnitGraph;
//线程池和并发问题检测的总控类，集成了11种不同的并发问题检查器

public class PoolCheck implements ICheck {
	public static final ExecutorService executor = Executors
			.newFixedThreadPool(Runtime.getRuntime().availableProcessors() / 2);
	//数据收集器
	protected PointCollector pointCollector = null;
	// 组件名称（用于标识）
	protected String component = null;
	// INR中断未响应问题相关的类集合
	protected Set<String> iNRClasses = null;

	public PoolCheck(PointCollector pointCollector, String component, Set<String> iNRClasses) {
		this.pointCollector = pointCollector;
		this.component = component;
		this.iNRClasses = iNRClasses;
	}
	/**
	 * 增强版逃逸分析：支持局部变量传递追踪
	 * 解决：$r31 (allocation) -> dtpExecutor (local var) -> return dtpExecutor 这种链路
	 */
	private static boolean checkEscape(SootMethod method, Local allocatedLocal) {
		if (!method.hasActiveBody()) return false;

		// 1. 建立追踪集合，初始包含创建点的变量
		Set<Value> trackedValues = new HashSet<>();
		trackedValues.add(allocatedLocal);

		// 2. 迭代传播 (Fixed-point iteration)
		// 目的是找出所有指向该对象的局部变量别名
		// 例如：x = new Obj(); y = x; z = y; return z;
		// 这里的 x, y, z 都应该被加入 trackedValues
		boolean changed = true;
		while (changed) {
			changed = false;
			for (Unit unit : method.getActiveBody().getUnits()) {
				if (unit instanceof AssignStmt) {
					AssignStmt assign = (AssignStmt) unit;
					Value left = assign.getLeftOp();
					Value right = assign.getRightOp();

					// 处理直接赋值: y = x
					if (trackedValues.contains(right)) {
						if (trackedValues.add(left)) {
							changed = true;
						}
					}

					// 处理类型转换: y = (Type) x
					if (right instanceof CastExpr) {
						Value castOp = ((CastExpr) right).getOp();
						if (trackedValues.contains(castOp)) {
							if (trackedValues.add(left)) {
								changed = true;
							}
						}
					}
				}
			}
		}

		// 3. 检查所有追踪变量是否逃逸
		for (Unit unit : method.getActiveBody().getUnits()) {

			// A. 检查 Return 语句
			if (unit instanceof ReturnStmt) {
				ReturnStmt returnStmt = (ReturnStmt) unit;
				if (trackedValues.contains(returnStmt.getOp())) {
					return true; // 只要返回了集合中的任意一个别名，就算逃逸
				}
			}

			// B. 检查赋值给 Field (this.field = x)
			if (unit instanceof AssignStmt) {
				AssignStmt assignStmt = (AssignStmt) unit;
				if (assignStmt.getLeftOp() instanceof FieldRef) {
					if (trackedValues.contains(assignStmt.getRightOp())) {
						return true;
					}
				}
			}

			// C. 检查作为参数传递 (func(x))
			if (unit instanceof Stmt) {
				Stmt stmt = (Stmt) unit;
				if (stmt.containsInvokeExpr()) {
					InvokeExpr invokeExpr = stmt.getInvokeExpr();
					// 遍历参数列表，看是否有我们在追踪的变量
					for (Value arg : invokeExpr.getArgs()) {
						if (trackedValues.contains(arg)) {
							return true;
						}
					}
				}
			}
		}

		return false;
	}

	private static boolean isReachable(UnitGraph graph, Unit startUnit, Unit endUnit) {
		if (startUnit == endUnit) return true;

		Queue<Unit> queue = new LinkedList<>();
		Set<Unit> visited = new HashSet<>();

		// 从起点开始
		queue.add(startUnit);
		visited.add(startUnit);

		while (!queue.isEmpty()) {
			Unit current = queue.poll();

			// 获取当前节点的后继节点 (Succs)
			for (Unit succ : graph.getSuccsOf(current)) {
				// 如果找到了终点
				if (succ == endUnit) {
					return true;
				}

				// 继续搜索未访问过的路径
				if (!visited.contains(succ)) {
					visited.add(succ);
					queue.add(succ);
				}
			}
		}

		// 遍历完所有可能路径都没找到终点 -> 不可达 (互斥)
		return false;
	}

	private static boolean isStmtInLoop(SootMethod method, Unit stmt) {
		// 如果方法没有活跃的方法体（如抽象方法或native方法），无法分析
		if (!method.hasActiveBody()) {
			return false;
		}

		try {
			Body body = method.getActiveBody();

			// ========================= 修正点 =========================
			// 错误写法: LoopNestTree loopNestTree = new LoopNestTree(cfg);
			// 正确写法: 直接传入 body，Soot 会在内部处理 CFG 构建
			LoopNestTree loopNestTree = new LoopNestTree(body);
			// =========================================================

			// 遍历分析出的所有循环
			for (Loop loop : loopNestTree) {
				// getLoopStatements() 返回构成该循环体的所有指令单元
				// 如果我们的创建语句包含在循环体内，则返回 true
				if (loop.getLoopStatements().contains(stmt)) {
					return true;
				}
			}
		} catch (Exception e) {
			// 某些极端情况下构建循环树可能会抛出异常，保守返回 false
			// e.printStackTrace();
		}

		return false;
	}
	private static boolean isSameMethodAndVariable(InitPoint initPoint, Value initVar, KeyPoint terminatePoint) {
		// 1. 必须在同一个方法内
		if (initPoint.getMethod() != terminatePoint.getMethod()) {
			return false;
		}

		// 2. 提取关闭点操作的变量
		Value shutdownVar = null;
		Unit termUnit = terminatePoint.getStmt();
		if (termUnit instanceof Stmt) {
			Stmt stmt = (Stmt) termUnit;
			if (stmt.containsInvokeExpr()) {
				InvokeExpr expr = stmt.getInvokeExpr();
				// shutdown() 是实例方法，必须是 InstanceInvokeExpr
				if (expr instanceof InstanceInvokeExpr) {
					shutdownVar = ((InstanceInvokeExpr) expr).getBase();
				}
			}
		}

		// 3. 比对两个变量是否是同一个 Soot Local 对象
		// 在 Soot 中，同一个 Body 内的同名变量是同一个对象实例，可以直接 equals
		if (shutdownVar != null && initVar.equals(shutdownVar)) {
			return true;
		}

		return false;
	}
	private boolean isReturned(SootMethod method, Value targetVar) {
		if (!method.hasActiveBody()) {
			return false;
		}

		// 遍历方法体内的所有语句
		for (Unit unit : method.getActiveBody().getUnits()) {
			// 检查是否是 ReturnStmt (例如: return $r1)
			if (unit instanceof ReturnStmt) {
				ReturnStmt returnStmt = (ReturnStmt) unit;
				Value returnValue = returnStmt.getOp();

				// 检查返回的值是否就是我们要检测的线程池对象
				if (returnValue.equals(targetVar)) {
					return true;
				}

				// 【可选增强】如果需要更精确的别名分析（处理 $r2 = $r1; return $r2 的情况）
				// 可以在这里结合你现有的 point.isAlias(...) 逻辑，
				// 但通常工厂模式生成的代码非常简单直接 ($r1 = new; return $r1)，上面的 equals 足够覆盖90%情况。
			}
		}
		return false;
	}

//	check方法就是要查11种误用方式，发现问题则记录到ThreadErrorRecord.
	@Override
	public void check() {
		if (pointCollector.getStartPoints().isEmpty()) {
			Log.i(component, " # end: StartPoint set is empty..  ");
			return;
		}
		Log.i(component, " # InitialPoints Size ", pointCollector.getInitialPoints().size());
		Log.i(component, " # StartPoints Size ", pointCollector.getStartPoints().size());

		Log.i(component, " # 1. Start HTR..  ");
//		开始 Hard to Release分析
		for (InitPoint point : pointCollector.getInitialPoints()) {//遍历每一个initPoint
//			遍历submit提交点。
			HTRLoop: for (OneParaKeyPoint submitPoint : pointCollector.getSubmitPoints()) {
				if (!point.isAliasCaller(submitPoint)) {
//					判断initPoint和submitPoint是否为同一个对象，如果是则遍历下一个submitPoint
					continue;
				}
//				提取submitPoint的所有可能涉及到的局部变量类型
				for (RefType refType : submitPoint.getParaLocalPossiableTypes()) {
//					检查该类型是否有HTR误用,如果有HTR误用的话，用ThreadErrorRecord生成HTR-sum.txt
					if (HTRChecker.hasHTRMisuse(refType)) {
						ThreadErrorRecord.recordHTR(component, point, refType.getSootClass());
						break HTRLoop;
					}
				}
			}
		}


//		开始INR的分析
		Log.i(component, " # 2. Start INR..  ");
		for (InitPoint point : pointCollector.getInitialPoints()) {
			INRLoop: for (OneParaKeyPoint submitPoint : pointCollector.getSubmitPoints()) {
				if (!point.isAliasCaller(submitPoint)) {
					continue;
				}
				for (KeyPoint shutDownNowPoint : pointCollector.getShutDownNowPoints()) {
					if (!shutDownNowPoint.isAliasCaller(point)) {
						continue;
					}
					for (RefType refType : submitPoint.getParaLocalPossiableTypes()) {
						if (iNRClasses.contains(refType.toString())) {
							ThreadErrorRecord.recordINR(component, point, refType.getSootClass());
							break INRLoop;
						}
					}
				}
			}
		}

		Log.i(component, " # 3. Start NTT..  ");
		for (InitPoint point : pointCollector.getInitialPoints()) {
			for (OneParaKeyPoint startPoint : pointCollector.getSubmitPoints()) {
				if (!startPoint.isAliasCaller(point)) {
					continue;
				}
				executor.execute(new Runnable() {
					@Override
					public void run() {
						try {
							Set<KeyPoint> keyPoints = new HashSet<KeyPoint>();
							keyPoints.addAll(pointCollector.getShutDownNowPoints());
							keyPoints.addAll(pointCollector.getShutDownPoints());
							if (NTTChecker.checkNTTMisuse(startPoint, keyPoints, pointCollector)) {
								ThreadErrorRecord.recordNTT(component, point);
							}
						} catch (Throwable e) {
							Log.i("## Exception During NTT ##", e.getClass());
						}

					}
				});
			}
		}

//		Log.i(component, " # 3.1. Start NT..  ");
//
//		for (InitPoint point : pointCollector.getInitialPoints()) {
//			Set<KeyPoint> keyPoints = new HashSet<KeyPoint>();
//			keyPoints.addAll(pointCollector.getShutDownNowPoints());
//			keyPoints.addAll(pointCollector.getShutDownPoints());
//			boolean terminate = false;
//			for (KeyPoint terminatePoint : keyPoints) {
//				if (point.isAliasCaller(terminatePoint)) {
//					terminate = true;
//					break;
//				}
//			}
//			if (!terminate) {
//				ThreadErrorRecord.recordNT(component, point);
//			}
//		}


// ...


//		// ... 在 detectMisuse 方法中 ...
//
//		Log.i(component, " # 3.1. Start NT..  ");
//
//		for (InitPoint point : pointCollector.getInitialPoints()) {
//
//			// 1. =========================== 语义过滤逻辑 (保持不变) ===========================
//			SootMethod method = point.getMethod();
//			String methodName = method.getName();
//			SootClass declaringClass = method.getDeclaringClass();
//			String className = declaringClass.getName();
//
//			boolean isLambdaContext =
//					className.contains("lambda") ||
//							className.contains("Lambda") ||
//							methodName.contains("lambda$") ||
//							(className.contains("$") && (methodName.equals("apply") || methodName.equals("run")));
//
//			boolean isAllocation = false;
//			Value allocatedLocal = null;
//
//			// 提取创建点对应的变量
//			if (point.getStmt() instanceof DefinitionStmt) {
//				DefinitionStmt stmt = (DefinitionStmt) point.getStmt();
//				// 兼容 NewExpr 和 staticinvoke (工厂方法返回)
//				if (stmt.getRightOp() instanceof NewExpr || stmt.containsInvokeExpr()) {
//					// 这里稍微放宽条件：只要是赋值语句，我们就尝试提取左值变量
//					isAllocation = stmt.getRightOp() instanceof NewExpr;
//					allocatedLocal = stmt.getLeftOp();
//				}
//			} else if (point.getStmt() instanceof Stmt && ((Stmt)point.getStmt()).containsInvokeExpr()) {
//				// 兼容 specialinvoke $r0.<init>...
//				InvokeExpr expr = ((Stmt)point.getStmt()).getInvokeExpr();
//				if (expr instanceof InstanceInvokeExpr && expr.getMethod().getName().equals("<init>")) {
//					isAllocation = true; // 构造函数视为分配
//					allocatedLocal = ((InstanceInvokeExpr) expr).getBase();
//				}
//			}
//
//			// 2. =========================== 执行 Lambda/Wrapper 过滤 (保持不变) ===========================
//			if (isLambdaContext && !isAllocation) {
//				continue;
//			}
//
//			// 3. =========================== 逃逸分析 (保持不变) ===========================
//			if (allocatedLocal != null) {
//				if (isReturned(method, allocatedLocal)) {
//					continue;
//				}
//			}
//
//			// 4. =========================== 核心修正：Shutdown 匹配增强 ===========================
//			Set<KeyPoint> keyPoints = new HashSet<KeyPoint>();
//			keyPoints.addAll(pointCollector.getShutDownNowPoints());
//			keyPoints.addAll(pointCollector.getShutDownPoints());
//
//			boolean terminate = false;
//			for (KeyPoint terminatePoint : keyPoints) {
//				// A. 原有的别名分析检查
//				if (point.isAliasCaller(terminatePoint)) {
//					terminate = true;
//					break;
//				}
//
//				// B. 【新增】同方法内的局部变量一致性检查
//				// 解决 main 方法中 staticinvoke 返回值与 shutdown 调用未关联的问题
//				if (allocatedLocal != null) {
//					if (isSameMethodAndVariable(point, allocatedLocal, terminatePoint)) {
//						terminate = true;
//						break;
//					}
//				}
//			}
//
//			if (!terminate) {
//				ThreadErrorRecord.recordNT(component, point);
//			}
//		}
//
		// ... inside detectMisuse method ...

//	    2025——12——3
//		Log.i(component, " # 3.1. Start NT..  ");
//
//		for (InitPoint point : pointCollector.getInitialPoints()) {
//
//			// 1. =========================== 语义过滤逻辑 (保持不变) ===========================
//			SootMethod method = point.getMethod();
//			String methodName = method.getName();
//			SootClass declaringClass = method.getDeclaringClass();
//			String className = declaringClass.getName();
//
//			boolean isLambdaContext =
//					className.contains("lambda") ||
//							className.contains("Lambda") ||
//							methodName.contains("lambda$") ||
//							(className.contains("$") && (methodName.equals("apply") || methodName.equals("run")));
//
//			boolean isAllocation = false;
//			Local allocatedLocal = null; // 类型改为 Local，方便后续处理
//
//			// 提取创建点对应的变量
//			if (point.getStmt() instanceof DefinitionStmt) {
//				DefinitionStmt stmt = (DefinitionStmt) point.getStmt();
//				if (stmt.getRightOp() instanceof NewExpr || stmt.containsInvokeExpr()) {
//					isAllocation = stmt.getRightOp() instanceof NewExpr;
//					if (stmt.getLeftOp() instanceof Local) {
//						allocatedLocal = (Local) stmt.getLeftOp();
//					}
//				}
//			} else if (point.getStmt() instanceof Stmt && ((Stmt)point.getStmt()).containsInvokeExpr()) {
//				InvokeExpr expr = ((Stmt)point.getStmt()).getInvokeExpr();
//				if (expr instanceof InstanceInvokeExpr && expr.getMethod().getName().equals("<init>")) {
//					isAllocation = true;
//					Value base = ((InstanceInvokeExpr) expr).getBase();
//					if (base instanceof Local) {
//						allocatedLocal = (Local) base;
//					}
//				}
//			}
//
//			// 2. =========================== Lambda/Wrapper 过滤 (保持不变) ===========================
//			if (isLambdaContext && !isAllocation) {
//				continue;
//			}
//
//			// 3. =========================== [修改] 增强型逃逸分析 ===========================
//			// 针对 ThreadPoolBuilder (返回创建对象) 和 ExecutorWrapper (被包装后流出)
//			if (allocatedLocal != null) {
//				// 使用新的 checkEscape 方法替代单纯的 isReturned
//				if (checkEscape(method, allocatedLocal)) {
//					continue; // 如果对象逃逸（被返回、被赋值给字段、被当作参数传递），则不在此处检查 NT
//				}
//			}
//
//			// 4. =========================== Shutdown 匹配增强 (保持你的修改) ===========================
//			Set<KeyPoint> keyPoints = new HashSet<KeyPoint>();
//			keyPoints.addAll(pointCollector.getShutDownNowPoints());
//			keyPoints.addAll(pointCollector.getShutDownPoints());
//
//			boolean terminate = false;
//			for (KeyPoint terminatePoint : keyPoints) {
//				// A. 原有的别名分析检查
//				if (point.isAliasCaller(terminatePoint)) {
//					terminate = true;
//					break;
//				}
//
//				// B. 同方法内的局部变量一致性检查
//				if (allocatedLocal != null) {
//					if (isSameMethodAndVariable(point, allocatedLocal, terminatePoint)) {
//						terminate = true;
//						break;
//					}
//				}
//			}
//
//			if (!terminate) {
//				ThreadErrorRecord.recordNT(component, point);
//			}
//		}
		Log.i(component, " # 3.1. Start NT..  ");

		for (InitPoint point : pointCollector.getInitialPoints()) {

			// 1. =========================== 语义过滤逻辑 (基础) ===========================
			SootMethod method = point.getMethod();
			String methodName = method.getName();
			SootClass declaringClass = method.getDeclaringClass();
			String className = declaringClass.getName();

			boolean isLambdaContext =
					className.contains("lambda") ||
							className.contains("Lambda") ||
							methodName.contains("lambda$") ||
							(className.contains("$") && (methodName.equals("apply") || methodName.equals("run")));

			boolean isAllocation = false;
			Local allocatedLocal = null;
			InvokeExpr originInvokeExpr = null; // [新增] 用于后续判断方法名

			// 提取创建点对应的变量
			if (point.getStmt() instanceof DefinitionStmt) {
				DefinitionStmt stmt = (DefinitionStmt) point.getStmt();
				if (stmt.getRightOp() instanceof NewExpr || stmt.containsInvokeExpr()) {
					isAllocation = stmt.getRightOp() instanceof NewExpr;
					if (stmt.getLeftOp() instanceof Local) {
						allocatedLocal = (Local) stmt.getLeftOp();
					}
					// [新增] 记录调用的表达式，用于后续分析来源
					if (stmt.containsInvokeExpr()) {
						originInvokeExpr = stmt.getInvokeExpr();
					}
				}
			} else if (point.getStmt() instanceof Stmt && ((Stmt)point.getStmt()).containsInvokeExpr()) {
				InvokeExpr expr = ((Stmt)point.getStmt()).getInvokeExpr();
				if (expr instanceof InstanceInvokeExpr && expr.getMethod().getName().equals("<init>")) {
					isAllocation = true;
					Value base = ((InstanceInvokeExpr) expr).getBase();
					if (base instanceof Local) {
						allocatedLocal = (Local) base;
					}
				}
			}

			// 2. =========================== Lambda/Wrapper 过滤 ===========================
			if (isLambdaContext && !isAllocation) {
				continue;
			}

			// 2.5. =========================== [新增/核心修复] 借用模式过滤 (Borrowing Logic) ===========================
			// 目的：过滤掉 access$000, getExecutor, getCurrentContext 等非创建型获取
			// 逻辑：如果变量不是通过 new 创建的，且来源方法的命名暗示它是“借用”或“访问”，则跳过检测
			if (!isAllocation && originInvokeExpr != null) {
				String invokedMethodName = originInvokeExpr.getMethod().getName();

				// 1. 过滤合成方法 (Synthetic Accessor) - 解决 access$000 误报
				// 编译器生成的用于访问外部类私有字段的方法通常以 access$ 开头
				if (invokedMethodName.startsWith("access$")) {
					continue;
				}

				// 2. 过滤 Getter 类方法 - 解决 getExecutor, getCurrentContext 误报
				// 通常以 get, current, find, lookup 开头的方法返回的是已有对象的引用
				if (invokedMethodName.startsWith("get") ||
						invokedMethodName.startsWith("current") ||
						invokedMethodName.startsWith("find") ||
						invokedMethodName.startsWith("lookup") ||
						invokedMethodName.equals("executor")) { // 某些流畅风格直接用名词

					// 特例排除：如果方法名包含 factory, new, create, build，即使以 get 开头也要小心（如 getNewInstance）
					// 但对于 NT 检测，保守起见，getter 通常不需要在当前上下文关闭
					continue;
				}

				// 3. 过滤单例模式
				if (invokedMethodName.equals("getInstance")) {
					continue;
				}
			}

			// 3. =========================== 增强型逃逸分析 ===========================
			// 针对 ThreadPoolBuilder (返回创建对象) 和 ExecutorWrapper (被包装后流出)
			if (allocatedLocal != null) {
				// 使用 checkEscape 方法替代单纯的 isReturned
				if (checkEscape(method, allocatedLocal)) {
					continue; // 如果对象逃逸（被返回、被赋值给字段、被当作参数传递），则不在此处检查 NT
				}
			}

			// 4. =========================== Shutdown 匹配增强 ===========================
			Set<KeyPoint> keyPoints = new HashSet<KeyPoint>();
			keyPoints.addAll(pointCollector.getShutDownNowPoints());
			keyPoints.addAll(pointCollector.getShutDownPoints());

			boolean terminate = false;
			for (KeyPoint terminatePoint : keyPoints) {
				// A. 原有的别名分析检查
				if (point.isAliasCaller(terminatePoint)) {
					terminate = true;
					break;
				}

				// B. 同方法内的局部变量一致性检查
				if (allocatedLocal != null) {
					if (isSameMethodAndVariable(point, allocatedLocal, terminatePoint)) {
						terminate = true;
						break;
					}
				}
			}

			if (!terminate) {
				ThreadErrorRecord.recordNT(component, point);
			}
		}











		Log.i(component, " # 4. Start IL..  ");

		for (KeyPoint point : pointCollector.getIsTerminatedPoints()) {
			if (ILChecker.isShutDownMisuse(pointCollector.getShutDownPoints(), point)) {
				ThreadErrorRecord.recordIL(component, point);
			}

		}
//		Log.i(component, " # 4. Start IL (Scope + Heuristic Name Check)..  ");
//
//		for (KeyPoint point : pointCollector.getIsTerminatedPoints()) {
//			// 使用新的 ScopeILChecker，传入所有收集到的 shutdown 点
//			if (ScopeILChecker.isShutDownMisuse(pointCollector.getShutDownPoints(), point)) {
//				ThreadErrorRecord.recordIL(component, point);
//			}
//		}



		Log.i(component, " # 5. Start CallerRunsChecker..  ");
		for (InitPoint point : pointCollector.getInitialPoints()) {
//			有setRejectedExecutionHandlerPoint
			if (CallerRunsChecker.hasMisuse(point, pointCollector.getSetRejectedExecutionHandlerPoints())) {
				ThreadErrorRecord.recordCallerRunsChecker(component, point);
			}
		}















//		Log.i(component, " # 6. Start ExceptionHandlerChecker..  ");
//		for (InitPoint point : pointCollector.getInitialPoints()) {
////			 有设置线程工厂的point、有submitPoint、有setUncaughtExceptionHandlerPoint
//			if (ExceptionHandlerChecker.hasMisuse(point, pointCollector.getSetThreadFactoryPoints(),
//					pointCollector.getSubmitPoints(), pointCollector.getSetUncaughtExceptionHandlerPoints())) {
//				ThreadErrorRecord.recordNonExceptionHandlerChecker(component, point);
//			}
//		}
		Log.i(component, " # 6. Start ExceptionHandlerChecker (Scope Based)..  ");
		for (InitPoint point : pointCollector.getInitialPoints()) {
			// 使用新的 ScopeChecker
			// 逻辑：只要在当前类作用域内发现了“裸奔”的任务逻辑（无try-catch），且没有明显的Factory Handler，就报错
			boolean hasMisuse = ScopeExceptionHandlerChecker.hasMisuse(
					point,
					pointCollector.getSetThreadFactoryPoints(),
					pointCollector.getSetUncaughtExceptionHandlerPoints()
			);

			if (hasMisuse) {
				ThreadErrorRecord.recordNonExceptionHandlerChecker(component, point);
			}
		}















		Log.i(component, " # 7. Start RepeatedlyCreateThreadPool (Loop & Path Reachability)..  ");

// 用于策略1：统计每个方法内创建线程池的次数
		Map<SootMethod, List<InitPoint>> methodCreationCounts = new HashMap<>();

// 遍历所有收集到的创建点
		for (InitPoint point : pointCollector.getInitialPoints()) {
			SootMethod method = point.getMethod();

			// ---------------------------------------------------------
			// 策略 1 数据准备：将创建点按方法分组
			// ---------------------------------------------------------
			if (!methodCreationCounts.containsKey(method)) {
				methodCreationCounts.put(method, new ArrayList<InitPoint>());
			}
			methodCreationCounts.get(method).add(point);

			// ---------------------------------------------------------
			// 策略 2：循环体检测 (Loop Detection) - 保持不变，非常重要
			// ---------------------------------------------------------
			if (isStmtInLoop(method, point.getStmt())) {
				Log.i(component, "RCTP found in Loop: " + method.getSignature());
				ThreadErrorRecord.recordRCTP(component, point);
			}
		}

// ---------------------------------------------------------
// 策略 1 执行：同方法内【顺序执行】的多实例检测
// 修改逻辑：只有当两个创建点之间存在“可达路径”时，才判定为 RCTP
// ---------------------------------------------------------
		for (Map.Entry<SootMethod, List<InitPoint>> entry : methodCreationCounts.entrySet()) {
			List<InitPoint> points = entry.getValue();

			// 如果数量少于 2，肯定构不成重复创建，直接跳过
			if (points.size() < 2) continue;

			SootMethod method = entry.getKey();
			if (!method.hasActiveBody()) continue;

			// 构建控制流图 (CFG)，用于判断语句之间的连通性
			BriefUnitGraph cfg = new BriefUnitGraph(method.getActiveBody());

			// 用于记录真正确认误用的点，避免重复记录
			Set<InitPoint> truePositivePoints = new HashSet<>();

			// 两两比较所有创建点，检查是否存在 A -> B 的路径
			for (int i = 0; i < points.size(); i++) {
				for (int j = 0; j < points.size(); j++) {
					if (i == j) continue; // 不和自己比

					InitPoint pointA = points.get(i);
					InitPoint pointB = points.get(j);

					// 核心检测：如果在 CFG 中，从 A 可以走到 B
					// 说明在一次运行中，可能会先创建 A，然后又创建 B -> 这才是 RCTP
					if (isReachable(cfg, pointA.getStmt(), pointB.getStmt())) {
						truePositivePoints.add(pointA);
						truePositivePoints.add(pointB);
					}
				}
			}

			// 记录确认的误用点
			if (!truePositivePoints.isEmpty()) {
				Log.i(component, "RCTP found (Sequential creations): " + method.getSignature());
				for (InitPoint point : truePositivePoints) {
					ThreadErrorRecord.recordRCTP(component, point);
				}
			} else {
				// 如果虽然有多个点，但彼此不可达（互斥分支），则认为是 Factory 模式，忽略
				Log.i(component, "Ignored Mutually Exclusive Creations (Factory Pattern): " + method.getSignature());
			}
		}
//		Log.i(component, " # 7. Start RepeatedlyCreateThreadPool..  ");
//// 简单的基于方法数量的检测
//		if (pointCollector.getInitialPoints().size() > 1) {
//			Log.i(component, "Multiple thread pool creation methods found: " +
//					pointCollector.getInitialPoints().size());
//			for (InitPoint point : pointCollector.getInitialPoints()) {
//				ThreadErrorRecord.recordRCTP(component, point);
//			}
//		}

////		重复创建线程池
//		Log.i(component, " # 7. Start RepeatedlyCreateThreadPool..  ");
//		for (InitPoint point : pointCollector.getInitialPoints()) {
//			CallGraph callGraph = Scene.v().getCallGraph();
//			Iterator<Edge> edgesInto = callGraph.edgesInto(point.getMethod());
//			int count = 0;
//			while (edgesInto.hasNext()) {
//				edgesInto.next();
//				count++;
//				if (count > 1) {
//					ThreadErrorRecord.recordRCTP(component, point);
//					break;
//				}
//			}
//		}






















//		Log.i(component, " # 8. Start UnrefactoredThreadLocal (UTL)..  ");
//		if (pointCollector instanceof PointCollectorExecutor) {
//			for (InitPoint point : pointCollector.getInitialPoints()) {
//				for (OneParaKeyPoint submitPoint : pointCollector.getSubmitPoints()) {
//					if (!point.isAliasCaller(submitPoint)) {
//						continue;
//					}
//					if (ThreadLocalChecker.hasThreadLocalMisuse(point, submitPoint)) {
//						ThreadErrorRecord.recordUTL(component, point);
//						break;
//					}
//				}
//			}
//		}

		Log.i(component, " # 8. Start UnrefactoredThreadLocal (UTL)..  ");
		// 注意：这里不再需要依赖 pointCollector instanceof PointCollectorExecutor
		// 也不需要遍历 submitPoints，因为我们是基于 Scope 扫描
		for (InitPoint point : pointCollector.getInitialPoints()) {
			// 直接传入 InitPoint，让 Checker 去扫描该 Point 所在的整个类环境
			if (ScopeThreadLocalChecker.hasThreadLocalMisuse(point)) {
				ThreadErrorRecord.recordUTL(component, point);
				// 如果同一个类里只需报一次错，可以在这里 break，否则去掉 break
				// break;
			}
		}


//
		Log.i(component, " # 9. Start UnboundedNumberOfThread (UBNT Core)..  ");
		if (pointCollector instanceof PointCollectorExecutor) {
			for (InitPoint point : pointCollector.getInitialPoints()) {
				for (OneParaValueKeyPoint setSizePoint : pointCollector.getSetCoreThreadSizePoints()) {
					if (!setSizePoint.isAliasCaller(point)) {
						continue;
					}
					if (IntMaxChecker.hasMaxIntegerSizeMisuse(setSizePoint)) {
						ThreadErrorRecord.recordUBNT(component , point);
						break;
					}
				}
			}
		}
		Log.i(component, " # 10. Start UnboundedNumberOfThread (UBNT Max)..  ", pointCollector.getSetMaxThreadSizePoints());
		if (pointCollector instanceof PointCollectorExecutor) {
			for (InitPoint point : pointCollector.getInitialPoints()) {
				for (OneParaValueKeyPoint setSizePoint : pointCollector.getSetMaxThreadSizePoints()) {
					if (!setSizePoint.isAliasCaller(point)) {
						continue;
					}
					if (IntMaxChecker.hasMaxIntegerSizeMisuse(setSizePoint)) {
						ThreadErrorRecord.recordUBNT(component , point);
						break;
					}
				}
			}
		}

//		Log.i(component, " # 11. Start UnnamedThread (UNT) ..  ");
//		for (InitPoint point : pointCollector.getInitialPoints()) {
////			setThreadFactory传进去；和setName的地方传进去；
//			if (SetThreadNameChecker.hasMisuse(point, pointCollector.getSetThreadFactoryPoints(),
//					pointCollector.getSetNamePoints())) {
//				ThreadErrorRecord.recordUNT(component, point);
//				break;
//			}
//		}

		Log.i(component, " # 11. Start UnnamedThread (UNT) (Scope Based)..  ");
		for (InitPoint point : pointCollector.getInitialPoints()) {
//        注意：这里不再传入 setFactoryPoints 和 setNamePoints，只传入 InitPoint
			if (ScopeThreadNameChecker.hasMisuse(point)) {
				ThreadErrorRecord.recordUNT(component, point);
				break;
			}
		}




	}

}
