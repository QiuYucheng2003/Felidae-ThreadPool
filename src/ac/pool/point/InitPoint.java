package ac.pool.point;

import java.util.HashSet;
import java.util.Set;

import soot.Local;
import soot.SootMethod;
import soot.jimple.DefinitionStmt;
import soot.jimple.Stmt;

//初始化init关键点扩展类
//
public class InitPoint extends KeyPoint{
//	追踪在初始化过程中创建或关联的   任务对象
	Set<Local> taskLocals = new HashSet<>();// 与初始化相关的任务对象集合
//	追踪初始化过程中涉及的   核心线程对象
	Set<Local> coreThreadLocals = new HashSet<>();// 与初始化相关的核心线程对象集合


//	向初始化点添加一个任务局部变量。
//	建立初始化对象与任务对象的关联关系
	public void addTask(Local taskLocal) {
		taskLocals.add(taskLocal);
	}

//	向初始化点Init——point添加一个核心线程局部变量。
//	追踪线程池的核心线程配置
	public void addCoreThread(Local coreThreadLocal) {
		coreThreadLocals.add(coreThreadLocal);
	}
	
	public Set<Local> getCoreThreadLocals() {
		return coreThreadLocals;
	}
	
	public Set<Local> getTaskLocals() {
		return taskLocals;
	}


	public static InitPoint newInitPoint(SootMethod method, Stmt stmt) {
//		创建基础的关键点对象，初始化KeyPoint的三个基本字段信息，method、stmt、caller
		InitPoint point = new InitPoint();
		point.method = method;
		point.stmt = stmt;
//		实例方法调用的话，有明确的调用者，直接getBaseCaller（stmt）即可。
//		这里也可能返回null，因为不是可能不是实例调用。
		point.setCaller(getBaseCaller(stmt));
//		当 getBaseCaller() 返回 null（静态方法调用）且语句是赋值语句 DefinitionStm
//		将赋值语句的左值（接收返回值的变量）作为调用者
		if(point.getCaller() == null && stmt instanceof DefinitionStmt) { // r0 = Executors.newxxx()
			DefinitionStmt definitionStmt = (DefinitionStmt) stmt;
			point.setCaller(definitionStmt.getLeftOp());		
		}
		return point;
	}

}
