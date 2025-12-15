package ac.util;

import ac.constant.AsyncTaskSig;
import ac.constant.ExecutorSig;
import ac.constant.ThreadSig;
import ac.util.InheritanceProcess.MatchType;
import soot.RefType;
import soot.SootClass;
import soot.Type;

//一个异步组件继承关系判断工具，专门用于在 Android 异步组件检测工具中判断类是否继承自特定的异步相关类。


public class AsyncInherit {
	// 判断是否继承自 Executor
	public static boolean isInheritedFromExecutor(SootClass theClass) {
		return InheritanceProcess.isInheritedFromGivenClass(theClass, ExecutorSig.CLASS_EXECUTOR);
	}
	// 判断类型是否继承自 ExecutorService
	public static boolean isInheritedFromExecutor(Type type) {
		if (type instanceof RefType) {
			return isInheritedFromExecutor(((RefType) type).getSootClass());
		}
		return false;
	}
	// 判断是否是 CallerRunsPolicy 拒绝策略
	public static boolean isInheritedCallerRunsPolicy(Type type) {
		if (type instanceof RefType) {
			return InheritanceProcess.isInheritedFromGivenClass(((RefType) type).getSootClass(), ExecutorSig.CLASS_CallerRunsPolicy);
		}
		return false;
	}

	// 判断是否继承自 Thread
	public static boolean isInheritedFromThread(SootClass theClass) {
		return InheritanceProcess.isInheritedFromGivenClass(theClass, ThreadSig.CLASS_THREAD);
	}

	public static boolean isInheritedFromExecutorService(SootClass theClass) {
		return InheritanceProcess.isInheritedFromGivenClass(theClass, ExecutorSig.CLASS_EXECUTOR_SERVICE);
	}
	public static boolean isInheritedFromExecutorService(Type type) {
		if (type instanceof RefType) {
			return isInheritedFromExecutorService(((RefType) type).getSootClass());
		}
		return false;
	}
	// 判断是否实现 Runnable 接口

	public static boolean isInheritedFromRunnable(SootClass theClass) {
		return InheritanceProcess.isInheritedFromGivenClass(theClass, ThreadSig.CLASS_RUNNABLE);
	}
	// 判断是否实现 Callable 接口

	public static boolean isInheritedFromRunnable(Type type) {
		if (type instanceof RefType) {
			return isInheritedFromRunnable(((RefType) type).getSootClass());
		}
		return false;
	}
	// 判断是否实现 Callable 接口
	public static boolean isInheritedFromCallable(SootClass theClass) {
		return InheritanceProcess.isInheritedFromGivenClass(theClass, ExecutorSig.CLASS_CALLABLE);
	}

	public static boolean isInheritedFromCallable(Type type) {
		if (type instanceof RefType) {
			return isInheritedFromCallable(((RefType) type).getSootClass());
		}
		return false;
	}


	// 判断是否继承自 Activity
	public static boolean isInheritedFromActivity(SootClass theClass) {
		return InheritanceProcess.isInheritedFromGivenClass(theClass, AsyncTaskSig.CLASS_ACTIVITY, MatchType.equal);
	}
	// 判断是否继承自 View
	public static boolean isInheritedFromView(SootClass theClass) {
		return InheritanceProcess.isInheritedFromGivenClass(theClass, AsyncTaskSig.CLASS_VIEW, MatchType.equal);
	}

	// 判断是否继承自 Fragment（支持多种 Fragment 类型）
	public static boolean isInheritedFromFragment(SootClass theClass) {
		return InheritanceProcess.isInheritedFromGivenClass(theClass, AsyncTaskSig.CLASS_FRAGMENT, MatchType.equal)
				|| InheritanceProcess.isInheritedFromGivenClass(theClass, AsyncTaskSig.CLASS_SUPPORT_FRAGMENT, MatchType.equal)
				|| InheritanceProcess.isInheritedFromGivenClass(theClass, AsyncTaskSig.CLASS_SUPPORT_FRAGMENT_V7, MatchType.equal);
	}
	// 判断是否继承自 AsyncTask
	public static boolean isInheritedFromAsyncTask(SootClass theClass) {
		return InheritanceProcess.isInheritedFromGivenClass(theClass, AsyncTaskSig.ASYNC_TASK, MatchType.equal);
	}

}
