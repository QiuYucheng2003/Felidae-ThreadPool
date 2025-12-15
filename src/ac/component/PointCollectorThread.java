package ac.component;

import ac.constant.ThreadSig;
import ac.pool.point.InitPoint;
import ac.pool.point.KeyPoint;
import ac.pool.point.OneParaKeyPoint;
import ac.pool.point.OneParaValueKeyPoint;
import ac.pool.point.PointCollector;
import ac.util.AsyncInherit;
import soot.Local;
import soot.SootMethod;
import soot.jimple.Stmt;

//Thread和Runnable专用关键点收集器
//专门处理直接使用 Thread 和 Runnable 的异步编程模式，将底层的 Thread 操作映射到统一的关键点体系中。
public class PointCollectorThread extends PointCollector {

//	识别线程未捕获异常处理器的设置
//	检测线程异常处理机制，避免未处理异常导致程序崩溃
	@Override
	protected KeyPoint newSetUncaughtExceptionHandlerPoint(SootMethod method, Stmt stmt) {
		return KeyPoint.newPoint(method, stmt);
	}
	@Override
	protected boolean isSetUncaughtExceptionHandlerPoint(Stmt stmt) {
		return ThreadSig.METHOD_SIG_setUncaughtExceptionHandler
				.equals(stmt.getInvokeExpr().getMethod().getSignature());
	}

	@Override
	protected OneParaKeyPoint newRejectedExecutionHandlerPoint(SootMethod method, Stmt stmt) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected OneParaKeyPoint newSetFactoryPoint(SootMethod method, Stmt stmt) {
		// TODO Auto-generated method stub
		return null;
	}

//	线程状态检查方法，识别线程中断状态检查调用
	@Override
	protected KeyPoint newIsTerminatedPoint(SootMethod method, Stmt stmt) {
		return KeyPoint.newPoint(method, stmt);
	}
	@Override
	protected boolean isIsTerminatedPoint(Stmt stmt) {
		return ThreadSig.METHOD_SUBSIG_IS_INTERRUPTED.equals(stmt.getInvokeExpr().getMethod().getSubSignature());
	}


	@Override
	protected KeyPoint newShutdownPoint(SootMethod method, Stmt stmt) {
		return null;
	}

//	识别线程关闭shutdownnow操作，检测线程的强制终止操作
	@Override
	protected KeyPoint newShutdownNowPoint(SootMethod method, Stmt stmt) {
		// TODO Auto-generated method stub
		return KeyPoint.newPoint(method, stmt);
	}
	@Override
	protected boolean isShutdownNowPoint(Stmt stmt) {
		return ThreadSig.METHOD_SUBSIG_INTERRUPT.equals(stmt.getInvokeExpr().getMethod().getSubSignature())
				|| ThreadSig.METHOD_SUBSIG_INTERRUPT_SAFELY.equals(stmt.getInvokeExpr().getMethod().getSubSignature());
	}


//	任务提交点处理——————创建线程提交关键点（特殊设计）
//	将Thread构造函数统一为"提交点"概念
	@Override
	protected OneParaKeyPoint newSubmitPoint(SootMethod method, Stmt stmt) {
		int index = getParaIndexByType(ThreadSig.CLASS_RUNNABLE, stmt);
		OneParaKeyPoint point = null;
		if(index == -1) {
			point = new OneParaKeyPoint();
			point.setMethod(method);
			point.setStmt(stmt);
			point.setCaller(OneParaKeyPoint.getBaseCaller(stmt));
			point.setParaLocal((Local) point.getCaller());
		}else {
			point = OneParaKeyPoint.newOneParaKeyPoint(method, stmt, index);
		}
		return point;
	}

//	线程初始化方法,识别线程对象创建
//  通过 AsyncInherit.isInheritedFromThread() 确保只处理Thread家族
	@Override
	protected InitPoint newInitPoint(SootMethod method, Stmt stmt) {
		return InitPoint.newInitPoint(method, stmt);
	}
	@Override
	protected boolean isInitPoint(Stmt stmt) {
		SootMethod invokedMethod = stmt.getInvokeExpr().getMethod();
		return invokedMethod .isConstructor() && AsyncInherit.isInheritedFromThread(invokedMethod.getDeclaringClass());
	}

	@Override
	protected boolean isRejectedExecutionHandlerPoint(Stmt stmt) {
		return false;
	}

	@Override
	protected boolean isSetFactoryPoint(Stmt stmt) {
		return false;
	}



	@Override
	protected boolean isShutdownPoint(Stmt stmt) {
		return false;
	}

	@Override
	protected boolean isSubmitPoint(Stmt stmt) {
		return isInitPoint(stmt) ;
	}


//	识别线程启动调用
//	匹配thread.start()
	@Override
	protected KeyPoint newStartPoint(SootMethod method, Stmt stmt) {
		return KeyPoint.newPoint(method, stmt);
	}
	@Override
	protected boolean isStartPoint(Stmt stmt) {
		return ThreadSig.METHOD_SIG_START.equals(stmt.getInvokeExpr().getMethod().getSignature());
	}


	@Override
	protected OneParaValueKeyPoint newSetMaxThreadSizePoint(SootMethod method, Stmt stmt) {
		return null;
	}

	@Override
	protected OneParaValueKeyPoint newSetCoreThreadSizePoint(SootMethod method, Stmt stmt) {
		return null;
	}



	@Override
	protected boolean isSetMaxThreadSizePoint(Stmt stmt) {
		return false;
	}

	@Override
	protected boolean isSetCoreThreadSizePoint(Stmt stmt) {
		return false;
	}

}
