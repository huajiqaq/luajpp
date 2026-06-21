package com.androlua.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import androidx.annotation.NonNull;

import com.androlua.internal.LuaConfig;
import com.androlua.internal.LuaLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 全局异常捕获处理类
 * 当程序发生未捕获异常时，收集设备信息和错误日志并保存到文件
 */
public class CrashHandler implements UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    private static final String CRASH_DIR = "crash";
    private static final String FILE_PREFIX = "crash";
    private static final String FILE_EXTENSION = ".log";
    private static final String DATE_FORMAT = "yyyy-MM-dd-HH-mm-ss";

    private static final CrashHandler sInstance = new CrashHandler();

    private UncaughtExceptionHandler mDefaultHandler;
    private Context mContext;
    private final Map<String, String> mDeviceInfo = new LinkedHashMap<>();
    private final DateFormat mDateFormatter = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());

    private CrashHandler() {
    }

    public static CrashHandler getInstance() {
        return sInstance;
    }

    /**
     * 初始化崩溃捕获器
     *
     * @param context 上下文
     */
    public void init(Context context) {
        mContext = context.getApplicationContext();
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(@NonNull Thread thread, @NonNull Throwable ex) {
        if (!handleException(ex) && mDefaultHandler != null) {
            mDefaultHandler.uncaughtException(thread, ex);
        }
        // 不主动杀死进程，让系统处理
    }

    /**
     * 处理未捕获异常
     *
     * @param ex 异常
     * @return true 表示已处理
     */
    private boolean handleException(Throwable ex) {
        if (ex == null) {
            return false;
        }
        collectDeviceInfo();
        saveCrashLog(ex);
        return true;
    }

    /**
     * 收集设备信息
     */
    private void collectDeviceInfo() {
        collectPackageInfo();
        collectBuildInfo();
        collectVersionInfo();
    }

    private void collectPackageInfo() {
        try {
            PackageManager pm = mContext.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(mContext.getPackageName(), PackageManager.GET_ACTIVITIES);
            if (pi != null) {
                mDeviceInfo.put("versionName", pi.versionName != null ? pi.versionName : "null");
                mDeviceInfo.put("versionCode", String.valueOf(pi.versionCode));
            }
        } catch (PackageManager.NameNotFoundException e) {
            LuaConfig.logError(TAG, e);
        }
    }

    private void collectBuildInfo() {
        collectFields(Build.class);
    }

    private void collectVersionInfo() {
        collectFields(Build.VERSION.class);
    }

    private void collectFields(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(null);
                String stringValue = formatFieldValue(value);
                mDeviceInfo.put(field.getName(), stringValue);
                LuaConfig.log(TAG + ": " + field.getName() + " : " + stringValue);
            } catch (Exception e) {
                LuaConfig.logError(TAG, e);
            }
        }
    }

    private String formatFieldValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String[]) {
            return Arrays.toString((String[]) value);
        }
        return value.toString();
    }

    /**
     * 保存崩溃日志到文件
     *
     * @param ex 异常
     */
    private void saveCrashLog(Throwable ex) {
        String logContent = buildLogContent(ex);

        if (!isExternalStorageWritable()) {
            LuaConfig.logWarn("External storage not writable, cannot save crash log");
            return;
        }

        try {
            String crashDirPath = getCrashDirectory();
            File crashDir = new File(crashDirPath);
            if (!crashDir.exists() && !crashDir.mkdirs()) {
                LuaLog.getInstance().addError(TAG, new IOException("Failed to create crash directory: " + crashDirPath));
                return;
            }

            String fileName = generateFileName();
            File logFile = new File(crashDir, fileName);

            try (FileOutputStream fos = new FileOutputStream(logFile)) {
                fos.write(logContent.getBytes());
            }

            LuaLog.getInstance().add("Crash log saved: " + logFile.getAbsolutePath());

        } catch (Exception e) {
            LuaConfig.logError(TAG, e);
        }
    }

    private String buildLogContent(Throwable ex) {
        StringBuilder sb = new StringBuilder();

        // 设备信息
        for (Map.Entry<String, String> entry : mDeviceInfo.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }

        // 异常堆栈
        sb.append("\n").append(getStackTraceString(ex));

        return sb.toString();
    }

    private String getStackTraceString(Throwable ex) {
        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        ex.printStackTrace(printWriter);

        Throwable cause = ex.getCause();
        while (cause != null) {
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        }
        printWriter.close();

        return writer.toString();
    }

    private boolean isExternalStorageWritable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    private String getCrashDirectory() {
        File externalDir = mContext.getExternalFilesDir(null);
        if (externalDir != null) {
            return externalDir.getAbsolutePath() + File.separator + CRASH_DIR;
        }
        return mContext.getFilesDir().getAbsolutePath() + File.separator + CRASH_DIR;
    }

    private String generateFileName() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String date = mDateFormatter.format(new Date());
        return FILE_PREFIX + "-" + date + "-" + timestamp + FILE_EXTENSION;
    }
}
