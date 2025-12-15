package ac.pool.point;

import soot.SootMethod;
import soot.Value;
import soot.jimple.Stmt;
//单参数值关键点扩展类
//扩展基础关键点功能，支持对任意类型参数值（包括数值、字符串等）的分析，特别适用于线程池配置参数的追踪。

//OneParaKeyPoint：精度优先，只处理可追踪的对象引用
//OneParaValueKeyPoint：覆盖优先，处理所有类型的参数值
public class OneParaValueKeyPoint extends KeyPoint{
	// 参数值（可以是任意类型的 Value）
	Value paraValue = null;
	
	public void setParaValue(Value paraValue) {
		this.paraValue = paraValue;
	}
	
	public Value getParaValue() {
		return paraValue;
	}
	
	public static OneParaValueKeyPoint newOneParaValueKeyPoint(SootMethod method, Stmt stmt, int index) {
		OneParaValueKeyPoint point = new OneParaValueKeyPoint();
//		初始化实例的信息。
		point.method = method;
		point.stmt = stmt;
		point.setCaller(getBaseCaller(stmt));
		point.paraValue =  point.getParameter(index);// 接受任意 Value 类型
		return point;
	}
	
}

