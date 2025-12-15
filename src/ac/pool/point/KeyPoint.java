package ac.pool.point;

import java.util.HashSet;
import java.util.Set;

import soot.Local;
import soot.PointsToAnalysis;
import soot.PointsToSet;
import soot.RefType;
import soot.Scene;
import soot.SootMethod;
import soot.Type;
import soot.Value;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.spark.sets.EmptyPointsToSet;
import soot.jimple.toolkits.pointer.FullObjectSet;

//作为所有关键点类型的基类，提供了统一的方法调用点caller分析框架
//特别是对调用对象（caller）的指针分析和类型推断。
//为所有类型的关键点提供共同的分析接口

public class KeyPoint {
	// 指针分析器
	public static final PointsToAnalysis pta = Scene.v().getPointsToAnalysis();
	SootMethod method = null;// 关键点所在的方法
	Stmt stmt = null; // 关键点语句
	private Value caller = null;// 方法调用的对象（如 thread.start() 中的 thread）

//	获取调用表达式中的第 index 个参数值。

//	返回的是lambda创建的匿名类实例的Local引用
	protected Value getParameter(int index) {
		return stmt.getInvokeExpr().getArg(index);
	}
//	对局部变量进行指针分析，获取该变量可能指向的对象集合。
	protected PointsToSet reachingObjects(Value value) {
		if(value instanceof Local) {
			return  pta.reachingObjects((Local) value);
		}
		return EmptyPointsToSet.v();
	}

//	获取调用者（caller）可能指向的对象集合。
	public PointsToSet getCallerPointsToSet() {
		return reachingObjects(getCaller());
	}
//	分析调用者（caller）可能的具体类型（RefType是class或者interface），返回 RefType 集合。
	public Set<RefType> getCallerPossibleType() {
		PointsToSet pts = getCallerPointsToSet();
		Set<RefType> set = new HashSet<RefType>();
		for(Type type: pts.possibleTypes()) {
			if(type instanceof RefType) {
				set.add((RefType) type);
			}
		}
		return set ;
	}
//	检查两个关键点的调用者是否存在别名关系（是否可能指向同一个对象）。
//	一个参数是局部变量类型；一个参数是KeyPoint类型；
//	这个别名分析，精度太低了。

//	由于指针分析失效，r0 和 r2 都被认为是同一个 FullObjectSet
//	所以 hasNonEmptyIntersection 总是返回 true，导致检测器认为它们操作的是同一个对象。
	public boolean isAliasCaller(KeyPoint otherKeyPoint) {
//     如果指针分析失效，回退到基于变量名的简单分析
		PointsToSet thisPTS = getCallerPointsToSet();
		PointsToSet otherPTS = otherKeyPoint.getCallerPointsToSet();

// 检查是否是 FullObjectSet（指针分析失效的情况）
		if (thisPTS instanceof FullObjectSet || otherPTS instanceof FullObjectSet) {
			// 回退到基于变量名的分析
			Value thisCaller = this.getCaller();
			Value otherCaller = otherKeyPoint.getCaller();
			if (thisCaller instanceof Local && otherCaller instanceof Local) {
				Local thisLocal = (Local) thisCaller;
				Local otherLocal = (Local) otherCaller;
				// 如果变量名相同，认为是同一个对象
				return thisLocal.getName().equals(otherLocal.getName());
			}
			return false; // 保守策略：不同变量名认为不是别名
		}
		//检查两个指针是否可能指向相同的对象
		boolean result = thisPTS.hasNonEmptyIntersection(otherPTS);
		return result;
	}

	
	public boolean isAliasCaller(Local local) {//这个方法也许是导致误报的情况之一的原因，要后面结合Check详细分析；
		return getCallerPointsToSet().hasNonEmptyIntersection(pta.reachingObjects(local));
	}


//	创建关键点对象、提取调用者基础对象。
	public static KeyPoint newPoint(SootMethod method, Stmt stmt) {
		KeyPoint keyPoint = new KeyPoint();
		keyPoint.method = method;
		keyPoint.stmt = stmt;
		keyPoint.setCaller(getBaseCaller(stmt));
		return keyPoint;
	}
	
	public static Value getBaseCaller(Stmt stmt) {
		return getBaseCaller(stmt.getInvokeExpr());
	}
	
	public static Value getBaseCaller(InvokeExpr invokeExpr) {
		if(invokeExpr instanceof InstanceInvokeExpr) {
			InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) invokeExpr;
			return instanceInvokeExpr.getBase();
		}
		return null;
	}







	public SootMethod getMethod() {
		return method;
	}

	public void setMethod(SootMethod method) {
		this.method = method;
	}

	public void setStmt(Stmt stmt) {
		this.stmt = stmt;
	}
	public Stmt getStmt() {
		return stmt;
	}
	public Value getCaller() {
		return caller;
	}

	public void setCaller(Value caller) {
		this.caller = caller;
	}

}