package ac.pool.point;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ac.constant.ThreadSig;
import ac.pool.checker.HTRChecker;
import ac.util.InheritanceProcess;
import ac.util.Log;
import destructible.DestructibleIdentify;
import soot.Body;
import soot.Local;
import soot.PointsToAnalysis;
import soot.PointsToSet;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.Stmt;
//PointCollector，
//PointCollector (抽象基类)
//├── PointCollectorAsyncTask (AsyncTask收集器)
//├── PointCollectorExecutor (线程池收集器)
//└── PointCollectorThread (Thread收集器)

//PointCollector是一个异步关键点收集框架

//——提供了完整的收集算法框架
//——定义了14种关键点类型的存储结构
//——实现了通用的代码遍历和关键点识别流程

//通过遍历所有类的方法体，识别和收集各种异步操作的关键调用点(key caller)，为后续的 misuse 检测提供数据基础。
public abstract class PointCollector {

	protected Set<InitPoint> initialPoints = new HashSet<>();// 初始化点

	protected Set<OneParaKeyPoint> submitPoints = new HashSet<>();// 任务提交点

	protected Set<KeyPoint> shutDownPoints = new HashSet<>();//关闭点

	protected Set<KeyPoint> shutDownNowPoints = new HashSet<>();// 立即关闭点

	protected Set<KeyPoint> startPoints = new HashSet<>();// 启动点

	static protected Set<DestroyPoint> destroyPoints = new HashSet<>(); //销毁点

	protected Set<OneParaKeyPoint> setThreadFactoryPoints = new HashSet<>();// 线程工厂设置

	protected Set<KeyPoint> isTerminatedPoints = new HashSet<>();// 终止状态检查

	protected Set<OneParaKeyPoint> setRejectedExecutionHandlerPoints = new HashSet<>();// 拒绝策略设置

	protected Set<KeyPoint> setUncaughtExceptionHandlerPoints = new HashSet<>();// 异常处理器设置

	protected Set<OneParaValueKeyPoint> setCoreThreadSizePoints = new HashSet<>();// 核心线程数设置

	protected Set<OneParaValueKeyPoint> setMaxThreadSizePoints = new HashSet<>();// 最大线程数设置

	protected Set<KeyPoint> setNamePoints = new HashSet<>();// 线程名设置


	// 固定算法骨架
//	public void start(Collection<SootClass> classes) {
//		// 遍历所有类 → 所有方法 → 所有语句
//		// 调用 findKeyPoint() 进行关键点识别
//		Log.i("## start PointCollector: classes.size() = ", classes.size());
//		for (SootClass sootClass : classes) {//遍历所有类。
//			List<SootMethod> methods = new ArrayList<>(sootClass.getMethods());
//			for (SootMethod method : methods) {//遍历所有方法。
////				if (!Scene.v().getReachableMethods().contains(method)) {
////					continue;
////				}
//				if (method.hasActiveBody()) {// 只分析有avtivebody的方法
//					Body body = method.getActiveBody();
//					for (Unit unit : body.getUnits()) {// 分析每个方法体，遍历每个unit》
//						Stmt stmt = (Stmt) unit;
//						if (stmt.containsInvokeExpr()) {
//							//对于每个方法和该方法中的每个statement,调用findKeyPoint.去进行仔细分析。
//							findKeyPoint(stmt, method);
//						}
//					}
//				}
//			}
//		}
//	}
	public void start(Collection<SootClass> classes) {
		Log.i("## start PointCollector: classes.size() = ", classes.size());
		for (SootClass sootClass : classes) {
			List<SootMethod> methods = new ArrayList<>(sootClass.getMethods());
			for (SootMethod method : methods) {

				// [修复 1] 如果没有 Body，强制加载！
				if (!method.hasActiveBody()) {
					try {
						method.retrieveActiveBody();
					} catch (Exception e) {
						// 某些系统方法无法加载是正常的，忽略
					}
				}

				// [修复 2] 再次检查 hasActiveBody()
				// 此时 Lambda 方法应该已经有 Body 了
				if (method.hasActiveBody()) {
					Body body = method.getActiveBody();
					for (Unit unit : body.getUnits()) {
						Stmt stmt = (Stmt) unit;
						if (stmt.containsInvokeExpr()) {

							// [临时 Debug] 这一步是用来验证是否成功进入了 Lambda 内部
							// 看到这个输出，说明第一关过了
							if (stmt.getInvokeExpr().getMethod().getName().equals("isShutdown")) {
								System.out.println("DEBUG: 成功进入 Lambda 并发现 isShutdown! " + method.getName());
							}

							findKeyPoint(stmt, method);
						}
					}
				} else {
					// 如果强制加载后还是没有，打印日志看看是哪个方法顽固不化
					// System.out.println("DEBUG: 依然没有 Body: " + method.getName());
				}
			}
		}
	}


	// 根据参数类型查找参数索引
	protected int getParaIndexByType(String type, Stmt stmt) {
		for (int i = 0; i < stmt.getInvokeExpr().getMethod().getParameterCount(); i++) {
			if (type.equals(stmt.getInvokeExpr().getMethod().getParameterType(i).toString())) {
				return i;
			}
		}
		return -1;
	}


//	这是关键点识别的核心方法，采用责任链模式
	protected void findKeyPoint(Stmt stmt, SootMethod method) {
//		快速过滤，只处理包含方法调用的语句，排除赋值、跳转等其他语句
		if (!stmt.containsInvokeExpr()) {
			return;
		}

//		接下来是关键点识别链（14个独立检查）
//		对于符合判断的调用点类型，就加入其相应的集合。
		if (isInitPoint(stmt)) {
			InitPoint point = newInitPoint(method, stmt);
			if (point != null) {
				initialPoints.add(point);
			}
		}
		if (isSubmitPoint(stmt)) {
			OneParaKeyPoint point = newSubmitPoint(method, stmt);
			if (point != null) {
				submitPoints.add(point);
			}
		}
		if (isDestroyPoint(stmt)) {
			DestroyPoint point = newDestroyPoint(method, stmt);
			if (point != null) {
				destroyPoints.add(point);
			}

		}
		if (isShutdownNowPoint(stmt)) {
			KeyPoint point = newShutdownNowPoint(method, stmt);
			if (point != null) {
				shutDownNowPoints.add(point);
			}
		}
		if (isShutdownPoint(stmt)) {
			KeyPoint point = newShutdownPoint(method, stmt);
			if (point != null) {
				shutDownPoints.add(point);
			}
		}
		if (isStartPoint(stmt)) {
			KeyPoint point = newStartPoint(method, stmt);
			if (point != null) {
				startPoints.add(point);
			}
		}
		if (isIsTerminatedPoint(stmt)) {
			KeyPoint point = newIsTerminatedPoint(method, stmt);
			if (point != null) {
				isTerminatedPoints.add(point);
			}
		}
		if (isSetFactoryPoint(stmt)) {
			OneParaKeyPoint point = newSetFactoryPoint(method, stmt);
			if (point != null) {
				setThreadFactoryPoints.add(point);
			}
		}
		if (isRejectedExecutionHandlerPoint(stmt)) {
			OneParaKeyPoint point = newRejectedExecutionHandlerPoint(method, stmt);
			if (point != null) {
				setRejectedExecutionHandlerPoints.add(point);
			}
		}
		if (isSetUncaughtExceptionHandlerPoint(stmt)) {
			KeyPoint point = newSetUncaughtExceptionHandlerPoint(method, stmt);
			if (point != null) {
				setUncaughtExceptionHandlerPoints.add(point);
			}
		}

		if (isSetCoreThreadSizePoint(stmt)) {
			OneParaValueKeyPoint point = newSetCoreThreadSizePoint(method, stmt);
			if (point != null) {
				setCoreThreadSizePoints.add(point);
			}
		}

		if (isSetMaxThreadSizePoint(stmt)) {
			Log.e(stmt);
			OneParaValueKeyPoint point = newSetMaxThreadSizePoint(method, stmt);
			if (point != null) {
				setMaxThreadSizePoints.add(point);
			}
		}

		if (isSetThreadNamePoint(stmt)) {
			KeyPoint point = newSetNamePoint(method, stmt);
			if (point != null) {
				setNamePoints.add(point);
			}
		}

	}

	protected KeyPoint newSetNamePoint(SootMethod method, Stmt stmt) {
		return KeyPoint.newPoint(method, stmt);
	}

	protected abstract OneParaValueKeyPoint newSetMaxThreadSizePoint(SootMethod method, Stmt stmt);

	protected abstract OneParaValueKeyPoint newSetCoreThreadSizePoint(SootMethod method, Stmt stmt);

//	protected abstract OneParaValueKeyPoint newSetQueueSizePoint(SootMethod method, Stmt stmt);

	protected abstract KeyPoint newStartPoint(SootMethod method, Stmt stmt);

	protected abstract KeyPoint newSetUncaughtExceptionHandlerPoint(SootMethod method, Stmt stmt);

	protected abstract OneParaKeyPoint newRejectedExecutionHandlerPoint(SootMethod method, Stmt stmt);

	protected abstract OneParaKeyPoint newSetFactoryPoint(SootMethod method, Stmt stmt);

	protected abstract KeyPoint newIsTerminatedPoint(SootMethod method, Stmt stmt);

	protected abstract KeyPoint newShutdownPoint(SootMethod method, Stmt stmt);

	protected abstract KeyPoint newShutdownNowPoint(SootMethod method, Stmt stmt);


//	// DestroyPoint 的特殊处理——通过指针分析识别与可销毁字段相关的调用
	protected DestroyPoint newDestroyPoint(SootMethod method, Stmt stmt) {
//		得到调用者caller，也许是局部变量类型的；
		Value caller = KeyPoint.getBaseCaller(stmt);
		if (caller instanceof Local) {
//		 	得到指针分析器，pta
			PointsToAnalysis pointsToAnalysis = Scene.v().getPointsToAnalysis();
//			遍历可销毁字段；
			for (SootField sootField : HTRChecker.destructibleSootFields) {
//				这个if条件，说明要判断sootField和caller，是否是继承关系？
//				如果是继承关系，说明caller这个Local局部变量是可销毁的，因为这个sootField是可销毁的；
				if (InheritanceProcess.isInheritedFromGivenClass(sootField.getType(), caller.getType())
						|| InheritanceProcess.isInheritedFromGivenClass(caller.getType(), sootField.getType()))
				{
//					指针分析，求得caller可能指向对象的集合
					PointsToSet aliaSet = pointsToAnalysis.reachingObjects((Local) caller, sootField);
					if (!aliaSet.isEmpty()) {
						DestroyPoint point = DestroyPoint.newDestroyPoint(method, stmt, sootField);
						destroyPoints.add(point);
					}
				}
			}
		}
		return null;
	}


	protected abstract boolean isSetMaxThreadSizePoint(Stmt stmt);

	protected abstract boolean isSetCoreThreadSizePoint(Stmt stmt);


	protected abstract boolean isStartPoint(Stmt stmt);


//	判断当前语句是否是异步组件的销毁操作，比如线程中断、线程池关闭、资源释放等。
	protected boolean isDestroyPoint(Stmt stmt) {
		return DestructibleIdentify.isDestroyMethod(stmt.getInvokeExpr().getMethod());
	}
//  用于识别设置线程名称的方法调用，这对于线程调试和监控很重要。
	protected boolean isSetThreadNamePoint(Stmt stmt) {
		return stmt.containsInvokeExpr()
				&& (ThreadSig.METHOD_SIG_SET_NAME.equals(stmt.getInvokeExpr().getMethod().getSignature()));
	}

	protected abstract OneParaKeyPoint newSubmitPoint(SootMethod method, Stmt stmt);

	protected abstract InitPoint newInitPoint(SootMethod method, Stmt stmt);

	protected abstract boolean isSetUncaughtExceptionHandlerPoint(Stmt stmt);

	protected abstract boolean isRejectedExecutionHandlerPoint(Stmt stmt);

	protected abstract boolean isSetFactoryPoint(Stmt stmt);

	protected abstract boolean isIsTerminatedPoint(Stmt stmt);

	protected abstract boolean isShutdownPoint(Stmt stmt);

	protected abstract boolean isShutdownNowPoint(Stmt stmt);

	protected abstract boolean isSubmitPoint(Stmt stmt);

	protected abstract boolean isInitPoint(Stmt stmt);

	public Set<DestroyPoint> getDestroyPoints() {
		return destroyPoints;
	}

	public Set<InitPoint> getInitialPoints() {
		return initialPoints;
	}

	public Set<KeyPoint> getIsTerminatedPoints() {
		return isTerminatedPoints;
	}

	public Set<OneParaKeyPoint> getSetRejectedExecutionHandlerPoints() {
		return setRejectedExecutionHandlerPoints;
	}

	public Set<OneParaKeyPoint> getSetThreadFactoryPoints() {
		return setThreadFactoryPoints;
	}

	public Set<KeyPoint> getSetUncaughtExceptionHandlerPoints() {
		return setUncaughtExceptionHandlerPoints;
	}

	public Set<KeyPoint> getShutDownNowPoints() {
		return shutDownNowPoints;
	}

	public Set<KeyPoint> getShutDownPoints() {
		return shutDownPoints;
	}

	public Set<OneParaKeyPoint> getSubmitPoints() {
		return submitPoints;
	}

	public Set<KeyPoint> getStartPoints() {
		return startPoints;
	}

	public Set<OneParaValueKeyPoint> getSetCoreThreadSizePoints() {
		return setCoreThreadSizePoints;
	}

	public Set<OneParaValueKeyPoint> getSetMaxThreadSizePoints() {
		return setMaxThreadSizePoints;
	}


	public Set<KeyPoint> getSetNamePoints() {
		return setNamePoints;
	}

}
