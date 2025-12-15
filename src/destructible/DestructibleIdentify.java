package destructible;

import soot.Body;
import soot.Local;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.tagkit.AbstractHost;
import soot.tagkit.AnnotationTag;
import soot.tagkit.Tag;
import soot.tagkit.VisibilityAnnotationTag;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;
import soot.toolkits.scalar.SimpleLocalUses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ac.constant.ThreadSig;
import ac.util.Log;

//识别可销毁资源
public class DestructibleIdentify {

// 						存储类与其销毁方法的映射
	public final static Map<SootClass, SootMethod> destructibleClasses = new HashMap<>();
//	                    记录包含destroy方法的类名
	public final static Set<String> destroyClasses = new HashSet<>();
//	                    记录包含close方法的类名
	public final static Set<String> closeClasses = new HashSet<>();
//	                    记录使用PreDestroy/PostConstruct注解的类名
	public final static Set<String> annotationClasses = new HashSet<>();
//	                    记录空实现的销毁方法——NULL方法实现
	public final static Map<String, SootMethod> emptyDestructibleClasses = new HashMap<>();

//	遍历所有类，识别可销毁类并填充数据结构
	public static void collect() {
		Set<SootClass> classes = new HashSet<SootClass>();
		classes.addAll(Scene.v().getClasses());//获取Scene中的所有class（库类、应用类）
		classes.addAll(Scene.v().getApplicationClasses());//获取所有应用类，HashSet会去重
		for (SootClass sootClass : classes) {  //对于每一个类，进行分析，是不是可销毁的类?
			if (isDestructibleClass(sootClass,sootClass)) {  //具体逻辑待实现。

			} 
		}
	}

//	空方法检测。
//	这段代码的核心目的是检测方法是否真正需要实例上下文：
//	返回false：方法使用了this对象（访问字段、调用方法等）
//	返回true：方法没有使用this对象，行为类似静态方法
	public static boolean isEmptyMethod(SootMethod sootMethod) {
//		抽象方法、native方法，不视为空
//		静态方法、没有方法体avtivebody、空方法体的，视为空
		if(sootMethod.isAbstract()) {
			return false;
		}
		if(sootMethod.isNative()) {
			return false;
		}
		if(sootMethod.isStatic()) {
			return true;
		}
		if(!sootMethod.hasActiveBody()) {
			return true;
		}

		Body body = sootMethod.getActiveBody();
		if (body.getUnits().isEmpty()) {
			return true;
		}
//		构建 UnitGraph 语句调用图，生成的控制流图对象，每个节点是一个Unit（单条指令）
		UnitGraph unitGraph = new BriefUnitGraph(body);
//		返回代表当前对象实例的局部变量
		Local thisLocal = body.getThisLocal();
		SimpleLocalDefs localDefs = new SimpleLocalDefs(unitGraph);//找出每个局部变量在哪些位置被赋值
		SimpleLocalUses localUses = new SimpleLocalUses(unitGraph,localDefs);//找出每个变量的值在哪些位置被使用
//      这个逻辑判断——————当前方法是否真正使用了this对象
		for (Unit defsOfThisLocal : localDefs.getDefsOf(thisLocal)) {//获取所有对this局部变量的"定义"点
//			检查每个定义点的使用情况
			if(!localUses.getUsesOf(defsOfThisLocal).isEmpty()) {
				return false;
			}
		}
//		如果实例方法没有使用this，可以考虑改为静态方法，因为静态方法本来就没有this
		return true;
	}


//	判断是否为可销毁的类
//	最初要分析的原始类（在整个递归过程中保持不变）
//	当前正在分析的具体类（在递归过程中会变化）
	public static boolean isDestructibleClass(SootClass originClass, SootClass sootClass) {
		if (destructibleClasses.containsKey(sootClass)) {//检查当前分析的类是否已经在缓存中
//			如果已缓存，从缓存中获取当前类对应的销毁方法，将原始类也添加到缓存中，映射到相同的销毁方法
//			原始分析MyServiceImpl类（originClass）
		//  发现它继承自AbstractService（sootClass）
		//  而AbstractService已经在缓存中有destroy()方法
		//  于是将MyServiceImpl也映射到同一个destroy()方法
			destructibleClasses.put(originClass, destructibleClasses.get(sootClass)); 
			return true;
		}
//		检查当前类是否是Thread类,如果是Thread类，直接返回不可销毁
		if(ThreadSig.CLASS_THREAD.equals(sootClass.getName())) {
			return false;
		}
//		获取当前类的所有方法，用Set去重
		Set<SootMethod> sootMethods = new HashSet<SootMethod>(sootClass.getMethods());
		for (SootMethod sootMethod : sootMethods) {//遍历每一个方法。
//			方法是否有PreDestroy注解？是否以destroy结尾的方法？方法名是否包含close？
			if (isPreDestroyAnnotationMethod(sootMethod) || isDestroyMethod(sootMethod) || isCloseMethod(sootMethod)) {
//				将原始类与找到的销毁方法建立映射关系
				destructibleClasses.put(originClass, sootMethod);
//				原始类是否是应用类+确保原始类不是抽象类+检查销毁方法是否为空实现
				if(Scene.v().getApplicationClasses().contains(originClass) && !originClass.isAbstract() && isEmptyMethod(sootMethod)) {
					Log.e("#####################");
					Log.e(originClass.getName());
					Log.e(sootMethod);
//					空实现的销毁方法记录
					emptyDestructibleClasses.put(originClass.getName() , sootMethod);
				}
				return true;
			}
		}
		if (sootClass.hasSuperclass()) {//检查是否有父类并且父类是否为可销毁类
			if (isDestructibleClass(originClass, sootClass.getSuperclass())) {
				return true;
			}
		}
		if (sootClass.getInterfaces() != null) { //遍历当前类实现的所有接口
			for (SootClass interfaceClass : sootClass.getInterfaces()) {
//				检查每个接口是否定义了可销毁方法。
				if (isDestructibleClass(originClass, interfaceClass)) {
					return true;
				}
			}
		}
		return false;
	}


//   判断是否为PreDestroy注解标注的方法
	private static boolean isPreDestroyAnnotationMethod(SootMethod sootMethod) {
//		1.获取该方法的所有注解
		List<AnnotationTag> annotationTags = getAnnotationtags(sootMethod);
		for (AnnotationTag annotationTag : annotationTags) {//遍历注解列表中的所有注解。
			//Ljavax/annotation/PreDestroy;
			//Ljakarta/annotation/PreDestroy;
			//xxx/PostConstruct;
//			看看是不是PreDestroy或者PostConstruct注解的方法。
//			如果是，那么将这个类加入annotationClasses
			if (annotationTag.getType().endsWith("PreDestroy") || annotationTag.getType().endsWith("PostConstruct")) {
				annotationClasses.add(sootMethod.getDeclaringClass().getName());
				return true;
			}
		}
		return false;
	}


//     检测是否为Destroy方法。
	public static boolean isDestroyMethod(SootMethod sootMethod) {
		if(sootMethod.getName().toLowerCase().endsWith("destroy")) {//是否以destroy结尾
			destroyClasses.add(sootMethod.getDeclaringClass().getName());//记录包含destroy方法的类名
			return true;
		}
		return false;
	}
//	   检测是否为Close方法。
	private static boolean isCloseMethod(SootMethod sootMethod) {
		if(sootMethod.getName().toLowerCase().contains("close")) {// 是否以close结尾
			closeClasses.add(sootMethod.getDeclaringClass().getName());//记录包含close方法的类名
			return true;
		}
		return false;
	}

//     提取一个类或者方法的所有注解。返回一个注解列表。
	private static List<AnnotationTag> getAnnotationtags(AbstractHost host) {
		List<AnnotationTag> tags = new ArrayList<>();//创建空的注解列表
		for (Tag tag : host.getTags()) {//遍历宿主（方法/类）的所有标签
			if (tag instanceof VisibilityAnnotationTag) {//检查标签是否是可见性注解标签
				VisibilityAnnotationTag visibilityAnnotationTag = (VisibilityAnnotationTag) tag;
				tags.addAll(visibilityAnnotationTag.getAnnotations());//提取所有注解并添加到结果列表
			}
		}
		return tags;
	}

}
