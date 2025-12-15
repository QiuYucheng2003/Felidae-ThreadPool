package ac.pool.checker;

import java.util.List;
import java.util.Set;

import ac.pool.point.InitPoint;
import ac.pool.point.OneParaKeyPoint;
import ac.util.AsyncInherit;
import soot.Local;
import soot.Unit;
import soot.jimple.DefinitionStmt;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;
//CallerRunsChecker 适合检测 通过 setRejectedExecutionHandler() 方法
//显式设置 CallerRunsPolicy 的潜在误用情况
//根据适合什么情况的检测，去写一个测试用例

//检测线程池拒绝策略 CallerRunsPolicy 的误用情况。
//CallerRunsPolicy 是一种特殊的拒绝策略，它会让调用者线程直接执行被拒绝的任务，在某些场景下可能导致性能问题或死锁
public class CallerRunsChecker {
//	检测给定的线程池初始化点是否存在 CallerRunsPolicy 误用。
	public static boolean hasMisuse(InitPoint initPoint, Set<OneParaKeyPoint> set) {
//		包含所有设置拒绝策略的关键点；
		for(OneParaKeyPoint setRejectedExecutionHandlerPoint: set) {
//			判断 这个初始化点和set.setRejectedExecutionHandler的这个point不是同一个point！
//			并且判断，这个setRejectedExecutionHandlerPoint中存在SetCallerRunsHandlerMisuse误用；
			if(initPoint.isAliasCaller(setRejectedExecutionHandlerPoint) && isSetCallerRunsHandlerMisuse(setRejectedExecutionHandlerPoint)) {
				return true;
			}
		}
		return false;
	}

//	通过数据流分析检测拒绝策略参数是否是 CallerRunsPolicy 类型。
	private static boolean isSetCallerRunsHandlerMisuse(OneParaKeyPoint setRejectedExecutionHandlerPoint) {
//		为包含拒绝策略设置的方法构建控制流图
		UnitGraph graph = new BriefUnitGraph(setRejectedExecutionHandlerPoint.getMethod().getActiveBody());
//		获取设置拒绝策略时传入的参数局部变量
		Local handlerLocal = setRejectedExecutionHandlerPoint.getParaLocal();
//		使用 Soot 的 SimpleLocalDefs 进行定义-使用分析
//		找到所有给 handlerLocal 赋值的语句
		SimpleLocalDefs defs = new SimpleLocalDefs(graph);
		List<Unit> defsOfHandlerLocal = defs.getDefsOf(handlerLocal);
//		类型检查
		for (Unit unit : defsOfHandlerLocal) {
			if(unit instanceof DefinitionStmt) {
				DefinitionStmt definitionStmt = (DefinitionStmt) unit;
				if(AsyncInherit.isInheritedCallerRunsPolicy(definitionStmt.getRightOp().getType())) {
					return true;
				}
			}
		}
		return false;
	}
}
