package ac.pool;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import ac.component.PointCollectorAsyncTask;
import ac.component.PointCollectorExecutor;
import ac.component.PointCollectorThread;
import ac.constant.ExecutorSig;
import ac.constant.ThreadSig;
import ac.pool.checker.HTRChecker;
import ac.pool.checker.INRChecker;
import ac.pool.checker.PoolCheck;
import ac.pool.point.InitPoint;
import ac.pool.point.KeyPoint;
import ac.pool.point.PointCollector;
import ac.util.AsyncInherit;
import ac.util.Log;
import soot.G;
import soot.Pack;
import soot.PackManager;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Type;
import soot.Body;
import soot.BodyTransformer;
import soot.jimple.infoflow.Infoflow;
import soot.jimple.infoflow.InfoflowConfiguration.CallgraphAlgorithm;
import soot.jimple.infoflow.InfoflowConfiguration.CodeEliminationMode;
import soot.jimple.infoflow.InfoflowConfiguration.SootIntegrationMode;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration.CallbackAnalyzer;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.entryPointCreators.DefaultEntryPointCreator;
import soot.jimple.infoflow.sourcesSinks.manager.ISourceSinkManager;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;

//负责整个分析流程的初始化和调度
//整个线程池和并发问题静态分析工具的主控制类
//分析目标识别（APK/JAR/目录）
//Soot分析环境配置
//调用图构建
//并发问题检测调度
//结果记录
public class PoolMain {

	public static long startTime = 0; /// 程序开始时间

	public static long preprocessStartTime = 0; // 预处理开始时间

	//	安卓平台路径
	public static String Android_Platforms = "/Users/qiuyucheng/Library/Android/sdk/platforms";

	//	默认输入路径（单个apk文件）
	static String inputPath = "/Users/qiuyucheng/Downloads/F-Droid.apk";
	//  输出目录
	public static final String Output = "./Felidae-ThreadPool-output/";

//	public static boolean refinement = true;

	//	当前分析文件是不是apk文件 ？
	private static boolean isApk = false;
	//  jar文件列表...
	private static List<String> jars = new ArrayList<String>();
	//  最大内存使用量
	private static long maxUsedMemory = 0;
	//  检测到的问题数量
	public static int misuseNum = 0;

	//	监控分析过程中的内存使用情况，防止内存溢出
	static class MemoryThread extends Thread {
		@Override
		public void run() {
			while (true && !isInterrupted()) {//表示没有被中断。
				try {
//					maxUsedMemory_t表示已经使用的内存
					long maxUsedMemory_t = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
					maxUsedMemory = Math.max(maxUsedMemory, maxUsedMemory_t);
					sleep(2 * 100);
				} catch (InterruptedException e) {//抛出一个异常中断，然后执行下面的逻辑
					long maxUsedMemory_t = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
					maxUsedMemory = Math.max(maxUsedMemory, maxUsedMemory_t);
					break;
				}
			}
		}
	}

	public static void main(String[] args) {
//		args :  args[0] "D:\\cbq\\EclipseWorkspace\\ADailyTest\\target\\classes"
//      args[1]、args[2]。。。
//		args = new String[]{"/Users/qiuyucheng/Downloads/Felidae-ThreadPool-main/target/classes"};
//		args = new String[]{"/Users/qiuyucheng/Downloads/Felidae-ThreadPool-main/src/Mytest/test_il.jar"};
		args = new String[]{"/Users/qiuyucheng/Downloads/Felidae-ThreadPool-main/TestProject/jetty-server-12.1.4.jar"};

		Thread thread = new MemoryThread();
		thread.setDaemon(true);  // 守护线程！
		thread.start();
		startTime = System.currentTimeMillis();
		inputPath = args[0];

		if (args.length > 1) {
			Android_Platforms = args[1];
		}
//      根据文件类型选择分析方式（支持  apk文件、jar文件、
		if (inputPath.toLowerCase().endsWith(".apk")) {
			isApk = true;
			startApk();
		}
		else if (inputPath.toLowerCase().endsWith(".jar")) {
			jars.add(inputPath);
			startJars();
		}
		else //既不是apk文件，也不是jar文件
		{
//			收集目录下所有jar文件（认为是一个目录）
			jars.addAll(getJars(inputPath));
			if(jars.isEmpty()) {
				jars.add(inputPath);
			}
			startJars();
		}

//		// 内存监控结束和统计
//		try {
//			thread.join(); //等待该thread内存线程执行完成。阻塞当前线程，直到被调用的线程执行完毕
//			Log.i("maxUsedMemory = ", maxUsedMemory / (1024 * 1024));
//
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		try{
			thread.interrupt();
		}catch(Exception e){
			throw new RuntimeException();
		}
//
		ThreadErrorRecord.recordTime("", "", preprocessStartTime, startTime);
		Log.i("## end ", inputPath);

	}

	//	 开始分析Jar文件
	private static void startJars() {
//		一系列初始化工作.
		G.reset();
		Options.v().set_src_prec(Options.src_prec_c);
		Options.v().set_process_dir(jars);
		Options.v().set_no_bodies_for_excluded(true);
		Options.v().set_no_writeout_body_releasing(true); // must be set to true if we want to access method bodies
		// after writing output to jimple
		Options.v().set_output_format(Options.output_format_none);
		Options.v().allow_phantom_refs();
		Options.v().set_whole_program(true);
		Options.v().set_exclude(getExcludeList());

		// 开启一些选项以确保 SPARK 能更好工作
		Options.v().setPhaseOption("cg.spark", "on");
		Options.v().setPhaseOption("cg.spark", "string-constants:true");

		soot.Main.v().autoSetOptions();

		// 2. 加载类 (移除之前的 jtp Transform 代码，因为不需要在那一步收集了)
		try {// 运行soot进行全分析。
			Scene.v().loadNecessaryClasses();
			// 注意：不需要 runPacks()，因为我们不需要运行 jtp 变换来收集方法，
			// 我们会在下面直接从 Scene 中获取。
		} catch (Throwable e) {
			e.printStackTrace();
		}

		// 3. 构建虚拟入口 (Dummy Main)
		// 收集所有 Application Class 中的 Public 方法
		List<String> methodsToCall = new ArrayList<>();
		for (SootClass sc : Scene.v().getApplicationClasses()) {
			// 跳过接口、抽象类、非具体的类
			if (sc.isInterface() || sc.isPhantom() || sc.isAbstract()) {
				continue;
			}

			for (SootMethod sm : sc.getMethods()) {
				// 筛选具体的、public 的方法作为潜在入口
				if (sm.isConcrete() && sm.isPublic() && !sm.isConstructor()) {
					methodsToCall.add(sm.getSignature());
				}
			}
		}

		// 使用 FlowDroid 的工具生成虚拟 Main 方法
		// 这个 DummyMain 会实例化这些类，并调用这些方法
		SootMethod dummyMain = null;
		if (!methodsToCall.isEmpty()) {
			// DefaultEntryPointCreator 是 FlowDroid 提供的工具
			DefaultEntryPointCreator creator = new DefaultEntryPointCreator(methodsToCall);
			dummyMain = creator.createDummyMain();
			Scene.v().addBasicClass(dummyMain.getDeclaringClass().getName());
			Scene.v().setEntryPoints(Collections.singletonList(dummyMain));
			System.out.println("虚拟入口已构建，包含调用目标: " + methodsToCall.size() + " 个");
		} else {
			System.err.println("错误：未找到任何可调用的 Public 方法，无法构建入口。");
			return;
		}

// 		使用FlowDroid工具构建调用图的程序段，Infoflow是FlowDroid的主分析对象
//		false表示不使用Android回调分析
		Infoflow infoflow = new Infoflow(inputPath, false);
//		配置Soot（Java字节码分析框架）的集成模式
		infoflow.getConfig().setSootIntegrationMode(SootIntegrationMode.UseExistingInstance);
//		选择调用图构建算法，SPARK算法：一种精确的指针分析算法，能生成较精确的调用图，而非完整的数据流图。
		infoflow.getConfig().setCallgraphAlgorithm(CallgraphAlgorithm.SPARK);
//		关闭污点分析功能，只需要构建调用图
		infoflow.getConfig().setTaintAnalysisEnabled(false);
//		禁止代码消除优化，确保所有代码都被分析，不进行任何优化删除
		infoflow.getConfig().setCodeEliminationMode(CodeEliminationMode.NoCodeElimination);
		try {
//			通过反射获取runAnalysis私有方法
			Method constructCG = Infoflow.class.getDeclaredMethod("runAnalysis", ISourceSinkManager.class);
//		    突破Java的访问限制，允许调用私有方法
			constructCG.setAccessible(true);
//			实际执行调用图构建分析，null表示不使用特定的源-汇管理器
			constructCG.invoke(infoflow, (ISourceSinkManager) null);
//			在调用图构建完成后，执行自定义的误用检测逻辑
			detectMisuse();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void startApk() {
		Log.i("## start apk ", inputPath);
//		创建APK分析的核心应用程序对象
//		Android_Platforms：Android平台文件路径（包含Android.jar等）
//		inputPath：目标APK文件路径
		SetupApplication application = new SetupApplication(Android_Platforms, inputPath);
//		配置是否合并多个Dex文件
		application.getConfig().setMergeDexFiles(true);
//		配置回调分析使用的算法，分析Android中的事件回调（如按钮点击、生命周期方法等）
//		CallbackAnalyzer.Fast - 使用快速但可能不够精确的回调分析器
		application.getConfig().getCallbackConfig().setCallbackAnalyzer(CallbackAnalyzer.Fast);
//		设置回调分析的最大时间限制，防止分析过程无限期运行
		application.getConfig().getCallbackConfig().setCallbackAnalysisTimeout(5 * 60);
//		分析APK中所有方法的调用关系，建立完整的调用图谱
		application.constructCallgraph();
//		基于构建的调用图进行安全检测
		detectMisuse();
	}

	private static void detectMisuse() {
		Log.i("----------detector starts ");
//     存储所有实现了Runnable或Callable接口的类名，Set是为了确保唯一性
		final Set<String> runnableClasses = new HashSet<String>();
//     专门存储"不响应中断"的类名
		Set<String> iNRClasses = new HashSet<>();
//     获取所有应用类（排除系统库类）
		Set<SootClass> sootClasses = new HashSet<>(Scene.v().getApplicationClasses());
		for (SootClass currentClass : sootClasses) {//遍历每一个应用类
//        检查 当前这个类是否实现了Runnable接口或者Callable接口
			if (AsyncInherit.isInheritedFromRunnable(currentClass)
					|| AsyncInherit.isInheritedFromCallable(currentClass))
			{
//           这么用runnableClasses将这个类存起来。
				runnableClasses.add(currentClass.getName());
//           检查该类中是否包含中断检查的逻辑
				if (!INRChecker.hasInterruptCheck(currentClass)) {
//              如果不包含，则加入iNRClasses这个set中
					iNRClasses.add(currentClass.getName());
				}
			}
		}
//     输出不响应中断的类数量
		Log.i("iNRClasses.size() = ", iNRClasses.size());
//     HTR检查器初始化
		HTRChecker.init();
//     System.out.println("HTRChecker init is done");
//     三种并发模型的数据收集器创建和启动

//     PointCollectorExecutor分析ExecutorService线程池相关的代码模式
//     收集newFixedThreadPool(), submit(), shutdown()等调用点
		PointCollector executorCollector = new PointCollectorExecutor();
		executorCollector.start(sootClasses);

		// 【新增修改】过滤掉位于构造函数和dummyMainMethod中的噪音点
		filterNoisePoints(executorCollector);

//     PointCollectorThread分析原生Thread使用模式
//     收集new Thread(), start(), interrupt()等调用点
		PointCollector threadCollector = new PointCollectorThread();
		threadCollector.start(sootClasses);

		// 【新增修改】过滤掉位于构造函数和dummyMainMethod中的噪音点
		filterNoisePoints(threadCollector);

//     PointCollectorAsyncTask分析Android AsyncTask使用模式
//     收集AsyncTask.execute(), 重写的doInBackground()等方法
//     PointCollector asyncTaskCollector = new PointCollectorAsyncTask();
//     asyncTaskCollector.start(sootClasses);

//     start的作用：
//     遍历所有类的方法体
//     识别特定的方法调用模式
//     建立关键程序点的映射关系


//     debug(pointCollector);
//     int eventSize = runnableClasses.size();
//     eventSize += pointCollector.getInitialPoints().size();
//     eventSize += pointCollector.getStartPoints().size();
//     eventSize += pointCollector.getShutDownPoints().size();
//     eventSize += pointCollector.getShutDownNowPoints().size();
//     ThreadErrorRecord.recordWorkingData(eventSize);

		// cg
//     静态分析工具可能无法准确识别通过反射或接口调用的run()方法
//     手动添加从start()/submit()到run()的调用边，确保分析完整性
		complementCGFromStartToRun(executorCollector);
		complementCGFromStartToRun(threadCollector);

		// check
		new PoolCheck(executorCollector, "ExecuteService-", iNRClasses).check();
		new PoolCheck(threadCollector, "Thread-", iNRClasses).check();
//     new PoolCheck(asyncTaskCollector, "AsyncTask-", iNRClasses).check();

//      在主线程各种关闭PoolCheck的executor线程池；
		long timeStart = System.currentTimeMillis();
		ExecutorService executor = PoolCheck.executor;
		executor.shutdown();

//     try {
////          等待已提交任务完成，最多等待10秒
//        executor.awaitTermination(10, TimeUnit.SECONDS);
//     } catch (InterruptedException e) {
//        // TODO Auto-generated catch block
//        e.printStackTrace();
//     }
		try {
			// 添加超时和状态检查
			if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
				System.out.println("NTT检查未在10秒内完成，强制关闭线程池...");
				executor.shutdownNow(); // 强制关闭

				// 再次等待
				if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
					System.err.println("线程池无法正常关闭");
				}
			}
			System.out.println("所有检查完成，程序退出");
		} catch (InterruptedException e) {
			System.err.println("等待被中断，强制关闭线程池");
			executor.shutdownNow();
			Thread.currentThread().interrupt(); // 重新设置中断状态
		}
	}

	// 【新增方法】用于过滤掉不需要检测的噪音点（构造函数、dummyMain）
	private static void filterNoisePoints(PointCollector pointCollector) {
		// 获取初始点集合（即 new ThreadPool 或 new Thread 的位置）
		// 假设 getInitialPoints 返回的是支持 remove 操作的集合（如 ArrayList 或 HashSet）
		// 如果 PointCollector 中没有直接暴露修改接口，可能需要修改 PointCollector 源码，
		// 但通常 getter 返回的是引用，可以直接修改。
		java.util.Iterator<? extends KeyPoint> iterator = pointCollector.getInitialPoints().iterator();

		while (iterator.hasNext()) {
			KeyPoint point = iterator.next();
			SootMethod method = point.getMethod(); // 获取该代码点所在的方法
			String methodName = method.getName();
			String methodSig = method.getSignature();

			// 1. 忽略发生在 dummyMainMethod 中的调用（分析环境产生的噪音）
			if (methodName.contains("dummyMainMethod") || methodSig.contains("dummyMainMethod")) {
				iterator.remove();
				continue;
			}

			// 2. 忽略发生在构造函数 <init> 中的调用
			// 在构造函数中创建线程池，静态分析往往难以追踪其完整的生命周期（如赋值给字段后在destroy中关闭），
			// 这通常会导致大量的 NT 误报。
			if (methodName.equals("<init>")) {
				iterator.remove();
				continue;
			}
		}
	}

	static void debug(PointCollector pointCollector) {
//		ExceptionHandler handler = new ExceptionHandler();
		for (SootMethod sootMethod:Scene.v().getSootClass("cn.ac.ios.PoolTest").getMethods()) {
//			Log.i(sootMethod,"#",handler.hasExceptionHandler(sootMethod));
			Log.i(sootMethod.getActiveBody());
		}
//		Log.i(" Start CallerRunsChecker..  ");
//
//		for (KeyPoint point : pointCollector.getSetRejectedExecutionHandlerPoints()) {
//			if (CallerRunsChecker.isSetCallerRunsHandlerMisuse(point)) {
//				ThreadErrorRecord.recordCallerRunsChecker(point);
//			}
//
//		}
		System.exit(0);
	}

	private static List<String> getExcludeList() {
		ArrayList<String> excludeList = new ArrayList<String>();
		excludeList.add("android.*");
		excludeList.add("androidx.*");
//		excludeList.add("org.*");
//		excludeList.add("soot.*");

		excludeList.add("java.*");
		excludeList.add("sun.*");
		excludeList.add("javax.*");
		excludeList.add("com.sun.*");

//		excludeList.add("com.ibm.*");
		excludeList.add("org.xml.*");
		excludeList.add("org.w3c.*");
//		excludeList.add("apple.awt.*");
//		excludeList.add("com.apple.*");
		return excludeList;
	}

	//	补充调用图中从  起点方法  到  运行方法 的边，主要处理别名调用关系
	static void complementCGFromStartToRun(PointCollector pointCollector) {
		for (KeyPoint startPoint : pointCollector.getStartPoints()) {
			for (InitPoint point : pointCollector.getInitialPoints()) {
				if (point.isAliasCaller(startPoint)) { //如果初始点和开始点是别名关系
					for (RefType refType : point.getCallerPossibleType()) {
						addEdgeFromStartToRunMethod(startPoint, refType.getSootClass());
					}
				}
			}
		}
	}

	//	在调用图中添加从 起点方法 到 线程执行方法 的调用边。
	static void addEdgeFromStartToRunMethod(KeyPoint startPoint, SootClass sootClass) {
		try {
			SootMethod runMethod = sootClass.getMethodUnsafe(ThreadSig.METHOD_SUBSIG_RUN);
			if(runMethod == null) {
				runMethod = sootClass.getMethodByNameUnsafe(ExecutorSig.METHOD_NAME_CALL);
			}
			if(runMethod == null) {
				return;
			}
			Edge edge = new Edge(startPoint.getMethod(), startPoint.getStmt(), runMethod);
			Scene.v().getCallGraph().addEdge(edge);
		} catch (Exception e) {

		}

	}

	//	递归扫描指定目录及其所有子目录，收集所有.jar文件的绝对路径。
	public static Set<String> getJars(String dic) {
		File file = new File(dic);
		Set<String> jarList = new HashSet<String>();
		if (file.isDirectory()) {
			File[] fileList = file.listFiles();
			if (fileList != null) {
				for (File subFile : fileList) {
					String string = subFile.getAbsolutePath();
					if (subFile.isDirectory()) {
						jarList.addAll(getJars(string));
					} else {
						if (string.toLowerCase().endsWith(".jar")) {
							jarList.add(string);
						}
					}

				}
			}
		}
		return jarList;
	}

	//	构建和创建输出目录路径，确保分析结果有合适的存储位置。
	public static String getOutputPath(String sub) {
		String path = null;
		if (isApk || inputPath.toLowerCase().endsWith(".jar")) {
			path = inputPath.substring(0, inputPath.lastIndexOf("."));
		} else {
			path = inputPath;
		}
		while (path.endsWith(File.separator)) {
			path = path.substring(0, path.length() - 1);
		}
		int start = path.lastIndexOf(File.separator);
		if (start == -1) {
			start = 0;
		}
		path = Output + path.substring(start) + File.separator + sub + File.separator;
		File file = new File(path);
		if (!file.exists()) {
			file.mkdirs();
		}
		return path;
	}

	public static String getApkFullPath() {
		return inputPath;
	}

	public static boolean isApk() {
		return isApk;
	}
}
