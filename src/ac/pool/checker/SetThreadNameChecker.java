package ac.pool.checker;

import java.util.HashSet;
import java.util.Set;
import ac.constant.ThreadSig;
import ac.pool.point.InitPoint;
import ac.pool.point.KeyPoint;
import ac.pool.point.OneParaKeyPoint;
import soot.Local;
import soot.RefType;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.jimple.ReturnStmt;

//线程命名检查器，专门用于检测线程池中是否正确地设置了线程名称。
public class SetThreadNameChecker {

//	initPoint: 线程池的初始化点
//  setFactoryPoints: 设置ThreadFactory的调用点集合
//  setNameKeyPoints: 设置线程名称的调用点集合（如thread.setName()）
	public static boolean hasMisuse(InitPoint initPoint, Set<OneParaKeyPoint> setFactoryPoints,
			Set<KeyPoint> setNameKeyPoints) {
//		先对setName方法调用点的判断；
		for (KeyPoint setNameKeyPoint : setNameKeyPoints) {
//			说明，setName的对象和initPoint的对象是同一个对象；
			if (setNameKeyPoint.isAliasCaller(initPoint)) {
				return false;
			}
		}
//		ThreadFactory线程命名检查， 遍历所有的ThreadFactory
		for (OneParaKeyPoint setFactoryPoint : setFactoryPoints) {
			if (setFactoryPoint.isAliasCaller(initPoint)) {
//				对于相关的ThreadFactory，获取其创建的核心线程
				Set<Local> coreThreads = getCoreThreadsFromThreadFactory(setFactoryPoint.getParaLocal());
				for (Local coreThread : coreThreads) {
//					检查这些线程是否被设置了名称，如果找到任何命名调用，返回false
					for (KeyPoint setNameKeyPoint : setNameKeyPoints) {
						if (setNameKeyPoint.isAliasCaller(coreThread)) {
							return false;
						}
					}
				}
			}
		}
//		如果以上两种均为找到别名调用，则说明存在 线程名称误用。
		return true;
	}


//	 获取ThreadFactory创建的线程
	private static Set<Local> getCoreThreadsFromThreadFactory(Local threadFactoryLocal) {
		Set<Local> locals = new HashSet<>();
//		使用指针分析（PTA）获取ThreadFactory局部变量可能指向的对象类型
		for (Type taskPossiableType : KeyPoint.pta.reachingObjects(threadFactoryLocal).possibleTypes()) {
			if(taskPossiableType instanceof RefType) {//如果是引用类型
				RefType refType = (RefType) taskPossiableType;
//				查找ThreadFactory的newThread方法，就是创建Thread的方法。
				SootMethod newThreadMethod = refType.getSootClass().getMethodUnsafe(ThreadSig.METHOD_SUBSIG_NEWTHREAD);
//				遍历newThread方法体中的所有语句，查找返回语句（ReturnStmt，提取返回的线程局部变量
				if (newThreadMethod != null && newThreadMethod.hasActiveBody()) {
					for (Unit unit : newThreadMethod.getActiveBody().getUnits()) {
						if (unit instanceof ReturnStmt) {//查找返回语句，ReturnStmt
							ReturnStmt returnStmt = (ReturnStmt) unit;
							if (returnStmt.getOp() instanceof Local) {
//								提取返回的线程局部变量
								locals.add((Local) returnStmt.getOp());
							}
						}
					}
				}
			}
		}
		return locals;
	}
}
