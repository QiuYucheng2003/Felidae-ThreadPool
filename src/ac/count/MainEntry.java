package ac.count;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

public class MainEntry {

    // 结果输出目录（当前项目根目录）
    private static final String STATISTICS_RESULT_FOLDER = "." + File.separator;
    // 结果文件名
    private static String STATISTICS_RESULT_FILE_NAME = "Felidae_Detailed_Statistics.txt";

    public static void main(String[] args) {

        // =========================================================================
        // 【TODO: 请修改这里】将下面的路径改为你电脑上 "Felidae-ThreadPool-output" 的真实路径
        // =========================================================================
        // Windows 示例: "D:\\Experiment\\Data\\Felidae-ThreadPool-output" (注意使用双反斜杠 \\)
        // Mac/Linux 示例: "/Users/yourname/Documents/Felidae-ThreadPool-output"

        String targetFolder = "/Users/qiuyucheng/Downloads/Felidae-ThreadPool-main/Felidae-ThreadPool-output-apply";

        // =========================================================================

        File rootFile = new File(targetFolder);

        // 检查路径是否正确
        if (!rootFile.exists()) {
            System.err.println("错误：找不到路径 -> " + targetFolder);
            System.err.println("请回到 MainEntry.java 代码中修改 'targetFolder' 变量！");
            return;
        }

        System.out.println("正在分析文件夹: " + targetFolder);

        ArrayList<StatisticsTool> tools = new ArrayList<>();

        // 启动分析逻辑
        StatisticsTool tool = new StatisticsTool(rootFile);
        tool.start();
        tools.add(tool);

        // 构建 CSV 格式输出
        StringBuffer stringBuffer = new StringBuffer();

        // 定义每一列的格式：%-30s 表示左对齐占30格，%10s 表示右对齐占10格
        // Project名通常比较长，给多一点空间；数字给少一点
        String rowFormat = "%-35s %-6s %-6s %-6s %-6s %-6s %-6s %-6s %-6s %-6s %-6s %-6s %-12s %-12s %-12s\n";
        for (StatisticsTool t : tools) {
            // 1. 打印表头
            // 注意：表头中间用空格分开，不再用分号
            stringBuffer.append(String.format(rowFormat,
                    "Project", "HTR", "INR", "NTT", "IL",
                    "NT", "ENC", "UNT", "DPSE", "RCTP", "UBNT", "UTL",
                    "SootTime", "LeoTime", "TotalTime"
            ));

            // 添加一条分隔线，看起来更像表格
            stringBuffer.append("-----------------------------------------------------------------------------------------------------------------------------------------------------\n");

            for (Result result : t.getResultSet()) {
                // 记得先计算！
                result.calculateCounts();

                // 2. 打印每一行数据
                // 将所有数据填入格式中
                stringBuffer.append(String.format(rowFormat,
                        result.appName,
                        String.valueOf(result.HTRCount),
                        String.valueOf(result.INRCount),
                        String.valueOf(result.NTTCount),
                        String.valueOf(result.ILCount),
                        String.valueOf(result.NTCount),
                        String.valueOf(result.ENCCount),
                        String.valueOf(result.UNTCount),
                        String.valueOf(result.DPSECount),
                        String.valueOf(result.RCTPCount),
                        String.valueOf(result.UBNTCount),
                        String.valueOf(result.UTLCount),
                        String.valueOf(result.sootTime),
                        String.valueOf(result.leopardTime),
                        String.valueOf(result.sootTime + result.leopardTime)
                ));
            }
            stringBuffer.append("\r\n");
        }

        // 保存文件
        initFolder(STATISTICS_RESULT_FOLDER);
        String fullOutputPath = STATISTICS_RESULT_FOLDER + File.separator + STATISTICS_RESULT_FILE_NAME;
        save2File(fullOutputPath, stringBuffer.toString());

        System.out.println("分析完成！");
        System.out.println("统计结果已保存到项目根目录: " + fullOutputPath);
        // 如果是在 IDE 中运行，通常可以在左侧项目栏刷新一下看到生成的文件
    }

    private static void initFolder(String folder) {
        File file = new File(folder);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    private static void save2File(String filePath, String content) {
        File file = new File(filePath);
        BufferedWriter out = null;
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            // 这里改为了 false，表示每次运行覆盖旧结果，方便调试。如果想保留历史记录改成 true。
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false)));
            out.write(content);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}