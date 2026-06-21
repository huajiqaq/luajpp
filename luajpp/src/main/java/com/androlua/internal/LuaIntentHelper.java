package com.androlua.internal;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.androlua.activity.LuaActivity;
import com.androlua.activity.LuaActivityX;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Activity/文件操作工具类。
 */
public final class LuaIntentHelper {

    private static final String ARG = "arg";
    private static final String NAME = "name";

    private LuaIntentHelper() {
    }

    public static void newActivity(Context context, String luaDir, String path) throws FileNotFoundException {
        newActivity(context, luaDir, 1, path, null, false);
    }

    public static void newActivity(Context context, String luaDir, String path, Object[] arg) throws FileNotFoundException {
        newActivity(context, luaDir, 1, path, arg, false);
    }

    public static void newActivity(Context context, String luaDir, int req, String path) throws FileNotFoundException {
        newActivity(context, luaDir, req, path, null, false);
    }

    public static void newActivity(Context context, String luaDir, int req, String path, Object[] arg) throws FileNotFoundException {
        newActivity(context, luaDir, req, path, arg, false);
    }

    public static void newActivity(Context context, String luaDir, int req, String path, Object[] arg, boolean newDocument) throws FileNotFoundException {
        Intent intent = new Intent(context, newDocument ? LuaActivityX.class : LuaActivity.class);
        intent.putExtra(NAME, path);

        String fullPath = resolveLuaPath(luaDir, path);

        if (newDocument && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        }

        intent.setData(Uri.parse("file://" + fullPath));
        if (arg != null) intent.putExtra(ARG, arg);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * 解析 Lua 脚本路径
     */
    @NonNull
    public static String resolveLuaPath(String luaDir, String path) throws FileNotFoundException {
        String fullPath = path.startsWith("/") ? path : luaDir + "/" + path;
        File f = new File(fullPath);

        if (f.isDirectory()) {
            File mainLua = new File(fullPath + "/main.lua");
            if (mainLua.exists()) fullPath = mainLua.getAbsolutePath();
        } else if (!f.exists() && !fullPath.endsWith(".lua")) {
            fullPath += ".lua";
        }

        if (!new File(fullPath).exists()) {
            throw new FileNotFoundException(fullPath);
        }
        return fullPath;
    }

    public static Uri getUriForFile(Context context, File path) {
        return FileProvider.getUriForFile(context, context.getPackageName(), path);
    }

    public static String getPathFromUri(Context context, Uri uri) {
        if (uri == null) return null;
        String scheme = uri.getScheme();
        if (scheme == null) return null;

        if ("content".equals(scheme)) {
            try (Cursor cursor = context.getContentResolver().query(uri,
                    new String[]{android.provider.MediaStore.Images.Media.DATA}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(android.provider.MediaStore.Images.Media.DATA);
                    if (idx >= 0) return cursor.getString(idx);
                }
            } catch (Exception ignored) {
            }
        } else if ("file".equals(scheme)) {
            return uri.getPath();
        }
        return null;
    }

    public static String getMimeType(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0) {
            String ext = name.substring(lastDot + 1);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) return mime;
        }
        return "application/octet-stream";
    }

    public static void installApk(Context context, String path) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        File file = new File(path);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(getUriForFile(context, file), getMimeType(file));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void openFile(Context context, String path) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        File file = new File(path);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.setDataAndType(getUriForFile(context, file), getMimeType(file));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void shareFile(Context context, String path) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        File file = new File(path);
        intent.setType("*/*");
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_STREAM, getUriForFile(context, file));
        context.startActivity(Intent.createChooser(intent, file.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
