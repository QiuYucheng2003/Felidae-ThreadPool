package ac.count;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class Result {

    // ... ErrorInfo 内部类保持不变，这里省略 ...
    static class ErrorInfo {
        // (保持你原来的代码内容不变)
        String asyncTaskClass = null;
        ArrayList<String> errorHTRSumWithExcutePath = new ArrayList<>();
        ArrayList<String> errorNTTSumWithExcutePath = new ArrayList<>();
        ArrayList<String> errorINRSumWithExcutePath = new ArrayList<>();
        ArrayList<String> errorILSumWithExcutePath = new ArrayList<>();
        ArrayList<String> errorNTSumWithExcutePath = new ArrayList<>();
        ArrayList<String> errorENCSumWithExcutePath = new ArrayList<>();
        ArrayList<String> errorUNTSumWithExcutePath = new ArrayList<>();
        ArrayList<String> errorDPSESumWithExcutePath = new ArrayList<>();
        ArrayList<String> errorRCTPSumWithExcutePath = new ArrayList<>();
        ArrayList<String> errorUBNTSumWithExcutePath = new ArrayList<>();
        ArrayList<String> errorUTLSumWithExcutePath = new ArrayList<>();
        Set<String> errorHTRSumWithoutExcutePath = new HashSet<>();
        Set<String> errorNTTSumWithoutExcutePath = new HashSet<>();
        Set<String> errorINRSumWithoutExcutePath = new HashSet<>();
        Set<String> errorILSumWithoutExcutePath = new HashSet<>();
        Set<String> errorNTSumWithoutExcutePath = new HashSet<>();
        Set<String> errorENCSumWithoutExcutePath = new HashSet<>();
        Set<String> errorUNTSumWithoutExcutePath = new HashSet<>();
        Set<String> errorDPSESumWithoutExcutePath = new HashSet<>();
        Set<String> errorRCTPSumWithoutExcutePath = new HashSet<>();
        Set<String> errorUBNTSumWithoutExcutePath = new HashSet<>();
        Set<String> errorUTLSumWithoutExcutePath = new HashSet<>();

        // ... addError 方法保持不变 ...
        void addHTRError(String key) { errorHTRSumWithExcutePath.add(key); errorHTRSumWithoutExcutePath.add(key); }
        void addNTTError(String key) { errorNTTSumWithExcutePath.add(key); errorNTTSumWithoutExcutePath.add(key); }
        void addINRError(String key) { errorINRSumWithExcutePath.add(key); errorINRSumWithoutExcutePath.add(key); }
        void addILError(String key) { errorILSumWithExcutePath.add(key); errorILSumWithoutExcutePath.add(key); }
        void addNTError(String key) { errorNTSumWithExcutePath.add(key); errorNTSumWithoutExcutePath.add(key); }
        void addENCError(String key) { errorENCSumWithExcutePath.add(key); errorENCSumWithoutExcutePath.add(key); }
        void addUNTError(String key) { errorUNTSumWithExcutePath.add(key); errorUNTSumWithoutExcutePath.add(key); }
        void addDPSEError(String key) { errorDPSESumWithExcutePath.add(key); errorDPSESumWithoutExcutePath.add(key); }
        void addRCTPError(String key) { errorRCTPSumWithExcutePath.add(key); errorRCTPSumWithoutExcutePath.add(key); }
        void addUBNTError(String key) { errorUBNTSumWithExcutePath.add(key); errorUBNTSumWithoutExcutePath.add(key); }
        void addUTLError(String key) { errorUTLSumWithExcutePath.add(key); errorUTLSumWithoutExcutePath.add(key); }
    }
    // ... ErrorInfo 结束 ...

    String appName = null;
    ArrayList<String> errorSum = new ArrayList<>();
    ArrayList<String> rightSum = new ArrayList<>();
    HashMap<String, ErrorInfo> asyncClass = new HashMap<>();
    HashSet<String> rightInstanceHashSet = new HashSet<>();
    HashSet<String> errorHashSet = new HashSet<>();
    int sootTime = 0;
    int leopardTime = 0;

    // 计数器
    int HTRCount = 0; int NTTCount = 0; int INRCount = 0; int ILCount = 0;
    int NTCount = 0; int ENCCount = 0; int UNTCount = 0; int DPSECount = 0;
    int RCTPCount = 0; int UBNTCount = 0; int UTLCount = 0;

    Result(String apkName) { appName = apkName; }

    // ... 省略中间的 addRight, addError, setSootTime 等 setter/getter，保持不变 ...
    public void addRight(String key) { rightSum.add(key); rightInstanceHashSet.add(key); }
    public void addError(String key) { errorSum.add(key); errorHashSet.add(key); }
    public void setSootTime(int sootTime) { this.sootTime = sootTime; }
    public void setLeopardTime(int leopardTime) { this.leopardTime = leopardTime; }
    public void addINR(String className, String key) { getErrorInfo(className).addINRError(key); }
    public void addHTR(String className, String key) { getErrorInfo(className).addHTRError(key); }
    public void addIL(String className, String key) { getErrorInfo(className).addILError(key); }
    public void addNTT(String className, String key) { getErrorInfo(className).addNTTError(key); }
    public void addNT(String className, String key) { getErrorInfo(className).addNTError(key); }
    public void addENC(String className, String key) { getErrorInfo(className).addENCError(key); }
    public void addUNT(String className, String key) { getErrorInfo(className).addUNTError(key); }
    public void addDPSE(String className, String key) { getErrorInfo(className).addDPSEError(key); }
    public void addRCTP(String className, String key) { getErrorInfo(className).addRCTPError(key); }
    public void addUBNT(String className, String key) { getErrorInfo(className).addUBNTError(key); }
    public void addUTL(String className, String key) { getErrorInfo(className).addUTLError(key); }

    public ErrorInfo getErrorInfo(String className) {
        if (asyncClass.containsKey(className)) { return asyncClass.get(className); }
        ErrorInfo errorInfo = new ErrorInfo();
        errorInfo.asyncTaskClass = className;
        asyncClass.put(className, errorInfo);
        return errorInfo;
    }

    // 【修正核心】添加这个新方法，显式计算所有统计数据
    public void calculateCounts() {
        // 重置
        HTRCount = 0; NTTCount = 0; INRCount = 0; ILCount = 0;
        NTCount = 0; ENCCount = 0; UNTCount = 0; DPSECount = 0;
        RCTPCount = 0; UBNTCount = 0; UTLCount = 0;

        for (String key : asyncClass.keySet()) {
            ErrorInfo errorInfo = asyncClass.get(key);

            HTRCount += errorInfo.errorHTRSumWithoutExcutePath.size();
            NTTCount += errorInfo.errorNTTSumWithoutExcutePath.size();
            INRCount += errorInfo.errorINRSumWithoutExcutePath.size();
            ILCount += errorInfo.errorILSumWithoutExcutePath.size();

            NTCount += errorInfo.errorNTSumWithoutExcutePath.size();
            ENCCount += errorInfo.errorENCSumWithoutExcutePath.size();
            UNTCount += errorInfo.errorUNTSumWithoutExcutePath.size();
            DPSECount += errorInfo.errorDPSESumWithoutExcutePath.size();
            RCTPCount += errorInfo.errorRCTPSumWithoutExcutePath.size();
            UBNTCount += errorInfo.errorUBNTSumWithoutExcutePath.size();
            UTLCount += errorInfo.errorUTLSumWithoutExcutePath.size();
        }
    }

    @Override
    public String toString() {
        calculateCounts(); // 为了兼容旧代码，这里调用一下
        return "";
    }
}