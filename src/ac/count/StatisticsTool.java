package ac.count;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StatisticsTool {

    // 内部类保持不变
    public static class DataSetResult {
        int HTRCount = 0; int NTTCount = 0; int INRCount = 0; int ILCount = 0;
        int NTCount = 0; int ENCCount = 0; int UNTCount = 0; int DPSECount = 0;
        int RCTPCount = 0; int UBNTCount = 0; int UTLCount = 0;
        int error = 0; int right = 0;
    }

    private static final String PATH = File.separator + "path" + File.separator;
    private static final String ERROR = File.separator + "error" + File.separator;

    // --- 文件名定义 (与你上传的截图一致) ---
    private String ERROR_DICT_HTR = "ExecuteService-HTR-sum.txt";
    private String ERROR_DICT_INR = "ExecuteService-INR-sum.txt";
    private String ERROR_DICT_NTT = "ExecuteService-NTT-sum.txt";
    private String ERROR_DICT_IL = "ExecuteService-IL-sum.txt";
    private String ERROR_DICT_NT = "ExecuteService-NT-sum.txt";
    private String ERROR_DICT_ENC = "ExecuteService-NonExceptionHandler-sum.txt";
    private String ERROR_DICT_UNT = "ExecuteService-UNT-sum.txt";
    private String ERROR_DICT_DPSE = "ExecuteService-CallerRuns-sum.txt";
    private String ERROR_DICT_RCTP = "ExecuteService-RCTP-sum.txt";
    private String ERROR_DICT_UBNT = "ExecuteService-UBNT-sum.txt";
    private String ERROR_DICT_UTL = "ExecuteService-UTL-sum.txt";

    private String ERROR_SUM = "error-async.txt";
    private String RIGHT_SUM = "right-async.txt";
    private String TIME_FILE_NAME = "log.txt";

    private File mDict = null;
    private String currentApk = null;
    private Set<Result> mSet = new HashSet<Result>();

    // 计数器
    private int HTRCount = 0, NTTCount = 0, INRCount = 0, ILCount = 0;
    private int NTCount = 0, ENCCount = 0, UNTCount = 0, DPSECount = 0, RCTPCount = 0, UBNTCount = 0, UTLCount = 0;
    private int error = 0, right = 0;

    public StatisticsTool(File file) {
        mDict = file;
    }

    public Set<Result> getResultSet() { return mSet; }

    public int start() {
        String[] appNames = mDict.list();
        if (appNames != null) {
            for (String apkDict : appNames) {
                if (apkDict.startsWith(".")) continue;
                File f = new File(mDict, apkDict);
                if (f.isDirectory()) {
                    System.out.println("Processing Project: " + apkDict);
                    processEachAppDict(apkDict);
                }
            }
        } else {
            System.err.println("错误: 指定的路径下没有找到任何子文件夹/文件 -> " + mDict.getAbsolutePath());
        }
        return 0;
    }

    private void processEachAppDict(String apkDict) {
        currentApk = apkDict;
        Result result = new Result(apkDict);

        // 调用处理方法
        procesHTRError(result);
        procesNTTError(result);
        procesINRError(result);
        procesILError(result);
        procesNTError(result);
        procesENCError(result);
        procesUNTError(result);
        procesDPSEError(result);
        procesRCTPError(result);
        procesUBNTError(result);
        procesUTLError(result);

        processError(result);
        processRight(result);
        procesTime(result);

        mSet.add(result);
    }

    // --- [核心修正] 通用的解析逻辑 ---
    // 自动处理 items.length >= 2 的情况，并使用 items[1] 作为 Key
    private void parseAndAdd(Result result, String fileName, java.util.function.BiConsumer<String, String> adder) {
        String filePath = getAbsoluteFilePath(ERROR, fileName);
        File checkFile = new File(filePath);

        // 读取文件
        List<String> lines = processEachFile(filePath);

        if (!lines.isEmpty()) {
            int addedCount = 0;
            for (String line : lines) {
                String[] items = line.split(";");
                // [修正点1] 放宽条件：只要 >= 2 列就读取
                if (items.length >= 2) {
                    // [修正点2] 读取 items[1] (指令内容) 作为Key，而不是 items[2] (null)
                    String key = items[1];
                    adder.accept(items[0], key);
                    addedCount++;
                }
            }
            // [调试信息] 如果文件有内容，打印读取了多少行
            if (addedCount > 0) {
                // System.out.println("   -> [" + fileName + "] added " + addedCount + " errors.");
            }
        } else {
            // 如果文件不存在，这里不报错，因为不是每个项目都有所有错误
        }
    }

    // --- 各个类型的具体处理 (全部使用上面的通用逻辑) ---
    private void procesHTRError(Result result) { parseAndAdd(result, ERROR_DICT_HTR, result::addHTR); }
    private void procesNTTError(Result result) { parseAndAdd(result, ERROR_DICT_NTT, result::addNTT); }
    private void procesINRError(Result result) { parseAndAdd(result, ERROR_DICT_INR, result::addINR); }
    private void procesILError(Result result)  { parseAndAdd(result, ERROR_DICT_IL,  result::addIL); }

    private void procesNTError(Result result)   { parseAndAdd(result, ERROR_DICT_NT,   result::addNT); }
    private void procesENCError(Result result)  { parseAndAdd(result, ERROR_DICT_ENC,  result::addENC); }
    private void procesUNTError(Result result)  { parseAndAdd(result, ERROR_DICT_UNT,  result::addUNT); }
    private void procesDPSEError(Result result) { parseAndAdd(result, ERROR_DICT_DPSE, result::addDPSE); }
    private void procesRCTPError(Result result) { parseAndAdd(result, ERROR_DICT_RCTP, result::addRCTP); }
    private void procesUBNTError(Result result) { parseAndAdd(result, ERROR_DICT_UBNT, result::addUBNT); }
    private void procesUTLError(Result result)  { parseAndAdd(result, ERROR_DICT_UTL,  result::addUTL); }

    private String getAbsoluteFilePath(String parentPath, String relativePath) {
        return mDict + File.separator + currentApk + parentPath + relativePath;
    }

    public interface LineParseListener {
        public void after(String[] items);
    }

    private void addError(String filePath, LineParseListener listener) {
        // 保留旧方法以兼容可能遗漏的调用，但主要逻辑已移至 parseAndAdd
        List<String> lines = processEachFile(filePath);
        if (lines.isEmpty() || listener == null) return;
        for (String line : lines) {
            String[] items = line.split(";");
            listener.after(items);
        }
    }

    private void procesTime(Result result) {
        String path = getAbsoluteFilePath(PATH, TIME_FILE_NAME);
        List<String> lines = processEachFile(path);
        for (String line : lines) {
            String lowerLine = line.toLowerCase();
            if (lowerLine.contains("soottime")) {
                result.setSootTime(getInteger(line));
            }
            // 【修正】添加 "felidaetime" 匹配你的 log.txt
            else if (lowerLine.contains("androlic time") ||
                    lowerLine.contains("leopard time") ||
                    lowerLine.contains("felidaetime") ||
                    lowerLine.contains("totaltime")) {
                result.setLeopardTime(getInteger(line));
            }
        }
    }

    private int getInteger(String source) {
        String[] strs = source.split(":");
        if (strs.length < 2) return 0;
        int i = 0;
        try { i = Integer.parseInt(strs[1].trim()); } catch (Exception e) {}
        return i;
    }

    private void processError(Result result) {
        List<String> errorList = processEachFile(getAbsoluteFilePath(PATH, ERROR_SUM));
        for (String error : errorList) result.addError(error);
    }

    private void processRight(Result result) {
        List<String> rightList = processEachFile(getAbsoluteFilePath(PATH, RIGHT_SUM));
        for (String right : rightList) {
            if (result.errorHashSet.contains(right)) continue;
            result.addRight(right);
        }
    }

    private List<String> processEachFile(String errorFile) {
        File file = new File(errorFile);
        List<String> lines = new ArrayList<>();
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().length() > 0) lines.add(line);
                }
            } catch (IOException e) { e.printStackTrace(); }
        } else {
            // [调试] 如果找不到文件，不报错，因为这很正常
            // 但如果你确定文件必须存在，可以取消下面的注释
            // System.out.println("   [Info] File missing: " + file.getName());
        }
        return lines;
    }

    @Override
    public String toString() {
        // 重置计数
        HTRCount = 0; NTTCount = 0; INRCount = 0; ILCount = 0;
        NTCount = 0; ENCCount = 0; UNTCount = 0; DPSECount = 0;
        RCTPCount = 0; UBNTCount = 0; UTLCount = 0;
        error = 0; right = 0;

        for (Result result : mSet) {
            result.toString(); // 触发Result内部计数计算
            HTRCount += result.HTRCount;
            NTTCount += result.NTTCount;
            INRCount += result.INRCount;
            ILCount += result.ILCount;
            NTCount += result.NTCount;
            ENCCount += result.ENCCount;
            UNTCount += result.UNTCount;
            DPSECount += result.DPSECount;
            RCTPCount += result.RCTPCount;
            UBNTCount += result.UBNTCount;
            UTLCount += result.UTLCount;
            error += result.errorHashSet.size();
            right += result.rightSum.size();
        }
        return "";
    }
}