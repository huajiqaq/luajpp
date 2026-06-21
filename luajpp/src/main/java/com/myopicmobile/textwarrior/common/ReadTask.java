package com.myopicmobile.textwarrior.common;

import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.ContentLoadingProgressBar;

import com.androlua.view.LuaEditor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;

/**
 * Created by Administrator on 2018/07/18 0018.
 */
public class ReadTask {

    private final WeakReference<AlertDialog> _dlgRef;
    private final WeakReference<ContentLoadingProgressBar> _progressRef;
    private final WeakReference<LuaEditor> _editRef;
    private final File _file;
    private final long _len;
    private int _total = 0;
    private final Handler _handler = new Handler(Looper.getMainLooper());
    private boolean _isRunning = false;

    public ReadTask(LuaEditor edit, String fileName) {
        this(edit, new File(fileName));
    }

    public ReadTask(LuaEditor edit, File file) {
        _editRef = new WeakReference<>(edit);
        _file = file;
        _len = file.length();

        ContentLoadingProgressBar progressBar = new ContentLoadingProgressBar(edit.getContext());
        progressBar.setMax(100);

        AlertDialog dlg = new AlertDialog.Builder(edit.getContext())
                .setTitle("正在打开")
                .setView(progressBar)
                .setCancelable(false)
                .create();

        _dlgRef = new WeakReference<>(dlg);
        _progressRef = new WeakReference<>(progressBar);
    }

    public int getMin() {
        return 0;
    }

    public int getMax() {
        return (int) _len;
    }

    public int getCurrent() {
        return _total;
    }

    public void start() {
        if (_isRunning) return;
        _isRunning = true;

        AlertDialog dlg = _dlgRef.get();
        if (dlg != null) {
            dlg.show();
        }

        new Thread(this::doInBackground).start();
    }

    private void doInBackground() {
        LuaEditor edit = _editRef.get();
        if (edit == null) {
            postResult(null);
            return;
        }

        try (FileInputStream fi = new FileInputStream(_file)) {
            byte[] buf = readAll(fi);
            final String result = new String(buf);
            postResult(result);
        } catch (Exception e) {
            postResult(null);
        }
    }

    private byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(4096);
        byte[] buffer = new byte[4096];
        int n;
        _total = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            _total += n;
            if (_len > 0) {
                final int progress = (int) ((long) _total * 100 / _len);
                postProgress(progress);
            }
        }
        byte[] ret = output.toByteArray();
        output.close();
        return ret;
    }

    private void postProgress(final int progress) {
        _handler.post(() -> {
            ContentLoadingProgressBar progressBar = _progressRef.get();
            if (progressBar != null && progress >= 0 && progress <= 100) {
                progressBar.setProgress(progress);
            }
        });
    }

    private void postResult(final String result) {
        _isRunning = false;
        _handler.post(() -> {
            AlertDialog dlg = _dlgRef.get();
            if (dlg != null && dlg.isShowing()) {
                dlg.dismiss();
            }

            LuaEditor edit = _editRef.get();
            if (edit != null && result != null) {
                edit.setText(result);
            }
        });
    }
}