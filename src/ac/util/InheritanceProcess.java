/* AsyncDetecotr - an Android async component misuse detection tool
 * Copyright (C) 2018 Linjie Pan
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

package ac.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import soot.ArrayType;
import soot.PrimType;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.Type;


//继承关系处理工具，专门用于在 Android 异步组件检测工具中分析和处理类的继承关系。

// isInheritedFromGivenClass() 方法：检查一个类是否继承自指定的父类
// getDirectSubClasses() 方法：获取指定类的所有直接子类
// 相等匹配 (MatchType.equal)：精确匹配类名
// 正则匹配 (MatchType.regular)：使用正则表达式匹配类名
// isClassInSystemPackage() 方法：判断类是否属于系统包（如 android.、java. 等）
/**
 * Processing class inheritance relationship
 * 
 * @author Linjie Pan
 * @version 1.0
 */
public class InheritanceProcess {

	public enum MatchType {
		equal, regular
//		一个是精确匹配模式，一个是正则表达式匹配模式。
	}

//	获取指定类(currentClass)的直接子类
	public static List<SootClass> getDirectSubClasses(SootClass currentClass) {
		List<SootClass> subclasses = new ArrayList<SootClass>();
		for (SootClass theClass : Scene.v().getApplicationClasses()) {//遍历所有应用类
			//检查当前遍历的类的直接父类是否等于 currentClass
			if (theClass.getSuperclassUnsafe().equals(currentClass)) {
				subclasses.add(theClass);//如果是的话，将当前类加入这个子类列表
			}
		}
		return subclasses;
	}
//	检查两个类型之间的继承关系:subtype(子类型）/ parentType(父类型)
	public static boolean isInheritedFromGivenClass(Type subType, Type parentType) {
//		基本类型 (PrimType） 如果都是基本类型，则返回false，因为基本类型无继承关系。
		if (subType instanceof PrimType || parentType instanceof PrimType)
			return false;
//		如果一个是数组类型，一个是引用类型，则返回false，因为类型不兼容
		else if ((subType instanceof ArrayType && parentType instanceof RefType)
				|| (parentType instanceof ArrayType && subType instanceof RefType))
			return false;
//		如果两个都是引用类型 (RefType)，转换为 SootClass 并调用三参数版本的方法进行精确匹配
		else if (subType instanceof RefType && parentType instanceof RefType)
			return isInheritedFromGivenClass(((RefType) subType).getSootClass(), parentType.toString(),
					MatchType.equal);
//		如果两个都是数组类型的，获取两个数组的维度numDimensions，如果维度不一样则返回false
		else if (subType instanceof ArrayType && parentType instanceof ArrayType) {
			int subDim = ((ArrayType) subType).numDimensions;
			int parentDim = ((ArrayType) parentType).numDimensions;
			if (subDim != parentDim)
				return false;
//			获取数组的基本类型，并递归检查基本类型的继承关系
			Type subBaseType = ((ArrayType) subType).baseType;
			Type parentBaseType = ((ArrayType) parentType).baseType;
			return isInheritedFromGivenClass(subBaseType, parentBaseType);
		} else {
			return false;
		}
	}

//	两参数版本的方法
	public static boolean isInheritedFromGivenClass(SootClass theClass, String classNameUnderMatch) {
		MatchType matchType = MatchType.equal;//设置匹配模式为精确匹配
		if (theClass == null)
			return false;
//		检查当前类是否匹配目标类名，如果匹配直接返回 true
		if (isTypeMatch(theClass, classNameUnderMatch, matchType)) {
			return true;
		}
//		如果类的解析级别低于 HIERARCHY 级别，尝试提升解析级别
		if(theClass.resolvingLevel() < SootClass.HIERARCHY) {
			Scene.v().addBasicClass(theClass.getName(), SootClass.HIERARCHY);
		}
		if(theClass.resolvingLevel() < SootClass.HIERARCHY ) {
			return false;
		}
		for (SootClass interfaceClass : theClass.getInterfaces()) {
//			遍历 theClass.getInterfaces() 获取该类的所有接口，对每一个接口递归调用该方法
			if (isInheritedFromGivenClass(interfaceClass, classNameUnderMatch, matchType)) {
				return true;	
			}
		}
		return isInheritedFromGivenClass(theClass.getSuperclassUnsafe(), classNameUnderMatch, matchType);
	}

	public static boolean isInheritedFromGivenClass(SootClass theClass, String classNameUnderMatch,
			MatchType matchType) {
		if (theClass == null)
			return false;
//		确保当前类的解析级别足够（HIERARCHY 级别包含继承关系信息）
		if(theClass.resolvingLevel() < SootClass.HIERARCHY) {
			Scene.v().addBasicClass(theClass.getName(), SootClass.HIERARCHY);
		}
//		根据类名获取父类的 SootClass 对象
		SootClass parentClass = Scene.v().getSootClass(classNameUnderMatch);
		if( parentClass.resolvingLevel() < SootClass.HIERARCHY) {
			Scene.v().addBasicClass(parentClass.getName(), SootClass.HIERARCHY);
		}
		if(theClass.resolvingLevel() < SootClass.HIERARCHY ||  parentClass.resolvingLevel() < SootClass.HIERARCHY) {
			return false;
		}
//		检查当前类是否直接匹配目标
		if (isTypeMatch(theClass, classNameUnderMatch, matchType))
			return true;
//		递归检查所有实现的接口
		for (SootClass interfaceClass : theClass.getInterfaces()) {
			if (isInheritedFromGivenClass(interfaceClass, classNameUnderMatch, matchType)) {
				return true;
			}
		}
//		最后递归检查父类的继承链
		return isInheritedFromGivenClass(theClass.getSuperclassUnsafe(), classNameUnderMatch, matchType);
	}

//	检查类型匹配
	private static boolean isTypeMatch(SootClass currentClass, String classNameUnderMatch, MatchType matchType) {
//		如果是精确匹配模式，比较类名字符串是否完全相等
		if (matchType == MatchType.equal && currentClass.getType().toString().equals(classNameUnderMatch))
			return true;
//		如果是正则匹配模式，调用 isRegularMatch 方法
		else if (matchType == MatchType.regular
				&& isRegularMatch(currentClass.getType().toString(), classNameUnderMatch))
			return true;
		else
			return false;
	}

//	执行正则表达式匹配
	private static boolean isRegularMatch(String targetStr, String regularStr) {
//		将正则字符串编译为 Pattern 对象
		Pattern p = Pattern.compile(regularStr);
//		创建匹配器，对目标字符串进行匹配
		Matcher m = p.matcher(targetStr);
		return m.matches();
	}

	/**
	 * Checks whether the given class name belongs to a system package
	 * 
	 * @param className The class name to check
	 * @return True if the given class name belongs to a system package, otherwise
	 *         false
	 */
	public static boolean isClassInSystemPackage(String className) {
		return className.startsWith("android.") || className.startsWith("java.") || className.startsWith("javax.")
				|| className.startsWith("sun.") || className.startsWith("org.omg.")
				|| className.startsWith("org.w3c.dom.") || className.startsWith("com.google.")
				|| className.startsWith("com.android.") || className.startsWith("com.ibm.")
				|| className.startsWith("com.sun.") || className.startsWith("com.apple.")
				|| className.startsWith("org.w3c.") || className.startsWith("soot");
	}

//	public static boolean isInheritedFromFragment(SootClass theClass) {
//		return isInheritedFromGivenClass(theClass, ClassSignature.FRAGMENTCLASS,MatchType.equal) || 
//				isInheritedFromGivenClass(theClass, ClassSignature.SUPPORTFRAGMENTCLASS_V7,MatchType.equal) ||
//				isInheritedFromGivenClass(theClass, ClassSignature.SUPPORTFRAGMENTCLASS,MatchType.equal) ;
//	}
}
