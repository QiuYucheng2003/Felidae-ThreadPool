package ac.pool.point;
import soot.SootField;
import soot.SootMethod;
import soot.jimple.Stmt;

//销毁关键点扩展类
//表示和处理与对象销毁相关的关键操作，特别是与HTR（Hard to Release）字段相关的销毁点。
//表示异步组件的销毁操作点，并关联到特定的 HTR 字段

public class DestroyPoint extends KeyPoint {
//	Soot字段类
	SootField htrField = null;// 关联的可销毁字段

	public SootField getHTRField() {
		return htrField;
	}

	public static DestroyPoint newDestroyPoint(SootMethod method, Stmt stmt, SootField htrField) {
		DestroyPoint point = new DestroyPoint();
		point.method = method;
		point.stmt = stmt;
//      核心：设置关联字段，调用这个方法的时候，要将相关联的字段赋值给调用实例的域。
		point.htrField = htrField;
//		设置——调用该销毁方法的调用者。
		point.setCaller(getBaseCaller(stmt));
		return point;
	}
}