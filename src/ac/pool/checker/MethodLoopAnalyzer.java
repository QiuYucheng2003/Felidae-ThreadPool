package ac.pool.checker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import soot.SootMethod;
import soot.Unit;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;

/**
 * The loop summary of method
 * 
 * @author Baoquan Cui
 * @version 1.0
 */
//基于深度优先搜索的循环检测算法，用于识别方法中的循环结构。
//循环检测 - 识别方法中的所有循环结构
//控制流分析 - 分析方法的基本块和控制流关系
public class MethodLoopAnalyzer {
//	存储所有基本语句（Unit）的分析信息
	protected List<UnitInfo> mUnitInfos = new ArrayList<UnitInfo>();
//  存储所有循环头（循环的起始位置）( while 、for循环开头的那些 unit 语句 ）
	protected Set<Unit> mLoopHeaderList = new HashSet<>();
//  方法的控制流图
	protected UnitGraph mUnitGraph = null;
	/**
	 *  unit存储实际的Soot语句对象: 可能是赋值语句、方法调用、条件跳转等
	 *  分析的基本单位，每个被分析的语句都有一个对应的UnitInfo.
	 */
	static class UnitInfo {
		protected Unit mUnit = null;         //// 对应的基本块
		protected boolean visited = false;
		protected int mDeepFirstSearchPathPosition = 0;// DFS路径位置，记录在深度优先搜索中的遍历序号
		protected Unit mLoopHeaderUnit = null;// 指向控制该语句的循环头
		protected boolean isCancelled = false;// 是否可取消（用于上层分析）


		//		public boolean equals(Object obj) {
//			boolean result = mUnit.equals(obj instanceof UnitInfo ? ((UnitInfo) obj).mUnit : obj);
//			return result;
//		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof UnitInfo)) {
				return mUnit.equals(obj);
			}
			UnitInfo other = (UnitInfo) obj;
			return mUnit.equals(other.mUnit);
		}

		@Override
		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append("unit-->" + mUnit);
			sb.append("\n");
			sb.append("iloop_header-->" + mLoopHeaderUnit);
			sb.append("\n");
			sb.append("DFEP_pos-->" + mDeepFirstSearchPathPosition);
			return sb.toString();
		}

	}


//	为有活动体的方法创建控制流图。
	public MethodLoopAnalyzer(SootMethod methodUnderAnalysis) {
		if (methodUnderAnalysis.hasActiveBody()) {
			mUnitGraph = new BriefUnitGraph(methodUnderAnalysis.getActiveBody());
		}
	}
//  获取所有循环的起始位置获得所有循环头。
	public Set<Unit> getLoopStartUnits() {
		return mLoopHeaderList;
	}

//	执行完generation()后，系统会生成：
//	mLoopHeaderList: 包含所有识别出的循环头语句
//	---------什么是循环头---------
//	while (i < 10)  for(int i=0；i<10;i++)类似这种，就是循环头
//	循环头是循环的入口点；所有循环迭代都必须经过循环头；循环头控制着循环的继续或退出

//	mUnitInfos: 每个语句单元的循环分析信息
//	触发回调: afterLoopAnalysis()执行后续处理
	protected void generation() {//generation 是真正的控制方法。
		if (mUnitGraph == null) {//安全检查，确保控制流图已正确初始,如果没有控制流图，无法进行循环分析
			return;
		}
//		深度优先遍历: 从每个头节点开始DFS遍历
		//获取控制流图的入口节点（通常是方法的第一个语句）
		for (Unit head : mUnitGraph.getHeads()) {
			traverUnitGraph(head, 0);
		}
//		收集循环头: 从分析结果中提取所有循环头
		for (UnitInfo unitInfo : mUnitInfos) {
//			从每个UnitInfo的mLoopHeaderUnit字段提取到全局的mLoopHeaderList,建立完整的循环头映射表。
			mLoopHeaderList.add(unitInfo.mLoopHeaderUnit);
		}
		afterLoopAnalysis();

	}
//	在 RunMethod 中被重写，用于收集取消单元和方法调用信息。
	protected void afterLoopAnalysis() {

	}


//	实现了基于深度优先搜索的循环检测，用于在控制流图中识别循环头节点
	private Unit traverUnitGraph(Unit b0, int deepFirstSearchPathPosition) {
		// return: innermost loop header of b0
//		初始化一些b0的信息。

//		得到一个unitInfo对象，如果mUnitInfos有，直接返回，如果没有则创造一个返回。
		UnitInfo unitInfo = getUnitInfo(b0);
		unitInfo.visited = true;
		unitInfo.mDeepFirstSearchPathPosition = deepFirstSearchPathPosition;
		for (Unit b : mUnitGraph.getSuccsOf(b0)) {// 遍历后续所有节点，UnitGraph类型自带的方法getSuccOf
			UnitInfo unitInfob = getUnitInfo(b);
//			情况1:未访问的后继结点。
			if (!unitInfob.visited) {
				// case(A) 遇到未访问的后继节点，递归DFS，然后传播循环头信息
				Unit nh = traverUnitGraph(b, deepFirstSearchPathPosition + 1);
				tagLoopHeader(b0, nh);
			}
			else
			{
				if (unitInfob.mDeepFirstSearchPathPosition > 0) {
					// case(B) 后继节点已在当前DFS路径中，将后继节点标记为循环头
					unitInfob.visited = true;
					tagLoopHeader(b0, b);
				}
				else if (unitInfob.mLoopHeaderUnit == null) {
					// case(C) 节点已访问但尚未分配循环头
				}
				else {
					Unit h = unitInfob.mLoopHeaderUnit;
					UnitInfo unitInfoH = getUnitInfo(h);
					if (unitInfoH.mDeepFirstSearchPathPosition > 0) {
						// case(D) 后继节点在活动循环中
//						继承相同的循环头
						tagLoopHeader(b0, h);
					} else {
						// case(E) re-entry 处理嵌套循环的重新进入，向上查找活动的循环头
						while (unitInfoH.mLoopHeaderUnit != null) {
							unitInfoH = getUnitInfo(unitInfoH.mLoopHeaderUnit);
							if (unitInfoH.mDeepFirstSearchPathPosition > 0) {
								tagLoopHeader(b0, h);
								break;
							}
						}
					}
				}
			}
		}
		unitInfo.mDeepFirstSearchPathPosition = 0;
		return unitInfo.mLoopHeaderUnit;
	}

//	循环头标记算法，用于建立节点与循环头之间的层次关系
//	确保每个节点指向最内层的循环头，处理嵌套循环情况，避免循环引用
	private void tagLoopHeader(Unit b, Unit h) {
//		DFS位置值：值越小表示在DFS树中越"浅"（越接近根节点）
//		循环头链：每个节点通过mLoopHeaderUnit指向其所属循环的头节点
		// b: 当前节点, h: 候选循环头节点
		if (b == h || h == null) {  //排除自循环和空循环
			return;
		}
		UnitInfo cur1 = getUnitInfo(b);
		UnitInfo cur2 = getUnitInfo(h);
//		当前节点已有循环头，需要处理循环头链的合并
		while (cur1.mLoopHeaderUnit != null) {
			UnitInfo ih = getUnitInfo(cur1.mLoopHeaderUnit);
			if (ih == cur2) {
				return;
			}
//			情况A：当前循环头比候选循环头"更深"（DFS位置值更小）
			if (ih.mDeepFirstSearchPathPosition < cur2.mDeepFirstSearchPathPosition) {
				cur1.mLoopHeaderUnit = cur2.mUnit;// 更新为更浅的循环头
				cur1 = cur2;// 移动cur1到候选循环头
				cur2 = ih;// 移动cur2到原循环头
			} else {
				cur1 = ih;// 继续向上遍历循环头链
			}
		}
//		当cur1.mLoopHeaderUnit == null时，直接建立关联
		cur1.mLoopHeaderUnit = cur2.mUnit;
	}

//	获取或创建一个UnitInfo的分析信息，返回一个UnitInfo对象；
	protected UnitInfo getUnitInfo(Unit unit) {
		for (UnitInfo unitInfo : mUnitInfos) {
			if (unitInfo.mUnit.equals(unit)) {
				return unitInfo;
			}
		}
		UnitInfo unitInfo = new UnitInfo();
		unitInfo.mUnit = unit;
		mUnitInfos.add(unitInfo);
		return unitInfo;
	}



}
