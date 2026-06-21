package com.androlua.util;

import android.annotation.SuppressLint;
import android.content.Context;

import com.androlua.LuaApplication;
import com.androlua.internal.LuaLog;

import org.luaj.LuaString;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Adler32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@SuppressWarnings("unused")
public class LuaUtil {

    private static final int IO_SIZE = 8192;
    private static final byte[] ZIP_BUF = new byte[IO_SIZE];

    // ==================== 文件读写 ====================

    public static byte[] readAsset(Context context, String name) throws IOException {
        try (InputStream is = context.getAssets().open(name)) {
            return readAll(is);
        }
    }

    public static byte[] readAll(String path) throws IOException {
        try (InputStream is = new FileInputStream(path)) {
            return readAll(is);
        }
    }

    public static byte[] readAll(InputStream input) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(IO_SIZE * 128)) {
            byte[] buf = new byte[IO_SIZE];
            int n;
            while ((n = input.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    public static void copyAsset(Context context, String assetName, String destPath) throws IOException {
        try (OutputStream out = new FileOutputStream(destPath);
             InputStream in = context.getAssets().open(assetName)) {
            transfer(in, out);
        }
    }

    // ==================== 文件复制 ====================

    public static void copyFile(String src, String dest) {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dest)) {
            transfer(in, out);
        } catch (IOException e) {
            LuaLog.getInstance().add("copyFile: " + e.getMessage());
        }
    }

    public static boolean copyFile(InputStream in, OutputStream out) {
        return transfer(in, out);
    }

    public static boolean copyDir(String src, String dest) {
        return copyDir(new File(src), new File(dest));
    }

    public static boolean copyDir(File src, File dest) {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        if (src.isDirectory()) {
            File[] children = src.listFiles();
            if (children != null && children.length != 0) {
                for (File child : children) {
                    if (!copyDir(child, new File(dest, child.getName()))) return false;
                }
            } else {
                return !dest.exists() && dest.mkdirs();
            }
        } else {
            try (InputStream in = new FileInputStream(src);
                 OutputStream out = new FileOutputStream(dest)) {
                return transfer(in, out);
            } catch (IOException e) {
                LuaLog.getInstance().add("copyDir: " + e.getMessage());
                return false;
            }
        }
        return true;
    }

    private static boolean transfer(InputStream in, OutputStream out) {
        try {
            byte[] buf = new byte[IO_SIZE];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return true;
        } catch (Exception e) {
            LuaLog.getInstance().add("transfer: " + e.getMessage());
            return false;
        }
    }

    // ==================== 删除 ====================

    public static boolean rmDir(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) rmDir(child);
            }
        }
        file.setWritable(true);
        return file.delete();
    }

    public static void rmDir(File dir, String ext) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) rmDir(child, ext);
            }
        }
        if (dir.getName().endsWith(ext)) dir.delete();
    }

    // ==================== ZIP ====================

    public static byte[] readZip(String zipPath, String entryPath) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath)) {
            ZipEntry entry = zip.getEntry(entryPath);
            if (entry == null) throw new FileNotFoundException(entryPath + " in " + zipPath);
            try (InputStream is = zip.getInputStream(entry)) {
                return readAll(is);
            }
        }
    }

    public static void unzip(String zipPath) throws IOException {
        unzip(zipPath, new File(zipPath).getParent(), "");
    }

    public static void unzip(String zipPath, boolean namedDir) throws IOException {
        if (!namedDir) {
            unzip(zipPath);
            return;
        }
        String name = stripSuffix(new File(zipPath).getName());
        unzip(zipPath, new File(zipPath).getParent() + File.separator + name, "");
    }

    public static void unzip(String zipPath, String destDir) throws IOException {
        unzip(zipPath, destDir, "");
    }

    public static void unzip(String zipPath, String destDir, String prefix) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(prefix)) continue;
                if (entry.isDirectory()) {
                    File dir = new File(destDir, name);
                    if (!dir.exists()) dir.mkdirs();
                } else {
                    File out = new File(destDir, name);
                    File dir = out.getParentFile();
                    if (dir != null && !dir.exists() && !dir.mkdirs()) {
                        throw new IOException("Failed to create: " + dir);
                    }
                    try (OutputStream fos = new FileOutputStream(out);
                         InputStream is = zip.getInputStream(entry)) {
                        transfer(is, fos);
                    }
                }
            }
        }
    }

    public static boolean zip(String srcPath) {
        return zip(srcPath, new File(srcPath).getParent());
    }

    public static boolean zip(String srcPath, String destDir) {
        return zip(srcPath, destDir, new File(srcPath).getName() + ".zip");
    }

    public static boolean zip(String srcPath, String destDir, String zipName) {
        File zipFile = new File(destDir, zipName);
        File parent = zipFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             CheckedOutputStream checked = new CheckedOutputStream(fos, new Adler32());
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(checked))) {
            compress(new File(srcPath), zos, "");
            checked.getChecksum().getValue();
            return true;
        } catch (IOException e) {
            LuaLog.getInstance().addError("LuaUtil", e);
            return false;
        }
    }

    private static void compress(File file, ZipOutputStream zos, String prefix) {
        if (file.isFile()) {
            try (FileInputStream fis = new FileInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(fis, IO_SIZE)) {
                zos.putNextEntry(new ZipEntry(prefix + file.getName()));
                int n;
                while ((n = bis.read(ZIP_BUF)) != -1) zos.write(ZIP_BUF, 0, n);
                zos.closeEntry();
            } catch (IOException e) {
                LuaLog.getInstance().addError("LuaUtil", e);
            }
        } else if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    compress(child, zos, child.isDirectory() ? prefix + child.getName() + "/" : prefix);
                }
            }
        }
    }

    // ==================== 文件类型 ====================

    private static final Map<String, String> FILE_SIG = new HashMap<>();

    static {
        String[][] sigs = {
                {"FFD8FF", "jpg"}, {"89504E47", "png"}, {"47494638", "gif"},
                {"49492A00", "tif"}, {"424D", "bmp"}, {"41433130", "dwg"},
                {"38425053", "psd"}, {"7B5C727466", "rtf"}, {"3C3F786D6C", "xml"},
                {"68746D6C3E", "html"}, {"D0CF11E0", "doc"}, {"255044462D312E", "pdf"},
                {"504B0304", "docx"}, {"52617221", "rar"}, {"57415645", "wav"},
                {"41564920", "avi"}, {"1F8B08", "gz"}
        };
        for (String[] s : sigs) FILE_SIG.put(s[0], s[1]);
    }

    public static String getFileType(String path) {
        try (InputStream is = new FileInputStream(path)) {
            return getFileType(is);
        } catch (IOException e) {
            LuaLog.getInstance().addError("LuaUtil", e);
        }
        return "unknown";
    }

    public static String getFileType(File file) {
        try (InputStream is = new FileInputStream(file)) {
            return getFileType(is);
        } catch (IOException e) {
            LuaLog.getInstance().addError("LuaUtil", e);
        }
        return "unknown";
    }

    public static String getFileType(InputStream is) {
        String header = readHeader(is);
        return header != null ? FILE_SIG.getOrDefault(header, "unknown") : "unknown";
    }

    private static String readHeader(InputStream is) {
        try (is) {
            byte[] b = new byte[4];
            int n = is.read(b);
            if (n <= 0) return null;
            StringBuilder sb = new StringBuilder(n * 2);
            for (int i = 0; i < n; i++) sb.append(String.format("%02X", b[i] & 0xFF));
            return sb.toString();
        } catch (IOException ignored) {
            return null;
        }
    }

    // ==================== 哈希 ====================

    public static String getFileMD5(String path) {
        return getFileMD5(new File(path));
    }

    public static String getFileMD5(File file) {
        try (InputStream in = new FileInputStream(file)) {
            return digest(in, "MD5");
        } catch (IOException e) {
            return null;
        }
    }

    public static String getFileMD5(InputStream in) {
        return digest(in, "MD5");
    }

    public static String getFileSha1(String path) {
        return getFileSha1(new File(path));
    }

    public static String getFileSha1(File file) {
        try (InputStream in = new FileInputStream(file)) {
            return digest(in, "SHA-1");
        } catch (IOException e) {
            return null;
        }
    }

    public static String getFileSha1(InputStream in) {
        return digest(in, "SHA-1");
    }

    private static String digest(InputStream in, String algo) {
        try (in) {
            MessageDigest md = MessageDigest.getInstance(algo);
            byte[] buf = new byte[IO_SIZE];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            return new BigInteger(1, md.digest()).toString(16);
        } catch (Exception e) {
            LuaLog.getInstance().addError("LuaUtil", e);
            return null;
        }
    }

    // ==================== 保存 ====================

    public static void save(String path, String text) {
        try {
            File parent = new File(path).getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileOutputStream out = new FileOutputStream(path)) {
                out.write(text.getBytes());
            }
        } catch (Exception e) {
            LuaLog.getInstance().addError("LuaUtil", e);
        }
    }

    public static void save(OutputStream out, LuaString text) {
        try {
            out.write(text.m_bytes, text.m_offset, text.m_length);
            out.close();
        } catch (Exception e) {
            LuaLog.getInstance().addError("LuaUtil", e);
        }
    }

    // ==================== 工具 ====================

    @SuppressLint("SimpleDateFormat")
    public static String getTimeName(String name, String ext) {
        return name + new SimpleDateFormat("_yyyy-MM-dd-HH-mm-ss").format(new Date()) + ext;
    }

    public static float getSimilarityRatio(String src, String target) {
        int max = Math.max(src.length(), target.length());
        return max == 0 ? 1f : 1 - (float) levenshtein(src, target) / max;
    }

    private static int levenshtein(String src, String tgt) {
        int m = src.length(), n = tgt.length();
        if (m == 0) return n;
        if (n == 0) return m;
        int[] prev = new int[n + 1], curr = new int[n + 1];
        for (int j = 0; j <= n; j++) prev[j] = j;
        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            char c1 = src.charAt(i - 1);
            for (int j = 1; j <= n; j++) {
                int cost = (c1 == tgt.charAt(j - 1)
                        || c1 == Character.toLowerCase(tgt.charAt(j - 1))
                        || Character.toLowerCase(c1) == tgt.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[n];
    }

    public static LuaString readZipFile(String zipPath, String entryPath) throws IOException {
        return LuaString.valueOf(readZip(zipPath, entryPath));
    }

    public static LuaString readApkFile(String entryPath) throws IOException {
        try (ZipFile zip = new ZipFile(LuaApplication.getInstance().getPackageCodePath())) {
            ZipEntry entry = zip.getEntry(entryPath);
            if (entry == null) throw new FileNotFoundException(entryPath + " in apk");
            try (InputStream is = zip.getInputStream(entry)) {
                return LuaString.valueOf(readAll(is));
            }
        }
    }

    private static String stripSuffix(String name) {
        int i = name.lastIndexOf('.');
        if (i > 0) name = name.substring(0, i);
        i = name.indexOf('_');
        if (i > 0) name = name.substring(0, i);
        i = name.indexOf('(');
        if (i > 0) name = name.substring(0, i);
        return name;
    }
}