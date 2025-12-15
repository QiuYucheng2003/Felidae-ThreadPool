package ac.pool.point;

import java.util.HashSet;
import java.util.Set;

import soot.Local;
import soot.PointsToSet;
import soot.RefType;
import soot.SootMethod;
import soot.Type;
import soot.jimple.Stmt;

//KeyPoint的单参数关键点扩展类，专门用于表示和处理带有一个重要参数的关键调用点。

public class OneParaKeyPoint extends KeyPoint{

	Local paraLocal = null;   // 关键参数对应的局部变量
	
	public void setParaLocal(Local paraLocal) {
		this.paraLocal = paraLocal;
	}
	
	public Local getParaLocal() {
		return paraLocal;
	}


//	分析关键参数可能指向的对象类型集合
	public Set<RefType> getParaLocalPossiableTypes() {
//		返回 paraLocal 变量可能指向的所有对象集合
		PointsToSet pts = pta.reachingObjects(paraLocal);
		Set<RefType> set = new HashSet<RefType>();
//		类型提取和过滤：
//		possibleTypes() 获取对象集合中所有可能的类型
//		只保留 RefType（引用类型），过滤掉基本类型和数组类型
		for(Type type: pts.possibleTypes()) {
			if(type instanceof RefType) {
				set.add((RefType) type);
			}
		}
		return set;
	}

//	创建单参数关键点对象，并提取指定索引的参数——>关键参数。
//	为什么这个方法，必须将指定索引的参数转为Local类型的关键参数？
//	Local 变量：可以在方法体内进行数据流分析，追踪其赋值来源
//	非Local变量：难以进行精确的指针分析和数据流追踪

//	index一般是0
	public static OneParaKeyPoint newOneParaKeyPoint(SootMethod method, Stmt stmt, int index) {
//		基础对象创建和设置
		OneParaKeyPoint point = new OneParaKeyPoint();
		point.method = method;// 设置所在方法
		point.stmt = stmt;// 设置语句
		point.setCaller(getBaseCaller(stmt));// 提取调用者对象
//		参数提取和验证
		if (point.getParameter(index) instanceof Local) {//检查这个value是不是Local局部变量类型
			point.paraLocal = (Local) point.getParameter(index);
		} else {
			return null;
		}
		return point;
	}
}
