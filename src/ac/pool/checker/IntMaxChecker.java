package ac.pool.checker;

import java.util.List;

import ac.pool.point.OneParaValueKeyPoint;
import soot.Local;
import soot.Unit;
import soot.jimple.DefinitionStmt;
import soot.jimple.IntConstant;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;

//线程池参数被设置为 Integer.MAX_VALUE 的误用。
public class IntMaxChecker {

//	该方法的分支1，判断point.getParaValue是否为直接整形常量，然后在判断是否为Integer.MAX_VALUE
//	分支2判断point.getParaValue是否为局部变量。紧接着在进行判断。
	public static boolean hasMaxIntegerSizeMisuse(OneParaValueKeyPoint point){
//		直接使用 Integer.MAX_VALUE 作为参数值的情况。
		if(point.getParaValue() instanceof IntConstant) {
			IntConstant intConstant = (IntConstant) point.getParaValue();
			if(intConstant.value == Integer.MAX_VALUE) {
				return true;
			}
		}

//		通过变量间接传递 Integer.MAX_VALUE 的情况。
		else if(point.getParaValue() instanceof Local)
		{
			Local local = (Local) point.getParaValue();
//			基于方法体创建单位图(UnitGraph)，表示方法的控制流
			UnitGraph graph = new BriefUnitGraph(point.getMethod().getActiveBody());
//			构建局部变量的定义-使用链
			SimpleLocalDefs defs = new SimpleLocalDefs(graph);
//			对于上述获得的使用链，返回所有定义该局部变量local的语句
			List<Unit> defsOfHandlerLocal = defs.getDefsOf(local);
			for (Unit unit : defsOfHandlerLocal) {
				if(unit instanceof DefinitionStmt) {
					DefinitionStmt definitionStmt = (DefinitionStmt) unit;
					if(definitionStmt.getRightOp() instanceof IntConstant) {
						IntConstant intConstant = (IntConstant) definitionStmt.getRightOp();
						if(intConstant.value == Integer.MAX_VALUE) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

}
