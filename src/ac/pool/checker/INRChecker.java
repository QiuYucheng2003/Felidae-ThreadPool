package ac.pool.checker;

import java.util.ArrayList;
import java.util.Set;

import ac.constant.ThreadSig;
import ac.pool.point.KeyPoint;
import ac.util.AsyncInherit;
import soot.SootClass;
import soot.SootMethod;
// INR (Interrupt No Respond）中断不响应，在循环中没有中断检查机制。
//INRChecker是一个中断检查器
//循环中断检查 —— 检查循环是否包含适当的  中断检查机制，检查isInterrupt
//Runnable实现验证 —— 验证实现Runnable接口的类是否正确处理中断
//中断误用检测 —— 检测不恰当的中断调用关系
public class INRChecker {

//	isAllLoopCancelled方法——判断是否所有的loop循环都有这个取消的机制。这就也达到了目的之一（循环中断检查）
//	这个方法是针对于 SootMethod的，是去判断一个方法。
	public static boolean hasInterruptCheck(SootMethod sootMethod) {
		return new RunMethod(sootMethod).isAllLoopCancelled();
	}
//  这个方法针对于 SootClass的，是一个类级别的终端检查方法。
//	检查实现Runnable接口的类中的所有run方法是否都有适当的中断检查。
	public static boolean hasInterruptCheck(SootClass sootClass) {
		boolean hasInterruptCheck = true;
		if (AsyncInherit.isInheritedFromRunnable(sootClass)) {//判断当前这个类是不是实现Runnable接口
//		如果这个类是实现Runnable的，那么获取这个类中所有的方法，存储在ArrayList列表中，这个列表是methods；
			ArrayList<SootMethod> methods = new ArrayList<>(sootClass.getMethods());
			for (SootMethod sootMethod : methods) {//遍历methods中的每一个方法
//				判断这个方法是不是是run方法（就是线程启动的方法），并且该方法有参数。
				if (ThreadSig.METHOD_SUBSIG_RUN.equals(sootMethod.getSubSignature())
						&& sootMethod.getParameterCount() == 0)
				{
//					调用上面对于单个方法的中断检查方法。
//					只要有一个是false就说明这个类中不是所有方法都有中断循环检查的
					hasInterruptCheck = hasInterruptCheck(sootMethod);
					if (!hasInterruptCheck)
					{
						return hasInterruptCheck;
					}
				}
			}
		}
		return hasInterruptCheck;
	}

//	中断误用检测，有初始点和中断点
//	检测是否存在不恰当的中断调用关系，即检查初始点是否通过别名关系调用了中断点。
//	这个方法目前还没有用。
	public static boolean hasINRMisuse(KeyPoint initialPoint, Set<KeyPoint> interruptPoints) {
		for (KeyPoint point : interruptPoints) {//遍历所有终端点。
			if (initialPoint.isAliasCaller(point)) {//基于指针分析(Points-To Analysis)确定对象引用关系
				return true;
			}
		}
		return false;
	}
}

//============ hasINRMisuse的误用例子
// 在分析时：
// initialPoint = TaskManager.startTask()中的worker.start()
// interruptPoint = worker.interrupt()
// 如果这两个点通过别名分析关联，则检测为误用

// public class TaskManager {
//    public void startTask() {
//        Thread worker = new Thread(new Task());
//        worker.start();
//        // 错误：立即中断刚启动的线程
//        worker.interrupt();  // interruptPoint
//    }
//}