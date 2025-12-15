/* AsyncDetecotr - an Android async component misuse detection tool
 * Copyright (C) 2018 Baoquan Cui
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */
package ac.pool.checker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.GotoStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;

/**
 * The loop summary of method
 * 
 * @author Baoquan Cui
 * @version 1.0
 */

// 循环分析（loop analysis），专门用于分析多线程环境中的方法循环结构，特别是检查循环是否可以被正确取消。
// 确保循环能够响应线程中断请求
public class RunMethod extends MethodLoopAnalyzer{
//	方法摘要缓存，避免重复分析相同的方法，相同的方法缓存一个就行。
//	key——方法的完整签名；value——对应的RunMethod分析结果。
	protected static final Map<String, RunMethod> doInBackgroundMethods = new HashMap<>();
//  存储所有可能导致循环取消的语句单元，包含 GotoStmt 和其他可能改变循环控制流的语句
//	这些最后用于遍历判断这些语句单元unit，是否有取消语句
	protected Set<Unit> mCancelUnitList = new HashSet<>();

	public RunMethod(SootMethod methodUnderAnalysis) {
//		传递要分析的SootMethod，初始化基础循环分析
		super(methodUnderAnalysis);
//		启动完整的分析方法，生成循环摘要
		generation();
	}

//返回分析过程中发现的所有可能取消循环的语句单元
	public Set<Unit> getCancelledUnits() {
		return mCancelUnitList;
	}


// 返回一个布尔值，判断是否所有的loop循环都有这个取消的机制。
	public boolean isAllLoopCancelled() {
//		 第一部分：处理取消单元（mCancelUnitList）
//		 标记循环取消状态，检查方法中的所有循环是否都有适当的取消机制。
		for (Unit unit : mCancelUnitList) {
			UnitInfo unitInfo = getUnitInfo(unit);
			if (unit instanceof GotoStmt) {//如果是go to的语句，也是取消机制的表现之一
				GotoStmt gotoStmt = (GotoStmt) unit;
				Unit targetUnit = gotoStmt.getTarget(); // 得到跳转目标的那个unit
				UnitInfo targetInfo = getUnitInfo(targetUnit);
				if (unitInfo.mLoopHeaderUnit != null) {//循环头不为空
					UnitInfo headerUnitInfo = getUnitInfo(unitInfo.mLoopHeaderUnit);
//					判断本语句unitInfo和目标跳转语句targetInfo的循环头是否是一样的？、
//					如果跳转目标属于不同的循环，说明当前循环可以被退出
					 boolean isDifferent= (targetInfo.mLoopHeaderUnit != unitInfo.mLoopHeaderUnit);
					headerUnitInfo.isCancelled =isDifferent;
				}
			}
//			如果取消单元本身就是循环头，直接标记为已取消
			if (mLoopHeaderList.contains(unit)) {
				UnitInfo headerUnitInfo = getUnitInfo(unit);
				headerUnitInfo.isCancelled = true;
			}
		}
//		遍历所有循环头，检查是否都被标记为可取消，只要有一个循环不可取消，就返回false
		for (Unit unit : mLoopHeaderList) {
			UnitInfo unitInfo = getUnitInfo(unit);
			if (!unitInfo.isCancelled) {
				return false;
			}
		}
		return true;
	}




//	整合被调用方法的循环分析结果；
//	收集Goto语句 - 识别潜在的循环退出点
	protected void afterLoopAnalysis() {
		for (Unit unit : mUnitGraph) {//遍历控制流图中的每一个语句单元unit.
//			InvokeExpr theExpr = ((Stmt) unit).containsInvokeExpr() ? ((Stmt) unit).getInvokeExpr() : null;
			InvokeExpr theExpr = null;
//			if (unit instanceof Stmt) {
////				 获取unit的调用表达式
//				theExpr = ((Stmt) unit).getInvokeExpr();
//			}
			// 修复：添加调用表达式检查
			if (unit instanceof Stmt) {
				Stmt stmt = (Stmt) unit;
				if (stmt.containsInvokeExpr()) {
					theExpr = stmt.getInvokeExpr();
				}
			}

			// Process the summary of invoked method
//			处理当前方法中调用的其他方法，整合它们的循环分析结果
//			SootMethod method = theExpr.getMethod();是一个SootMethod类的实例对象。
			if (theExpr != null && theExpr.getMethod().hasActiveBody()) {
				//获得分析方法的方法签名
				String key = theExpr.getMethod().getSignature();
				//通过该方法签名去map中获得对应的value
				RunMethod currentMethodSummary = doInBackgroundMethods.get(key);
				if (currentMethodSummary != null) {//找到了被调用方法的分析摘要，将其结果合并到当前方法中
					this.mLoopHeaderList.addAll(currentMethodSummary.mLoopHeaderList);//合并循环头信息
					this.mCancelUnitList.addAll(currentMethodSummary.mCancelUnitList);//合并取消单元信息
				}
			}
				// goto语句包括：
				//break 语句
				//continue 语句
				//显式的 goto 标签跳转
				//任何改变控制流的无条件跳转
			if (unit instanceof GotoStmt) {
				mCancelUnitList.add(unit);//将所有Goto语句添加到取消单元列表中
			}
		}
	}

}
