package com.androlua.view;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.DownloadListener;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.androlua.activity.LuaActivity;
import com.androlua.core.LuaGcable;
import com.androlua.internal.LuaConfig;

import org.luaj.LuaError;
import org.luaj.LuaFunction;
import org.luaj.LuaValue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Lua WebView 组件
 * 支持 JavaScript 交互、文件上传、下载管理等功能
 */
@SuppressWarnings("unused")
@SuppressLint("ViewConstructor")
public class LuaWebView extends WebView implements LuaGcable {

    private static final String DOWNLOAD_DIR = "Download";

    private final LuaActivity mContext;
    private ProgressBar mProgressBar;
    private Dialog mFileDialog;
    private ListView mFileList;
    private String mCurrentDir = "/";
    private String mHtmlSource;

    private LuaValue mAdsFilter;
    private LuaFunction mOnReceivedTitleListener;
    private LuaFunction mOnReceivedIconListener;

    private ValueCallback<Uri> mUploadMessage;
    private final Map<Long, String[]> mDownloadMap = new HashMap<>();
    private DownloadBroadcastReceiver mDownloadReceiver;
    private OnDownloadCompleteListener mOnDownloadCompleteListener;

    private boolean mIsGced;

    @SuppressLint({"AddJavascriptInterface", "SetJavaScriptEnabled"})
    public LuaWebView(LuaActivity context) {
        super(context);
        mContext = context;
        context.regGc(this);

        initWebSettings();
        initProgressBar();
        setupJavascriptInterface();
        setWebViewClient(new LuaWebViewClientImpl());
        setWebChromeClient(new LuaWebChromeClient());
        setDownloadListener(new DownloadListenerImpl());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebSettings() {
        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setDisplayZoomControls(true);
        settings.setSupportZoom(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
    }

    private void initProgressBar() {
        DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
        int progressHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, displayMetrics);

        mProgressBar = new ProgressBar(mContext, null, android.R.attr.progressBarStyleHorizontal);
        mProgressBar.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, progressHeight, 0, 0));
        addView(mProgressBar);
    }

    private void setupJavascriptInterface() {
        addJavascriptInterface(new LuaJavaScriptInterface(), "lua");
        addJavascriptInterface(new HtmlSourceGetter(), "java_obj");
    }

    // ==================== 公开方法 ====================

    public String getSource() {
        return mHtmlSource;
    }

    public void setCookie(String url, String cookie) {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);

        String[] cookies = cookie.split(";");
        for (String c : cookies) {
            cookieManager.setCookie(url, c.trim());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.flush();
        } else {
            CookieSyncManager.getInstance().sync();
        }
    }

    public void setCookie(String cookie) {
        setCookie(getUrl(), cookie);
    }

    public String getCookie(String url) {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        return cookieManager.getCookie(url);
    }

    public String getCookie() {
        return getCookie(getUrl());
    }

    public void setProgressBarVisible(boolean visible) {
        mProgressBar.setVisibility(visible ? VISIBLE : GONE);
    }

    public void setProgressBar(ProgressBar progressBar) {
        mProgressBar = progressBar;
    }

    public void setAdsFilter(LuaFunction filter) {
        mAdsFilter = filter;
    }

    public void setOnDownloadCompleteListener(OnDownloadCompleteListener listener) {
        mOnDownloadCompleteListener = listener;
    }

    public void setOnReceivedTitleListener(LuaFunction listener) {
        mOnReceivedTitleListener = listener;
    }

    public void setOnReceivedIconListener(LuaFunction listener) {
        mOnReceivedIconListener = listener;
    }

    @SuppressLint("AddJavascriptInterface")
    public void addJsInterface(JsInterface object, String name) {
        addJavascriptInterface(new JsObjectWrapper(object), name);
    }

    public void setWebViewClient(LuaWebViewClient client) {
        setWebViewClient(new LuaWebViewClientWrapper(client));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && canGoBack()) {
            goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        LayoutParams lp = (LayoutParams) mProgressBar.getLayoutParams();
        lp.x = l;
        lp.y = t;
        mProgressBar.setLayoutParams(lp);
        super.onScrollChanged(l, t, oldl, oldt);
    }

    @Override
    public void destroy() {
        if (mDownloadReceiver != null) {
            try {
                mContext.unregisterReceiver(mDownloadReceiver);
            } catch (Exception ignored) {
            }
        }
        super.destroy();
    }

    @Override
    public void gc() {
        destroy();
        mIsGced = true;
    }

    @Override
    public boolean isGc() {
        return mIsGced;
    }

    // ==================== 文件选择 ====================

    private void showFileChooser(String dir) {
        if (mFileDialog == null) {
            createFileDialog();
        }

        File directory = new File(dir);
        ArrayList<String> items = new ArrayList<>();
        items.add("../");

        String[] files = directory.list();
        if (files != null) {
            Arrays.sort(files);
            for (String file : files) {
                if (new File(dir + file).isDirectory()) {
                    items.add(file + "/");
                } else {
                    items.add(file);
                }
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, items);
        mFileList.setAdapter(adapter);
        mFileDialog.setTitle(dir);
        mFileDialog.show();
    }

    private void createFileDialog() {
        mFileDialog = new Dialog(getContext());
        mFileList = new ListView(getContext());
        mFileList.setFastScrollEnabled(true);
        mFileList.setFastScrollAlwaysVisible(true);
        mFileDialog.setContentView(mFileList);

        mFileList.setOnItemClickListener((parent, view, position, id) -> {
            String item = ((TextView) view).getText().toString();

            if (item.equals("../")) {
                mCurrentDir = new File(mCurrentDir).getParent() + "/";
                showFileChooser(mCurrentDir);
                return;
            }

            String fullPath = mCurrentDir + item;
            File file = new File(fullPath);

            if (file.isDirectory()) {
                mCurrentDir = fullPath + "/";
                showFileChooser(mCurrentDir);
            } else if (mUploadMessage != null) {
                mUploadMessage.onReceiveValue(Uri.fromFile(file));
                mUploadMessage = null;
                mFileDialog.dismiss();
            }
        });
    }

    // ==================== 内部类 ====================

    private class HtmlSourceGetter {
        @JavascriptInterface
        public void get(String html) {
            mHtmlSource = html;
        }
    }

    private class LuaJavaScriptInterface {
        @JavascriptInterface
        public Object callLuaFunction(String name) {
            return mContext.getLuaState().get(name).jcall();
        }

        @JavascriptInterface
        public Object callLuaFunction(String name, String arg) {
            return mContext.getLuaState().get(name).jcall(arg);
        }

        @JavascriptInterface
        public Object doLuaString(String code) {
            return mContext.getLuaState().load(code).jcall();
        }
    }

    private record JsObjectWrapper(JsInterface mJsInterface) {
        @JavascriptInterface
        public String execute(String arg) {
            return mJsInterface.execute(arg);
        }
    }

    // ==================== WebViewClient ====================

    private class LuaWebViewClientImpl extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (mAdsFilter != null) {
                try {
                    if (mAdsFilter.call(url).toboolean()) {
                        return true;
                    }
                } catch (LuaError e) {
                    LuaConfig.logError("LuaWebView", e);
                }
            }

            if (url.startsWith("http") || url.startsWith("file")) {
                view.loadUrl(url);
            } else {
                try {
                    mContext.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    mContext.sendError("LuaWebView", e);
                }
            }
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            if (mAdsFilter != null) {
                try {
                    if (mAdsFilter.call(url).toboolean()) {
                        return new WebResourceResponse(null, null, null);
                    }
                } catch (LuaError e) {
                    LuaConfig.logError("LuaWebView", e);
                }
            }
            return null;
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            new AlertDialog.Builder(mContext)
                    .setTitle("SSL Error")
                    .setMessage(error.toString())
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> handler.proceed())
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> handler.cancel())
                    .setCancelable(false)
                    .show();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            if (url != null && url.toLowerCase().startsWith("http")) {
                view.loadUrl("javascript:window.java_obj.get('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');");
            }
        }
    }

    private class LuaWebViewClientWrapper extends WebViewClient {
        private final LuaWebViewClient mClient;

        public LuaWebViewClientWrapper(LuaWebViewClient client) {
            mClient = client;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return mClient.shouldOverrideUrlLoading(view, url);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            mClient.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            mClient.onPageFinished(view, url);
        }

        @Override
        public void onLoadResource(WebView view, String url) {
            mClient.onLoadResource(view, url);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            if (mAdsFilter != null) {
                try {
                    if (mAdsFilter.call(url).toboolean()) {
                        return new WebResourceResponse(null, null, null);
                    }
                } catch (LuaError e) {
                    LuaConfig.logError("LuaWebView", e);
                }
            }
            return mClient.shouldInterceptRequest(view, url);
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            mClient.onReceivedError(view, errorCode, description, failingUrl);
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            mClient.onReceivedSslError(view, handler, error);
        }

        @Override
        public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
            mClient.onReceivedHttpAuthRequest(view, handler, host, realm);
        }
    }

    // ==================== WebChromeClient ====================

    private class LuaWebChromeClient extends WebChromeClient {
        private final EditText mPromptInput = new EditText(mContext);

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            new AlertDialog.Builder(mContext)
                    .setTitle(url)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                    .setCancelable(false)
                    .show();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
            new AlertDialog.Builder(mContext)
                    .setTitle(url)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> result.cancel())
                    .setCancelable(false)
                    .show();
            return true;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
            mPromptInput.setText(defaultValue);
            new AlertDialog.Builder(mContext)
                    .setTitle(url)
                    .setMessage(message)
                    .setView(mPromptInput)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm(mPromptInput.getText().toString()))
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> result.cancel())
                    .setOnCancelListener(dialog -> result.cancel())
                    .show();
            return true;
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (newProgress == 100) {
                mProgressBar.setVisibility(GONE);
            } else {
                mProgressBar.setVisibility(VISIBLE);
                mProgressBar.setProgress(newProgress);
            }
            super.onProgressChanged(view, newProgress);
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            if (mOnReceivedTitleListener != null) {
                mOnReceivedTitleListener.jcall(title);
            }
        }

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            super.onReceivedIcon(view, icon);
            if (mOnReceivedIconListener != null) {
                mOnReceivedIconListener.jcall(icon);
            }
        }

        @Override
        public Bitmap getDefaultVideoPoster() {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }

        // 文件选择（兼容各种 Android 版本）
        @SuppressWarnings("unused")
        public void openFileChooser(ValueCallback<Uri> uploadMsg) {
            mUploadMessage = uploadMsg;
            mCurrentDir = mContext.getLuaExtDir();
            showFileChooser(mCurrentDir);
        }

        @SuppressWarnings("unused")
        public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType) {
            mUploadMessage = uploadMsg;
            mCurrentDir = mContext.getLuaExtDir();
            showFileChooser(mCurrentDir);
        }

        @SuppressWarnings("unused")
        public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
            openFileChooser(uploadMsg, acceptType);
        }
    }

    // ==================== DownloadListener ====================

    private class DownloadListenerImpl implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
            String filename = extractFilename(url, contentDisposition);
            String size = formatSize(contentLength);

            EditText filenameInput = new EditText(mContext);
            filenameInput.setText(filename);

            new AlertDialog.Builder(mContext)
                    .setTitle("Download")
                    .setMessage("Type: " + mimetype + "\nSize: " + size)
                    .setView(filenameInput)
                    .setPositiveButton("Download", (dialog, which) -> {
                        String finalName = filenameInput.getText().toString();
                        performDownload(url, mimetype, finalName, false);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton("Only Wifi", (dialog, which) -> {
                        String finalName = filenameInput.getText().toString();
                        performDownload(url, mimetype, finalName, true);
                    })
                    .show();
        }

        private String extractFilename(String url, String contentDisposition) {
            if (contentDisposition != null) {
                String pattern = "filename=\"";
                int start = contentDisposition.indexOf(pattern);
                if (start != -1) {
                    start += pattern.length();
                    int end = contentDisposition.indexOf('"', start);
                    if (end > start) {
                        return contentDisposition.substring(start, end);
                    }
                }
            }
            return Uri.parse(url).getLastPathSegment();
        }

        private String formatSize(long size) {
            if (size > 1024 * 1024) {
                return String.format(Locale.US, "%.2f MB", size / (1024.0 * 1024.0));
            } else if (size > 1024) {
                return String.format(Locale.US, "%.2f KB", size / 1024.0);
            }
            return size + " B";
        }

        private void performDownload(String url, String mimetype, String filename, boolean wifiOnly) {
            if (mDownloadReceiver == null) {
                mDownloadReceiver = new DownloadBroadcastReceiver();
                ContextCompat.registerReceiver(mContext, mDownloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_NOT_EXPORTED);
            }

            DownloadManager manager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

            String downloadDir = mContext.getLuaExtDir(DOWNLOAD_DIR);
            request.setDestinationInExternalPublicDir(new File(mContext.getLuaExtDir()).getName() + "/" + DOWNLOAD_DIR, filename);
            request.setTitle(filename);
            request.setDescription(url);
            request.setMimeType(mimetype);

            if (wifiOnly) {
                request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
            }

            long downloadId = manager.enqueue(request);
            mDownloadMap.put(downloadId, new String[]{new File(downloadDir, filename).getAbsolutePath(), mimetype});
        }
    }

    private class DownloadBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
            String[] data = mDownloadMap.get(downloadId);

            if (data != null && mOnDownloadCompleteListener != null) {
                mOnDownloadCompleteListener.onDownloadComplete(data[0], data[1]);
            }
        }
    }

    // ==================== 接口定义 ====================

    public interface OnDownloadCompleteListener {
        void onDownloadComplete(String filePath, String mimetype);
    }

    public interface JsInterface {
        @JavascriptInterface
        String execute(String arg);
    }

    public interface LuaWebViewClient {
        boolean shouldOverrideUrlLoading(WebView view, String url);

        void onPageStarted(WebView view, String url, Bitmap favicon);

        void onPageFinished(WebView view, String url);

        void onLoadResource(WebView view, String url);

        WebResourceResponse shouldInterceptRequest(WebView view, String url);

        void onReceivedError(WebView view, int errorCode, String description, String failingUrl);

        void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error);

        void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm);
    }
}
