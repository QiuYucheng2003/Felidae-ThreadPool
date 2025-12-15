//package ac.pool.checker;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.Map;
//import java.util.Set;
//
//import ac.pool.PoolMain;
//import destructible.DestructibleIdentify;
//import soot.Type;
//import soot.AnySubType;
//import soot.Body;
//import soot.Local;
//import soot.PointsToAnalysis;
//import soot.PointsToSet;
//import soot.RefType;
//import soot.Scene;
//import soot.SootClass;
//import soot.SootField;
//import soot.SootMethod;
//import soot.Unit;
//import soot.Value;
//import soot.jimple.DefinitionStmt;
//import soot.jimple.FieldRef;
//import soot.jimple.InvokeExpr;
//import soot.jimple.ParameterRef;
//
//// Hard to Release
//public class HTRChecker {
//
////	map中有该type对应的sootclass 并且 按照这个sootclass去取map中的value不为空，说明有对应的set，该类中有可销毁的字段。
//	public static boolean hasHTRMisuse(RefType type) {
//		return destructibleSootFieldMap.containsKey(type.getSootClass())
//				&& !destructibleSootFieldMap.get(type.getSootClass()).isEmpty();
//	}
//
////	destructibleSootFieldMap 可销毁的字段map和set
//	public final static Map<SootClass, Set<SootField>> destructibleSootFieldMap = new HashMap<>();
//	public final static HashSet<SootField> destructibleSootFields = new HashSet<>();
//
//	public static void init() {
//		Set<SootClass> sootClasses = new HashSet<SootClass>();//获取所有应用类
//		sootClasses.addAll(Scene.v().getApplicationClasses());
//		for (SootClass currentClass : sootClasses) {
////			为每个class类在映射中创建空集合
//			destructibleSootFieldMap.put(currentClass, new HashSet<>());
//			reachingDestructibleObject(currentClass);
//		}
//	}
//
////	分析指定类中的字段赋值，识别哪些字段被赋值为可销毁对象
//	private static void reachingDestructibleObject(SootClass sootClass) {
////		对于每一个类，通过getMethods获得类中的方法，然后去遍历类中的所有的方法。
//		ArrayList<SootMethod> methods = new ArrayList<>(sootClass.getMethods());
//		for (SootMethod sootMethod : methods) {
//			if (sootMethod.hasActiveBody()) {//判断要有activeBody
////				如果当前分析的是APK文件，并且指定的方法不在可达方法集合中，那么进行下一次循环，因为都是不可达方法了
//				if (PoolMain.isApk() && !Scene.v().getReachableMethods().contains(sootMethod)) {
//					continue;
//				}
////				获得具体的方法体body（apk文件且指定的方法在可达方法中）
//				Body body = sootMethod.getActiveBody();
//				for (Unit unit : body.getUnits()) {//对方法体中的每个语句unit进行遍历
//					if (unit instanceof DefinitionStmt) {//如果是赋值语句
//						DefinitionStmt definitionStmt = (DefinitionStmt) unit;
////						问题： 你的检测器只分析——字段赋值，构造函数参数赋值未被检测；
//						if (definitionStmt.getLeftOp() instanceof FieldRef)
//						{	//如果左边是字段引用，说明这是字段引用的赋值
//							FieldRef fieldRef = (FieldRef) definitionStmt.getLeftOp();
//							if (fieldRef.getField() != null && fieldRef.getField().isStatic()) {
//								//如果左边是字段引用，并且是static静态的话，就跳过这句unit，遍历下一句unit
//								//静态字段通常不涉及对象销毁管理
//								continue;
//							}
////							只分析当前类中声明的字段（排除继承的字段），不分析父类中的字段。
//							//通过字段获取DeclaringClass来获取声明类。
//							if (fieldRef.getField().getDeclaringClass().getName().equals(sootClass.getName()))
//							{
////								分析赋值右侧的值
//								Value rightOp = definitionStmt.getRightOp();//获取右操作数
//								PointsToAnalysis pointsToAnalysis = Scene.v().getPointsToAnalysis();//获取指针分析器
//								PointsToSet pointsToSet = null;//存储rightOp可能指向的所有内存对象
//								Set<Type> types = new HashSet<>();//收集rightOp可能指向对象的所有类型
//								if (rightOp instanceof Local) {//局部变量的话如 x = y 中的 y用
//									// 指针分析器去分析，可能指向的所有内存对象
//									pointsToSet = pointsToAnalysis.reachingObjects((Local) rightOp);
//								}
//								else if (rightOp instanceof FieldRef)
//								{//字段引用，如 x = Class.staticField）
//									FieldRef fieldRef2 = (FieldRef) rightOp;
////									静态的字段引用
//									if (fieldRef2.getField() != null && fieldRef2.getField().isStatic()) {
//										pointsToSet = pointsToAnalysis.reachingObjects(fieldRef2.getField());
//									}
//								}
////								处理方法调用，如 x = obj.method()
//								else if (rightOp instanceof InvokeExpr) {
//									InvokeExpr invokeExpr = (InvokeExpr) rightOp;
////									返回类型中，将该方法所返回的可能性类型全存储了。
//									types.add(invokeExpr.getMethod().getReturnType());
//								}
////								经过上面三个if-else后，如果内存对象集合和类型存储集合都不为空的话，将对象涉及到的类型全部放入内存对象
//								if (pointsToSet != null && pointsToSet.possibleTypes() != null) {
//									types.addAll(pointsToSet.possibleTypes());
//								}
////								分析Type中的类型
//								Set<RefType> refTypes = new HashSet<>();
//								for (Type type : types) {
////									引用类型
//									if (type instanceof RefType) {
//										refTypes.add((RefType) type);
//									}
////									某个类型及其所有子类型，getBase()获取基类型
//									else if (type instanceof AnySubType)
//									{
//										refTypes.add(((AnySubType) type).getBase());
//									}
//								}
////								对于引用类型再次遍历——识别和收集可销毁字段
//								for (RefType refType : refTypes) {
////									每个引用类型获取其类class。
//									SootClass fieldClass = refType.getSootClass();
////									判断该类是否为"可销毁的"，两个fieldClass要检查的类和当前上下文类
//									if (DestructibleIdentify.isDestructibleClass(fieldClass, fieldClass)) {
////										按类分组记录可销毁字段
//										destructibleSootFieldMap.get(sootClass).add(fieldRef.getField());
////										记录所有可销毁字段
//										destructibleSootFields.add(fieldRef.getField());
//									}
//								}
//
//							}
//						}
//					}
//				}
//			}
//		}
//	}
//}

package ac.pool.checker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ac.pool.PoolMain;
import ac.util.AsyncInherit; // 假设你需要用到这个工具类来判断是否继承自 Runnable/Callable
import destructible.DestructibleIdentify;
import soot.Type;
import soot.AnySubType;
import soot.Body;
import soot.Local;
import soot.PointsToAnalysis;
import soot.PointsToSet;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;
import soot.jimple.InvokeExpr;

// Hard to Release
public class HTRChecker {

	// 【修改点 1】修改判定逻辑，增加过滤
	public static boolean hasHTRMisuse(RefType type) {
		SootClass sootClass = type.getSootClass();

		// 1. 基础检查：如果该类没有记录任何可销毁字段，直接返回 false
		if (!destructibleSootFieldMap.containsKey(sootClass)
				|| destructibleSootFieldMap.get(sootClass).isEmpty()) {
			return false;
		}

		// 2. 【核心修改】通用过滤逻辑：忽略“临时任务包装器”
		// 如果这个类本身就是一个 Runnable/Callable 的包装器（Lambda或内部类），
		// 那么它持有字段通常是为了在 run() 中使用，而不是长期持有资源，属于安全模式。
		if (isTransientTaskWrapper(sootClass)) {
			return false;
		}

		return true;
	}

	/**
	 * 【新增方法】判断一个类是否为临时的任务包装器
	 * 特征：
	 * 1. 类名包含 "$"，说明是内部类、匿名类或 Lambda 表达式编译后的合成类。
	 * 2. 该类实现了 Runnable 或 Callable 接口。
	 */
	private static boolean isTransientTaskWrapper(SootClass sootClass) {
		// 1. 检查类名特征
		// Lambda 表达式通常编译为 ClassName$lambda$...
		// 匿名内部类通常编译为 ClassName$1...
		// 静态内部类通常编译为 ClassName$InnerName...
		if (!sootClass.getName().contains("$")) {
			return false;
		}

		// 2. 检查是否为任务类 (Runnable / Callable)
		// 这里复用了你项目中已有的 AsyncInherit 工具类逻辑，或者直接检查接口
		// 如果 PoolMain 中有 AsyncInherit，建议直接调用：
		if (AsyncInherit.isInheritedFromRunnable(sootClass)
				|| AsyncInherit.isInheritedFromCallable(sootClass)) {
			return true;
		}

		// 如果没有 AsyncInherit，可以使用以下原生 Soot 逻辑替代：
        /*
        for (SootClass iface : sootClass.getInterfaces()) {
            if (iface.getName().equals("java.lang.Runnable") ||
                iface.getName().equals("java.util.concurrent.Callable")) {
                return true;
            }
        }
        if (sootClass.hasSuperclass()) {
            String superName = sootClass.getSuperclass().getName();
            // 检查是否继承自特定的任务基类（视具体情况而定）
             if (superName.equals("java.util.concurrent.FutureTask")) return true;
        }
        */

		return false;
	}

	//  destructibleSootFieldMap 可销毁的字段map和set
	public final static Map<SootClass, Set<SootField>> destructibleSootFieldMap = new HashMap<>();
	public final static HashSet<SootField> destructibleSootFields = new HashSet<>();

	public static void init() {
		Set<SootClass> sootClasses = new HashSet<SootClass>();//获取所有应用类
		sootClasses.addAll(Scene.v().getApplicationClasses());
		for (SootClass currentClass : sootClasses) {
//        为每个class类在映射中创建空集合
			destructibleSootFieldMap.put(currentClass, new HashSet<>());
			reachingDestructibleObject(currentClass);
		}
	}

	//  ... (reachingDestructibleObject 方法保持不变) ...
	//  分析指定类中的字段赋值，识别哪些字段被赋值为可销毁对象
	private static void reachingDestructibleObject(SootClass sootClass) {
		// 保持原样，无需修改
		// ... 代码省略，与你提供的一致 ...
		ArrayList<SootMethod> methods = new ArrayList<>(sootClass.getMethods());
		for (SootMethod sootMethod : methods) {
			if (sootMethod.hasActiveBody()) {
				if (PoolMain.isApk() && !Scene.v().getReachableMethods().contains(sootMethod)) {
					continue;
				}
				Body body = sootMethod.getActiveBody();
				for (Unit unit : body.getUnits()) {
					if (unit instanceof DefinitionStmt) {
						DefinitionStmt definitionStmt = (DefinitionStmt) unit;
						if (definitionStmt.getLeftOp() instanceof FieldRef)
						{
							FieldRef fieldRef = (FieldRef) definitionStmt.getLeftOp();
							if (fieldRef.getField() != null && fieldRef.getField().isStatic()) {
								continue;
							}
							if (fieldRef.getField().getDeclaringClass().getName().equals(sootClass.getName()))
							{
								Value rightOp = definitionStmt.getRightOp();
								PointsToAnalysis pointsToAnalysis = Scene.v().getPointsToAnalysis();
								PointsToSet pointsToSet = null;
								Set<Type> types = new HashSet<>();
								if (rightOp instanceof Local) {
									pointsToSet = pointsToAnalysis.reachingObjects((Local) rightOp);
								}
								else if (rightOp instanceof FieldRef)
								{
									FieldRef fieldRef2 = (FieldRef) rightOp;
									if (fieldRef2.getField() != null && fieldRef2.getField().isStatic()) {
										pointsToSet = pointsToAnalysis.reachingObjects(fieldRef2.getField());
									}
								}
								else if (rightOp instanceof InvokeExpr) {
									InvokeExpr invokeExpr = (InvokeExpr) rightOp;
									types.add(invokeExpr.getMethod().getReturnType());
								}
								if (pointsToSet != null && pointsToSet.possibleTypes() != null) {
									types.addAll(pointsToSet.possibleTypes());
								}
								Set<RefType> refTypes = new HashSet<>();
								for (Type type : types) {
									if (type instanceof RefType) {
										refTypes.add((RefType) type);
									}
									else if (type instanceof AnySubType)
									{
										refTypes.add(((AnySubType) type).getBase());
									}
								}
								for (RefType refType : refTypes) {
									SootClass fieldClass = refType.getSootClass();
									if (DestructibleIdentify.isDestructibleClass(fieldClass, fieldClass)) {
										destructibleSootFieldMap.get(sootClass).add(fieldRef.getField());
										destructibleSootFields.add(fieldRef.getField());
									}
								}

							}
						}
					}
				}
			}
		}
	}
}