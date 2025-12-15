package ac.component;

import ac.constant.ExecutorSig;
import ac.constant.ThreadSig;
import ac.pool.point.InitPoint;
import ac.pool.point.KeyPoint;
import ac.pool.point.OneParaKeyPoint;
import ac.pool.point.OneParaValueKeyPoint;
import ac.pool.point.PointCollector;
import ac.util.AsyncInherit;
import soot.IntType;
import soot.SootMethod;
import soot.jimple.DefinitionStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
//线程池执行器专用关键点收集器
//专门处理 Java Executor 框架相关的异步编程模式，包括线程池的创建、配置、任务提交和生命周期管理。
public class PointCollectorExecutor extends PointCollector {
	//	识别线程池创建，完整覆盖Java线程池的所有创建方式
	@Override
	protected InitPoint newInitPoint(SootMethod method, Stmt stmt) {
		return InitPoint.newInitPoint(method, stmt);
	}
	//	识别线程池执行器的初始化点，支持两种创建方式。
//	不匹配的情况包括：
//  非方法调用语句
//  非构造函数且非静态方法
//  静态方法调用但没有赋值给Executor类型变量
//  构造函数但不是Executor家族
//  赋值语句但左值类型不是Executor
	@Override
	protected boolean isInitPoint(Stmt stmt) {
//		invokeExpr: 获取语句中的方法调用表达式，invokedMethod: 提取被调用的具体方法
		InvokeExpr invokeExpr = stmt.getInvokeExpr();
		SootMethod invokedMethod = invokeExpr.getMethod();
//		检查是否是构造函数；检查声明类是否继承自Executor
		if (invokedMethod.isConstructor() && AsyncInherit.isInheritedFromExecutor(invokedMethod.getDeclaringClass())) {
			return true;
		}
//		检查是否是静态方法、检查是否是赋值语句、检查赋值左值的类型
		else if (invokedMethod.isStatic())
		{
			if (stmt instanceof DefinitionStmt) {
				DefinitionStmt definitionStmt = (DefinitionStmt) stmt;
				if (AsyncInherit.isInheritedFromExecutor(definitionStmt.getLeftOp().getType())) {
					return true;
				}
			}
		}
		return false;
	}

	//  识别任务提交操作，检测线程池的任务提交模式
//  覆盖所有提交方式：
//  submit(Callable)
//  submit(Runnable)
//  submit(Runnable, T)
//  execute(Runnable)
	@Override
	protected OneParaKeyPoint newSubmitPoint(SootMethod method, Stmt stmt) {
		return OneParaKeyPoint.newOneParaKeyPoint(method, stmt, 0);
	}
	@Override
	protected boolean isSubmitPoint(Stmt stmt) {
		String subSig = stmt.getInvokeExpr().getMethod().getSubSignature();
		return ExecutorSig.METHOD_SUBSIG_SUBMIT_CALLABLE.equals(subSig)
				|| ExecutorSig.METHOD_SUBSIG_SUBMIT_RUNNABLE.equals(subSig)
				|| ExecutorSig.METHOD_SUBSIG_SUBMIT_RUNNABLE_T.equals(subSig)
				|| ExecutorSig.METHOD_SUBSIG_EXECUTE.equals(subSig);
	}
	//	识别线程池关闭操作
//	优雅关闭：shutdown() - 等待任务完成
//  强制关闭：shutdownNow() - 立即终止
	@Override
	protected KeyPoint newShutdownPoint(SootMethod method, Stmt stmt) {
		return KeyPoint.newPoint(method, stmt);
	}
	@Override
	protected KeyPoint newShutdownNowPoint(SootMethod method, Stmt stmt) {
		return KeyPoint.newPoint(method, stmt);
	}
	@Override
	protected boolean isShutdownPoint(Stmt stmt) {
		return ExecutorSig.METHOD_SUBSIG_SHUT_DOWN.equals(stmt.getInvokeExpr().getMethod().getSubSignature());
	}
	@Override
	protected boolean isShutdownNowPoint(Stmt stmt) {
		return ExecutorSig.METHOD_SUBSIG_SHUT_DOWN_NOW.equals(stmt.getInvokeExpr().getMethod().getSubSignature());
	}

	//  识别线程池启动
//	在Executor中，任务提交即视为启动
	@Override
	protected KeyPoint newStartPoint(SootMethod method, Stmt stmt) {
		return KeyPoint.newPoint(method, stmt);
	}
	@Override
	protected boolean isStartPoint(Stmt stmt) {
		return isSubmitPoint(stmt);
	}



	//	线程池状态检查方法，识别线程池状态检查调用
//  匹配：executor.isShutdown() 和 executor.isTerminated()
	@Override
	protected KeyPoint newIsTerminatedPoint(SootMethod method, Stmt stmt) {
		return KeyPoint.newPoint(method, stmt);
	}
//	@Override
//	protected boolean isIsTerminatedPoint(Stmt stmt) {
//		return ExecutorSig.METHOD_SUBSIG_IS_SHUT_DOWN.equals(stmt.getInvokeExpr().getMethod().getSubSignature())
//				|| ExecutorSig.METHOD_SUBSIG_IS_TERMINATED.equals(stmt.getInvokeExpr().getMethod().getSubSignature());
//	}

	@Override
	protected boolean isIsTerminatedPoint(Stmt stmt) {
		SootMethod method = stmt.getInvokeExpr().getMethod();
		String methodName = method.getName();

		// [修复 3] 使用方法名匹配，而不是严格的子签名匹配
		// 只要方法名叫 isShutdown 且没有参数，就认为是目标
		if (methodName.equals("isShutdown") || methodName.equals("isTerminated")) {
			if (method.getParameterCount() == 0) {
				// 打印日志验证是否被这里捕获
				System.out.println("DEBUG: 捕获到终止检查点: " + methodName);
				return true;
			}
		}

		// 保留你原有的逻辑作为备份
		return ExecutorSig.METHOD_SUBSIG_IS_SHUT_DOWN.equals(method.getSubSignature())
				|| ExecutorSig.METHOD_SUBSIG_IS_TERMINATED.equals(method.getSubSignature());
	}
	//	识别线程工厂设置，检测线程创建和命名的自定义逻辑
//  基于 ThreadFactory 类型识别参数
	@Override
	protected OneParaKeyPoint newSetFactoryPoint(SootMethod method, Stmt stmt) {
		int index = getParaIndexByType(ExecutorSig.CLASS_ThreadFactory, stmt);
		return OneParaKeyPoint.newOneParaKeyPoint(method, stmt, index);
	}

	@Override
	protected boolean isSetFactoryPoint(Stmt stmt) {
		return (isInitPoint(stmt) && stmt.getInvokeExpr().getMethod().getParameterTypes().toString()
				.contains(ExecutorSig.METHOD_SIG_setThreadFactory))
				|| ExecutorSig.METHOD_SIG_setThreadFactory.equals(stmt.getInvokeExpr().getMethod().getSignature());
	}

	//	识别拒绝策略处理器设置，检测线程池的任务拒绝处理策略
//	通过类型找到 RejectedExecutionHandler 参数位置
	@Override
	protected OneParaKeyPoint newRejectedExecutionHandlerPoint(SootMethod method, Stmt stmt) {
		int index = getParaIndexByType(ExecutorSig.CLASS_RejectedExecutionHandler, stmt);
		return OneParaKeyPoint.newOneParaKeyPoint(method, stmt, index);
	}
	@Override
	protected boolean isRejectedExecutionHandlerPoint(Stmt stmt) {
		return (isInitPoint(stmt) && stmt.getInvokeExpr().getMethod().getParameterTypes().toString()
				.contains(ExecutorSig.METHOD_SIG_setRejectedExecutionHandler))
				|| ExecutorSig.METHOD_SIG_setRejectedExecutionHandler
				.equals(stmt.getInvokeExpr().getMethod().getSignature());
	}



	//	异常处理相关方法，识别线程池异常处理器设置
	@Override
	protected KeyPoint newSetUncaughtExceptionHandlerPoint(SootMethod method, Stmt stmt) {
		return KeyPoint.newPoint(method, stmt);
	}
	@Override
	protected boolean isSetUncaughtExceptionHandlerPoint(Stmt stmt) {
		return (isInitPoint(stmt) && stmt.getInvokeExpr().getMethod().getParameterTypes().toString()
				.contains(ThreadSig.METHOD_SIG_setUncaughtExceptionHandler))
				|| ThreadSig.METHOD_SIG_setUncaughtExceptionHandler
				.equals(stmt.getInvokeExpr().getMethod().getSignature());
	}



// 识别最大线程数配置
	@Override
	protected OneParaValueKeyPoint newSetMaxThreadSizePoint(SootMethod method, Stmt stmt) {
		int index = 0;
		if (stmt.getInvokeExpr().getArgCount() > 1 && stmt.getInvokeExpr().getArg(1).getType() instanceof IntType) {
			index = 1;
		}
		return OneParaValueKeyPoint.newOneParaValueKeyPoint(method, stmt, index);
	}
//	识别核心线程数配置，显示方式为setCorePoolSize(coreSize)
	@Override
	protected OneParaValueKeyPoint newSetCoreThreadSizePoint(SootMethod method, Stmt stmt) {
		int index = 0;
		return OneParaValueKeyPoint.newOneParaValueKeyPoint(method, stmt, index);
	}


	@Override
	protected boolean isSetCoreThreadSizePoint(Stmt stmt) {
		if (ExecutorSig.METHOD_SIG_setCorePoolSize.equals(stmt.getInvokeExpr().getMethod().getSignature())) {
			return true;
		} else {
			if (isInitPoint(stmt)) {
				if (stmt.getInvokeExpr().getArgCount() > 0
						&& stmt.getInvokeExpr().getArg(0).getType() instanceof IntType) {
					return true;
				}
			}
		}
		return false;
	}



	@Override
	protected boolean isSetMaxThreadSizePoint(Stmt stmt) {
		return ExecutorSig.METHOD_SIG_setMaximumPoolSize.equals(stmt.getInvokeExpr().getMethod().getSignature())
				|| (!ExecutorSig.METHOD_SIG_setCorePoolSize.equals(stmt.getInvokeExpr().getMethod().getSignature())
				&& isSetCoreThreadSizePoint(stmt));
	}




}
