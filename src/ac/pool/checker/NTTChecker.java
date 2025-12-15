package ac.pool.checker;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import ac.constant.ExecutorSig;
import ac.constant.Signature;
import ac.constant.ThreadSig;
import ac.pool.point.DestroyPoint;
import ac.pool.point.InitPoint;
import ac.pool.point.KeyPoint;
import ac.pool.point.PointCollector;
import soot.Local;
import soot.MethodOrMethodContext;
import soot.PointsToAnalysis;
import soot.PointsToSet;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.CastExpr;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;

public class NTTChecker {

	private static CallGraph cg = Scene.v().getCallGraph();

	private static PointCollector pointCollector;
	// 缓存一个方法——>UnitGraph，一个方法对应一个Graph
	private static Map<SootMethod, WeakReference<UnitGraph>> methodCFGMap = new ConcurrentHashMap<>();

	public static boolean checkNTTMisuse(KeyPoint startPoint, Set<KeyPoint> interruptPoints,
										 PointCollector pointCollector) {
		NTTChecker.pointCollector = pointCollector;

		// 0. 守护线程预检查
		if (isDaemonThread(startPoint)) {
			return false;
		}

		// [NEW FIX] 1. 工厂方法模式白名单 (Factory Pattern Heuristic)
		// 如果方法名暗示它是创建者(create/new/build)，且返回类型是线程池/线程，则认为对象所有权已转移。
		// 这是解决 Factory/Builder 误报最稳健的方式。
		if (isFactoryMethod(startPoint.getMethod())) {
			return false;
		}

		// 2. 逃逸分析检查 (Flow Analysis)
		// 分析对象是否被 return, 赋值给字段, 或作为参数传递。
		if (isEscaped(startPoint)) {
			return false;
		}

		boolean hasInterruptPoint = false;
		for (KeyPoint interruptEvent : interruptPoints) {
			if (startPoint.isAliasCaller(interruptEvent)) {//检查启动点和中断点是否操作 同一对象。
				hasInterruptPoint = true;
//           检查销毁点是否与启动点相关
				for (DestroyPoint destroyEvent : pointCollector.getDestroyPoints()) {
					if(!destroyEvent.isAliasCaller(startPoint)) {
						continue;
					}
//              核心的happens-before关系检查
					if (HB(destroyEvent.getStmt(), interruptEvent.getStmt(), interruptEvent.getMethod())) {
						return true;
					}
				}
			}
		}
		return !hasInterruptPoint;
	}

	// ==================================================================================
	// [NEW FIX] 工厂模式判定 (解决顽固误报)
	// ==================================================================================
	private static boolean isFactoryMethod(SootMethod method) {
		// 1. 检查方法名
		String name = method.getName().toLowerCase();
		boolean isCreatorName = name.startsWith("create") ||
				name.startsWith("new") ||
				name.startsWith("build") ||
				name.startsWith("get") || // 某些 getter 可能会懒加载创建
				name.startsWith("wrap") ||
				// [新增] 处理内部类访问外部类的合成方法 (如 access$000)
				name.startsWith("access");

		if (!isCreatorName) {
			return false;
		}

		// 2. 检查返回类型是否为线程或线程池相关
		Type returnType = method.getReturnType();
		String returnTypeStr = returnType.toString();

		return returnTypeStr.contains("ExecutorService") ||
				returnTypeStr.contains("ThreadPoolExecutor") ||
				returnTypeStr.contains("Thread") ||
				returnTypeStr.contains("DtpExecutor") || // 针对特定框架
				returnTypeStr.contains("Future") || // 有时返回 Future 代表任务提交
				// [新增] 处理 Jetty Context 等上下文对象误报
				returnTypeStr.contains("Context");
	}
	// ==================================================================================
	// [Updated] 逃逸分析相关方法 (增加健壮性)
	// ==================================================================================

	private static boolean isEscaped(KeyPoint point) {
		Local local = extractLocalFromPoint(point);
		if (local == null) return false;

		return checkEscape(point.getMethod(), local);
	}

	private static Local extractLocalFromPoint(KeyPoint point) {
		Unit unit = point.getStmt();
		if (unit instanceof DefinitionStmt) {
			Value left = ((DefinitionStmt) unit).getLeftOp();
			if (left instanceof Local) {
				return (Local) left;
			}
		} else if (unit instanceof Stmt) {
			Stmt stmt = (Stmt) unit;
			if (stmt.containsInvokeExpr()) {
				InvokeExpr invokeExpr = stmt.getInvokeExpr();
				if (invokeExpr instanceof InstanceInvokeExpr && invokeExpr.getMethod().getName().equals("<init>")) {
					Value base = ((InstanceInvokeExpr) invokeExpr).getBase();
					if (base instanceof Local) {
						return (Local) base;
					}
				}
			}
		}
		return null;
	}

	private static boolean checkEscape(SootMethod method, Local allocatedLocal) {
		// [FIX] 强制尝试获取活跃 Body，防止因 Body 被释放导致分析失败
		if (!method.hasActiveBody()) {
			try {
				method.retrieveActiveBody();
			} catch (Exception e) {
				return false; // 无法获取 Body，无法分析，保守返回 false (或可考虑在此处也应用工厂白名单)
			}
		}

		if (!method.hasActiveBody()) return false;

		Set<Value> trackedValues = new HashSet<>();
		Set<String> trackedNames = new HashSet<>();

		trackedValues.add(allocatedLocal);
		trackedNames.add(allocatedLocal.getName());

		boolean changed = true;
		while (changed) {
			changed = false;
			for (Unit unit : method.getActiveBody().getUnits()) {
				if (unit instanceof AssignStmt) {
					AssignStmt assign = (AssignStmt) unit;
					Value left = assign.getLeftOp();
					Value right = assign.getRightOp();

					boolean isTracked = trackedValues.contains(right);
					if (!isTracked && right instanceof Local) {
						isTracked = trackedNames.contains(((Local)right).getName());
					}

					if (!isTracked && right instanceof CastExpr) {
						Value castOp = ((CastExpr) right).getOp();
						if (trackedValues.contains(castOp)) isTracked = true;
						else if (castOp instanceof Local && trackedNames.contains(((Local)castOp).getName())) isTracked = true;
					}

					if (isTracked) {
						if (trackedValues.add(left)) changed = true;
						if (left instanceof Local) {
							if (trackedNames.add(((Local) left).getName())) changed = true;
						}
					}
				}
			}
		}

		for (Unit unit : method.getActiveBody().getUnits()) {
			// A. Check Return
			if (unit instanceof ReturnStmt) {
				ReturnStmt returnStmt = (ReturnStmt) unit;
				Value op = returnStmt.getOp();
				if (trackedValues.contains(op)) return true;
				if (op instanceof Local && trackedNames.contains(((Local)op).getName())) return true;
			}
			// B. Check Field Assign
			if (unit instanceof AssignStmt) {
				AssignStmt assignStmt = (AssignStmt) unit;
				if (assignStmt.getLeftOp() instanceof FieldRef) {
					Value right = assignStmt.getRightOp();
					if (trackedValues.contains(right) || (right instanceof Local && trackedNames.contains(((Local)right).getName()))) {
						return true;
					}
				}
			}
			// C. Check Param Passing
			if (unit instanceof Stmt) {
				Stmt stmt = (Stmt) unit;
				if (stmt.containsInvokeExpr()) {
					InvokeExpr invokeExpr = stmt.getInvokeExpr();
					boolean isSelfInit = false;
					if (invokeExpr instanceof InstanceInvokeExpr) {
						Value base = ((InstanceInvokeExpr) invokeExpr).getBase();
						// 判断是否是对象自身的 <init> 调用
						if (base.equals(allocatedLocal) || (base instanceof Local && trackedNames.contains(((Local)base).getName()))) {
							if (invokeExpr.getMethod().getName().equals("<init>")) {
								isSelfInit = true;
							}
						}
					}

					if (!isSelfInit) {
						for (Value arg : invokeExpr.getArgs()) {
							if (trackedValues.contains(arg) || (arg instanceof Local && trackedNames.contains(((Local)arg).getName()))) {
								return true;
							}
						}
					}
				}
			}
		}
		return false;
	}

	// ==================================================================================
	// [Existing Logic] 守护线程检查
	// ==================================================================================
	private static boolean isDaemonThread(KeyPoint startPoint) {
		PointsToAnalysis pta = Scene.v().getPointsToAnalysis();
		Local startLocal = extractLocalFromPoint(startPoint);
		if (startLocal == null) return false;

		PointsToSet startPts = pta.reachingObjects(startLocal);
		if (scanMethodForSetDaemon(startPoint.getMethod(), startPts)) return true;

		for (InitPoint initPoint : pointCollector.getInitialPoints()) {
			if (initPoint.getCallerPointsToSet().hasNonEmptyIntersection(startPts)) {
				if (scanMethodForSetDaemon(initPoint.getMethod(), startPts)) return true;
			}
		}
		return false;
	}

	private static boolean scanMethodForSetDaemon(SootMethod method, PointsToSet targetPts) {
		// 同样增加 Body 检查
		if (!method.hasActiveBody()) {
			try { method.retrieveActiveBody(); } catch(Exception e) { return false; }
		}
		if (!method.hasActiveBody()) return false;

		PointsToAnalysis pta = Scene.v().getPointsToAnalysis();
		for (Unit unit : method.getActiveBody().getUnits()) {
			if (unit instanceof Stmt) {
				Stmt stmt = (Stmt) unit;
				if (stmt.containsInvokeExpr()) {
					InvokeExpr invokeExpr = stmt.getInvokeExpr();
					SootMethod invokeMethod = invokeExpr.getMethod();
					if (invokeMethod.getName().equals("setDaemon") && invokeMethod.getParameterCount() == 1 && invokeExpr instanceof InstanceInvokeExpr) {
						Value arg = invokeExpr.getArg(0);
						boolean isTrue = false;
						if (arg instanceof IntConstant && ((IntConstant) arg).value == 1) isTrue = true;
						if (isTrue) {
							Value base = ((InstanceInvokeExpr) invokeExpr).getBase();
							if (base instanceof Local) {
								PointsToSet currentBasePts = pta.reachingObjects((Local) base);
								if (currentBasePts.hasNonEmptyIntersection(targetPts)) return true;
							}
						}
					}
				}
			}
		}
		return false;
	}

	// ... (HB 方法无需修改，保持原样) ...
	// destroy happens before unit
//  Happens-Before关系检查
	private static boolean HB(Unit destroyUnit, Unit unit, SootMethod sootMethod) {
		ConcurrentLinkedQueue<Unit> unitQueue = new ConcurrentLinkedQueue<>();
		ConcurrentLinkedQueue<SootMethod> unitToMethodQueue = new ConcurrentLinkedQueue<>();
		HashSet<Unit> visitedUnits = new HashSet<>();
		HashSet<Local> visitedJoinCaller = new HashSet<>();
		visitedUnits.add(unit);
		unitToMethodQueue.add(sootMethod);
		while (!unitQueue.isEmpty()) {
			Unit currentUnit = unitQueue.poll();
			SootMethod currentMethod = unitToMethodQueue.poll();
			visitedUnits.add(currentUnit);
			// destroyEvent --> interruptEvent
			if (currentUnit.equals(destroyUnit)) {
				return true;
			}
			// invoke Expr
			if (currentUnit instanceof Stmt) {
				Stmt stmt = (Stmt) currentUnit;
				if (stmt.containsInvokeExpr()) {
					InvokeExpr invokeExpr = stmt.getInvokeExpr();
					SootMethod invokeMethod = invokeExpr.getMethod();
					// join method
					if (invokeMethod.getSubSignature().equals(ExecutorSig.METHOD_SUBSIG_awaitTermination)) {
						pushMethodUnitsViaJoin(invokeExpr, visitedUnits, visitedJoinCaller, unitQueue,
								unitToMethodQueue);
					}
					// start method
					else if (Signature.startMethods_set.contains(invokeMethod.getSubSignature())) {
						pushMethodUnitsViaStart(invokeExpr, visitedUnits, visitedJoinCaller, unitQueue,
								unitToMethodQueue);
					} else {
						if (!invokeMethod.getDeclaringClass().isLibraryClass()) {
							pushMethodUnitsToConcurrentLinkedQueue(invokeMethod, visitedUnits, unitQueue,
									unitToMethodQueue);
						}
					}
				}
			}
			UnitGraph currentCFG = getUnitGraph(currentMethod);
			List<Unit> preds = currentCFG.getPredsOf(currentUnit);
			if (preds.isEmpty()) {
				// traverse on CG
				Iterator<Edge> edgesInto = cg.edgesInto(currentMethod);
				while (edgesInto.hasNext()) {
					Edge edge = edgesInto.next();
					MethodOrMethodContext edgeSrc = edge.getSrc();
					SootMethod srcMethod = edgeSrc.method();
					if (edge.srcStmt() != null) {
						pushMethodUnitsToConcurrentLinkedQueue(srcMethod, visitedUnits, unitQueue, unitToMethodQueue,
								edge.srcStmt());
					} else {
						pushMethodUnitsToConcurrentLinkedQueue(srcMethod, visitedUnits, unitQueue, unitToMethodQueue);
					}
				}
			} else {
				pushMethodUnitsToConcurrentLinkedQueue(currentMethod, visitedUnits, unitQueue, unitToMethodQueue,
						currentUnit);
			}
		}
		return false;
	}

	private static void pushMethodUnitsToConcurrentLinkedQueue(SootMethod sootMethod, HashSet<Unit> visitedUnits,
															   ConcurrentLinkedQueue<Unit> unitQueue, ConcurrentLinkedQueue<SootMethod> unitToMethodQueue)
	{
		if (sootMethod.hasActiveBody()) {
			UnitGraph srcUnitGraph = getUnitGraph(sootMethod);
			List<Unit> tails = srcUnitGraph.getTails();
			for (Unit tail : tails) {
				unitQueue.add(tail);
				unitToMethodQueue.add(sootMethod);
			}
		}
	}

	private static void pushMethodUnitsToConcurrentLinkedQueue(SootMethod sootMethod, HashSet<Unit> visitedUnits,
															   ConcurrentLinkedQueue<Unit> unitQueue, ConcurrentLinkedQueue<SootMethod> unitToMethodQueue, Unit start) {
		if (sootMethod.hasActiveBody()) {
			UnitGraph srcUnitGraph = getUnitGraph(sootMethod);
			List<Unit> preds = srcUnitGraph.getPredsOf(start);
			for (Unit pred : preds) {
				if (!visitedUnits.contains(pred) && !unitQueue.contains(pred)) {
					unitQueue.add(pred);
					unitToMethodQueue.add(sootMethod);
				}
			}

		}
	}

	private static void pushMethodUnitsViaStart(InvokeExpr invokeExpr, HashSet<Unit> visitedUnits,
												HashSet<Local> visitedJoinCaller, ConcurrentLinkedQueue<Unit> unitQueue,
												ConcurrentLinkedQueue<SootMethod> unitToMethodQueue) {
		if (invokeExpr instanceof InstanceInvokeExpr) {
			InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) invokeExpr;
			Value caller = instanceInvokeExpr.getBase();
			if (caller instanceof Local) {
				Local localCaller = (Local) caller;
				PointsToAnalysis pta = Scene.v().getPointsToAnalysis();
				PointsToSet localCallerPointsToSet = pta.reachingObjects(localCaller);
				for (Local joinCaller : visitedJoinCaller) {
					PointsToSet joinCallerPointsToSet = pta.reachingObjects(joinCaller);
					boolean alais = localCallerPointsToSet.hasNonEmptyIntersection(joinCallerPointsToSet);
					if (alais) {
						return;
					}
				}
				pushFindRunMethodUnitsToConcurrentLinkedQueue(localCaller, visitedUnits, unitQueue, unitToMethodQueue);
			}
		}
		return;
	}

	private static void pushMethodUnitsViaJoin(InvokeExpr invokeExpr, HashSet<Unit> visitedUnits,
											   HashSet<Local> visitedJoinCaller, ConcurrentLinkedQueue<Unit> unitQueue,
											   ConcurrentLinkedQueue<SootMethod> unitToMethodQueue) {
		if (invokeExpr instanceof InstanceInvokeExpr) {
			InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) invokeExpr;
			Value caller = instanceInvokeExpr.getBase();
			if (caller instanceof Local) {
				Local localCaller = (Local) caller;
				visitedJoinCaller.add(localCaller);
				pushFindRunMethodUnitsToConcurrentLinkedQueue(localCaller, visitedUnits, unitQueue, unitToMethodQueue);
			}
		}

	}


	//  1.方法出口处理
//  2.指定起点的前驱处理
	private static void pushFindRunMethodUnitsToConcurrentLinkedQueue(Local localCaller, HashSet<Unit> visitedUnits,
																	  ConcurrentLinkedQueue<Unit> unitQueue, ConcurrentLinkedQueue<SootMethod> unitToMethodQueue) {
		PointsToAnalysis pta = Scene.v().getPointsToAnalysis();
		PointsToSet localCallerPointsToSet = pta.reachingObjects(localCaller);
		for (InitPoint initPoint : pointCollector.getInitialPoints()) {
			boolean alais = localCallerPointsToSet.hasNonEmptyIntersection(initPoint.getCallerPointsToSet());
			if (alais) {
				for (RefType type : initPoint.getCallerPossibleType()) {
					pushRunMethodUnitsToConcurrentLinkedQueue(type.getSootClass(), visitedUnits, unitQueue,
							unitToMethodQueue);

				}
			}

		}
	}
	private static void pushRunMethodUnitsToConcurrentLinkedQueue(SootClass sootClass, HashSet<Unit> visitedUnits,
																  ConcurrentLinkedQueue<Unit> unitQueue, ConcurrentLinkedQueue<SootMethod> unitToMethodQueue) {
		try {
			SootMethod runMethod = sootClass.getMethod(ThreadSig.METHOD_SUBSIG_RUN);
			pushMethodUnitsToConcurrentLinkedQueue(runMethod, visitedUnits, unitQueue, unitToMethodQueue);

		} catch (Exception e) {

		}
	}

	private static UnitGraph getUnitGraph(SootMethod sootMethod) {
		UnitGraph unitGraph = null;

//     缓存检查
		if (methodCFGMap.containsKey(sootMethod)) {
			unitGraph = methodCFGMap.get(sootMethod).get();
		}
//     缓存未命中此方法时，再次创建此图。
		if (unitGraph == null) {
			if (sootMethod.hasActiveBody()) {
				//创建这个SootMethod对应的UnitGraph
				unitGraph = new BriefUnitGraph(sootMethod.getActiveBody());
//           缓存在这个CFGMap中。
				methodCFGMap.put(sootMethod, new WeakReference<UnitGraph>(unitGraph));
			}
		}
		return unitGraph;
	}

}