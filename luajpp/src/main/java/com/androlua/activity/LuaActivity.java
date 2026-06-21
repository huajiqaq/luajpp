package com.androlua.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.androlua.core.LuaContext;
import com.androlua.core.LuaGcable;
import com.androlua.internal.ActivityDelegate;
import com.androlua.internal.LuaConfig;
import com.androlua.internal.LuaLayout;
import com.androlua.internal.LuaLog;
import com.google.android.material.textview.MaterialTextView;

import org.luaj.Globals;
import org.luaj.LuaFunction;
import org.luaj.LuaTable;
import org.luaj.LuaValue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lua Activity 基类，完全不直接接触 LuaEngine。
 * 所有逻辑委托给 ActivityDelegate。
 */
@SuppressWarnings("unused")
public class LuaActivity extends AppCompatActivity implements LuaContext, ActivityDelegate.UICallback {

    public static final String ARG = "arg";
    public static final String DATA = "data";
    public static final String NAME = "name";

    private static final String DEFAULT_HOST_NAME = "activity";

    private final ActivityDelegate mDelegate = new ActivityDelegate(this, getHostName(), this);

    private ArrayAdapter<String> adapter;
    private ListView listView;
    private boolean isSetViewed;

    // ==================== UICallback 实现 ====================

    @Override
    public void applyDefaultView() {
        if (!isSetViewed) {
            if (listView == null) initListView();
            setContentView(listView);
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
                Insets i = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
    }

    @Override
    public boolean isSetViewed() {
        return isSetViewed;
    }

    @Override
    public void handleError(Exception e) {
        applyDefaultView();
        setResult(-1, new Intent().putExtra(DATA, e.toString()));
    }

    @Override
    public void handleVersionChanged(Bundle savedInstanceState) {
        Intent intent = getIntent();
        if (intent.getBooleanExtra("isVersionChanged", false) && savedInstanceState == null) {
            onVersionChanged(intent.getStringExtra("newVersionName"),
                    intent.getStringExtra("oldVersionName"));
        }
    }

    // ==================== 可覆盖的配置方法 ====================

    protected String getHostName() {
        return DEFAULT_HOST_NAME;
    }

    protected void onVersionChanged(String newVersionName, String oldVersionName) {
    }

    // ==================== 生命周期（纯转发） ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LuaConfig.runSafely(() -> {
                StrictMode.VmPolicy vmPolicy = new StrictMode.VmPolicy.Builder().permitNonSdkApiUsage().build();
                StrictMode.setVmPolicy(vmPolicy);
            }, "setVmPolicy");
        }

        mDelegate.initEngine(savedInstanceState);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        mDelegate.onNewIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mDelegate.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mDelegate.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mDelegate.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mDelegate.onStop();
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        isSetViewed = true;
    }

    @Override
    protected void onDestroy() {
        mDelegate.destroyEngine();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mDelegate.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // ==================== 视图设置 ====================

    public void setContentView(LuaTable view) {
        isSetViewed = true;
        setContentView(new LuaLayout(this).load(view, mDelegate.getLuaState()).touserdata(View.class));
    }

    public void setFragment(Fragment fragment) {
        setContentView(new View(this));
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
    }

    public void showLogs() {
        new AlertDialog.Builder(this).setTitle("Logs")
                .setAdapter(adapter, null)
                .setPositiveButton(android.R.string.ok, null).create().show();
    }

    private void initListView() {
        listView = new ListView(this);
        listView.setFastScrollEnabled(true);
        listView.setFastScrollAlwaysVisible(true);

        List<String> logs = LuaLog.getInstance().getLogs();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, logs) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                MaterialTextView view = (MaterialTextView) super.getView(position, convertView, parent);
                if (convertView == null) view.setTextIsSelectable(true);
                return view;
            }
        };
        listView.setAdapter(adapter);
    }

    // ==================== 菜单 ====================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return mDelegate.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        boolean handled = mDelegate.onOptionsItemSelected(item);
        return handled || super.onOptionsItemSelected(item);
    }

    // ==================== 键盘/触摸事件 ====================

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        return mDelegate.onKeyShortcut(keyCode, event) || super.onKeyShortcut(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return mDelegate.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return mDelegate.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        return mDelegate.onKeyLongPress(keyCode, event) || super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mDelegate.onTouchEvent(event) || super.onTouchEvent(event);
    }

    // ==================== 广播 ====================

    public Intent registerReceiver(IntentFilter filter) {
        return mDelegate.registerReceiver(filter);
    }

    // ==================== Service ====================

    public boolean bindService(int flag) {
        return mDelegate.bindService(flag);
    }

    public boolean bindService(ServiceConnection conn, int flag) {
        return mDelegate.bindService(conn, flag);
    }

    public boolean bindService(String path, ServiceConnection conn, int flag) {
        return mDelegate.bindService(path, conn, flag);
    }

    public void startService(String path, Object[] arg) throws FileNotFoundException {
        mDelegate.startService(path, arg);
    }

    public boolean stopService() {
        return mDelegate.stopService();
    }

    // ==================== Activity 跳转 ====================

    public void newActivity(String path) throws FileNotFoundException {
        mDelegate.newActivity(path);
    }

    public void newActivity(String path, Object[] arg) throws FileNotFoundException {
        mDelegate.newActivity(path, arg);
    }

    public void newActivity(int req, String path) throws FileNotFoundException {
        mDelegate.newActivity(req, path);
    }

    public void newActivity(int req, String path, Object[] arg) throws FileNotFoundException {
        mDelegate.newActivity(req, path, arg);
    }

    public void newActivity(int req, String path, Object[] arg, boolean newDocument) throws FileNotFoundException {
        mDelegate.newActivity(req, path, arg, newDocument);
    }

    public void newActivity(int req, String path, int in, int out, Object[] arg, boolean newDocument) throws FileNotFoundException {
        mDelegate.newActivity(req, path, arg, newDocument);
        overridePendingTransition(in, out);
    }

    // ==================== Activity 结果回调 ====================

    public void openDocumentTree(LuaFunction function) {
        startForResult(Intent.ACTION_OPEN_DOCUMENT_TREE, function);
    }

    public void openDocument(String type, LuaFunction function) {
        startForResult(Intent.ACTION_OPEN_DOCUMENT, function, type);
    }

    public void getDocument(String type, LuaFunction function) {
        startForResult(Intent.ACTION_GET_CONTENT, function, type);
    }

    public void createDocument(String type, String name, LuaFunction function) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(type);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        int code = function.hashCode();
        mDelegate.registerCallback(code, function);
        startActivityForResult(intent, code);
    }

    private void startForResult(String action, LuaFunction function, String... extras) {
        Intent intent = new Intent(action);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (extras.length > 0) intent.setType(extras[0]);
        int code = function.hashCode();
        mDelegate.registerCallback(code, function);
        startActivityForResult(intent, code);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mDelegate.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void result(Object[] data) {
        Intent res = new Intent();
        res.putExtra(NAME, getIntent().getStringExtra(NAME));
        res.putExtra(DATA, data);
        setResult(0, res);
        finish();
    }

    public void finish(boolean finishTask) {
        if (!finishTask) {
            super.finish();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = getIntent();
            if (intent != null && (intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_DOCUMENT) != 0)
                finishAndRemoveTask();
            else super.finish();
        } else {
            super.finish();
        }
    }

    // ==================== 快捷方式 ====================

    public void addShortcut(String label, String text) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory("android.intent.category.DEFAULT");
        intent.setClassName(getPackageName(), LuaActivity.class.getName());
        intent.setData(Uri.parse(text));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.content.pm.ShortcutManager scm = getSystemService(android.content.pm.ShortcutManager.class);
            if (scm == null) return;
            Drawable appIcon = getApplicationInfo().loadIcon(getPackageManager());
            Bitmap bitmap = Bitmap.createBitmap(
                    Math.max(appIcon.getIntrinsicWidth(), 96),
                    Math.max(appIcon.getIntrinsicHeight(), 96),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            appIcon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            appIcon.draw(canvas);
            android.content.pm.ShortcutInfo si = new android.content.pm.ShortcutInfo.Builder(this, text)
                    .setIcon(Icon.createWithBitmap(bitmap))
                    .setShortLabel(label)
                    .setIntent(intent)
                    .build();
            scm.requestPinShortcut(si, null);
        } else {
            Intent addShortcut = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
            addShortcut.putExtra(Intent.EXTRA_SHORTCUT_NAME, label);
            addShortcut.putExtra(Intent.EXTRA_SHORTCUT_INTENT, intent);
            addShortcut.putExtra("duplicate", false);
            sendBroadcast(addShortcut);
        }
        android.widget.Toast.makeText(this, "添加成功", android.widget.Toast.LENGTH_SHORT).show();
    }

    // ==================== 文件相关 ====================

    public Uri getUriForFile(File path) {
        return mDelegate.getUriForFile(path);
    }

    public String getPathFromUri(Uri uri) {
        return mDelegate.getPathFromUri(uri);
    }

    public void installApk(String path) {
        mDelegate.installApk(path);
    }

    public void openFile(String path) {
        mDelegate.openFile(path);
    }

    public void shareFile(String path) {
        mDelegate.shareFile(path);
    }

    // ==================== 委托方法 ====================

    public Object runFunc(String name, Object... args) {
        return mDelegate.runFunc(name, args);
    }

    @Override
    public void sendMsg(String msg) {
        mDelegate.sendMsg(msg);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    public void sendError(String title, Exception e) {
        mDelegate.sendError(title, e);
    }

    @Override
    public int getWidth() {
        return mDelegate.getWidth();
    }

    @Override
    public int getHeight() {
        return mDelegate.getWidth();
    }

    @Override
    public float getDensity() {
        return mDelegate.getDensity();
    }

    public void setDebug(boolean debug) {
        LuaConfig.setDebug(debug);
    }

    public Globals getLuaState() {
        return mDelegate.getLuaState();
    }

    public String getLuaDir() {
        return mDelegate.getLuaDir();
    }

    public String getLuaPath(String path) {
        return mDelegate.getLuaPath(path);
    }

    public String getLuaPath() {
        return mDelegate.getLuaPath();
    }

    public String getRootDir() {
        return mDelegate.getRootDir();
    }

    // ==================== LuaContext 接口实现 ====================

    @Override
    public InputStream findResource(String name) {
        return mDelegate.findResource(name);
    }

    @Override
    public String findFile(String filename) {
        return mDelegate.findFile(filename);
    }

    @Override
    public ArrayList<ClassLoader> getClassLoaders() {
        return mDelegate.getClassLoaders();
    }

    @Override
    public void call(String func, Object... args) {
        mDelegate.call(func, args);
    }

    @Override
    public void set(String name, Object value) {
        mDelegate.set(name, value);
    }

    @Override
    public String getLuaPath(String dir, String name) {
        return mDelegate.getLuaPath(dir, name);
    }

    @Override
    public String getLuaDir(String dir) {
        return mDelegate.getLuaDir(dir);
    }

    @Override
    public String getLuaExtDir() {
        return mDelegate.getLuaExtDir();
    }

    @Override
    public void setLuaExtDir(String dir) {
        mDelegate.setLuaExtDir(dir);
    }

    @Override
    public String getLuaExtDir(String dir) {
        return mDelegate.getLuaExtDir(dir);
    }

    @Override
    public String getLuaExtPath(String path) {
        return mDelegate.getLuaExtPath(path);
    }

    @Override
    public String getLuaExtPath(String dir, String name) {
        return mDelegate.getLuaExtPath(dir, name);
    }

    @Override
    public android.content.Context getContext() {
        return mDelegate.getContext();
    }

    @Override
    public Object doFile(String path, Object... arg) {
        return mDelegate.doFile(path, arg);
    }

    @Override
    public Map<String, ?> getSharedData() {
        return mDelegate.getSharedData();
    }

    @Override
    public Object getSharedData(String key) {
        return mDelegate.getSharedData(key);
    }

    @Override
    public Object getSharedData(String key, Object def) {
        return mDelegate.getSharedData(key, def);
    }

    @Override
    public boolean setSharedData(String key, Object value) {
        return mDelegate.setSharedData(key, value);
    }

    @Override
    public void regGc(LuaGcable obj) {
        mDelegate.regGc(obj);
    }

    @Override
    public Map<String, Object> getGlobalData() {
        return mDelegate.getGlobalData();
    }

    // ==================== LuaMetaTable 支持 ====================

    public LuaValue __index(LuaValue key) {
        return mDelegate.__index(key);
    }

    public void __newindex(LuaValue key, LuaValue value) {
        mDelegate.__newindex(key, value);
    }
}