package ac.constant;


import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
//Signature 类是一个任务启动方法签名集合类
//专门用于在 Android 异步组件检测工具中定义和集中管理所有能够启动异步任务的方法签名。

public class Signature {

	public static final String[] startMethods = { ExecutorSig.METHOD_SUBSIG_SUBMIT_CALLABLE, ExecutorSig.METHOD_SUBSIG_SUBMIT_RUNNABLE,
			ExecutorSig.METHOD_SUBSIG_SUBMIT_RUNNABLE_T, ExecutorSig.METHOD_SUBSIG_EXECUTE };
	
	public static final Set<String> startMethods_set = new HashSet<>(Arrays.asList(startMethods));

}
