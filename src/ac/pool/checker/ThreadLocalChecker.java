package ac.pool.checker;

import ac.pool.point.InitPoint;
import ac.pool.point.OneParaKeyPoint;
import soot.RefType;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.jimple.Stmt;

//ThreadLocal误用检查器，专门用于检测在线程池环境中不当使用ThreadLocal的问题。
public class ThreadLocalChecker {
	//	检查在特定的线程池初始化点和任务提交点之间，是否存在ThreadLocal的误用。
	public static  boolean hasThreadLocalMisuse(InitPoint point, OneParaKeyPoint submitPoint) {
//		获取提交点参数（任务对象）的所有可能类型
		for (Type type : submitPoint.getParaLocalPossiableTypes()) {
			RefType refType = (RefType) type;//转为引用类型
//			根据引用类型，获取后台执行方法
			SootMethod backgroundMethod = ExceptionHandlerChecker.getBackgroundMethod(refType);
			//对每个后台方法进行分析，是否有对ThreadPool类的操作。
			if (hasThreadLocalInThreadPool(backgroundMethod)) {
				return true;
			}
		}
		return false;
	}

	private  static  boolean hasThreadLocalInThreadPool(SootMethod sootMethod) {
		if (sootMethod != null && sootMethod.hasActiveBody()) {//方法不为null并且方法有活跃体
			for (Unit unit : sootMethod.getActiveBody().getUnits()) {//对于每一条语句进行分析
				if (unit instanceof Stmt) {
					Stmt stmt = (Stmt) unit;
					if (stmt.containsInvokeExpr()) {//stmt.containsInvokeExpr()表示包含方法调用语句。
//						检查指定的方法中是否包含对ThreadLocal类的任何方法调用。
						if ("java.lang.ThreadLocal"
								.equals(stmt.getInvokeExpr().getMethod().getDeclaringClass().toString())) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

}
