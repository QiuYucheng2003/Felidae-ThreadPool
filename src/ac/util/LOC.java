package ac.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.rmi.server.RemoteObject;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class LOC {


    private static int classCount = 0;
    private static int lineCode = 0;
    static Set<String> jarList = new HashSet<>();

    public static void main(String[] args) {

//		args = new String[] { "./apache-hive-4.0.0", "./apache-hive-4.0.0-alpha-1", "./apache-iotdb-0.13.4",
//				"./apache-iotdb-1.3.1", "./apache-tez-0.10.3", "./apache-uniffle-0.8.0-incubating", "./cassandra",
//				"./elasticsearch-7.17.21", "./elasticsearch-hadoop-7.17.21", "./hadoop-3.4.0", "./hadoop322_backup",
//				"./HBase-2.4.5", "./hbase-3.0.0-beta-1", "./ozone-1.4.0", "./phoenix-hbase-2.5.0-5.2.0",
//				"./pravega-0.13.0", "./zookeeper" };

//        args = new String[] {"D:\\cbq\\Research\\RPC20240724\\rmi-jndi-ldap-jrmp-jmx-jms-master","D:\\cbq\\Research\\RPC20240724\\apache-jmeter-5.6.3", "D:\\cbq\\Research\\RPC20240724\\org.ops4j.pax.exam2-exam-reactor-4.13.5", "D:\\cbq\\Research\\RPC20240724\\RPC-Benchmark\\grpc-java-master\\benchmarks", "D:\\cbq\\Research\\RPC20240724\\RPC-Benchmark\\dubbo-samples-master"};

        args = new String[] {"/Users/qiuyucheng/Downloads/Felidae-ThreadPool-main/TestProject/agrona-1.17.1.jar"};
        for (String str : args) {
            String path = str;
            if (path.toLowerCase().endsWith("jar")) {
                jarList.add(path);
            } else {
                findAllJarFiles(new File(path));
            }
            for (String jar : jarList) {
                findAllJarClassfiles(jar);
            }
            System.out.println("*************************");
            System.out.println(path);
            System.out.println("jarList.size() = " + jarList.size());
            System.out.println("classCount = " + classCount);
            System.out.println("lineCode = " + lineCode);
            jarList.clear();
            lineCode = 0;
            classCount = 0;
        }

    }

    public static void findAllJarFiles(File dir) {
        try {
            File[] files = dir.listFiles();
            for (int i = 0; i < files.length; i++) {
                String jspPath = files[i].getAbsolutePath().replace("\\", "/");
                if (jspPath.toLowerCase().endsWith(".jar")) {
                    jarList.add(jspPath);
                }
                if (files[i].isDirectory()) {
                    findAllJarFiles(files[i]);
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static Set<String> findAllJarClassfiles(String jarName) {
        Set<String> jarFileList = new HashSet<String>();
        try {
            JarFile jarFile = new JarFile(jarName);
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                String fileName = jarEntry.getName();
                if (fileName.endsWith(".class")) {
                    // System.out.println(fileName);
                    fileName = fileName.replace("/", ".");
                    jarFileList.add(fileName);
                    classCount++;
                    try {
                        InputStream is = jarFile.getInputStream(jarEntry);
                        BufferedReader br = new BufferedReader(new InputStreamReader(is));
                        @SuppressWarnings("unused")
                        String line = "";
                        while ((line = br.readLine()) != null) {
                            lineCode++;
                        }
                    } catch (Throwable e) {
//						e.printStackTrace();
                    }
                }
            }
            jarFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return jarFileList;
    }


}