package com.myopicmobile.textwarrior.common;

import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.ContentLoadingProgressBar;

import com.androlua.view.LuaEditor;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;

/**
 * Created by Administrator on 2018/07/18 0018.
 */
public class WriteTask {

    private final WeakReference<AlertDialog> _dlgRef;
    private final WeakReference<ContentLoadingProgressBar> _progressRef;
    private final WeakReference<LuaEditor> _editRef;
    private final File _file;
    private final long _len;
    private final Handler _handler = new Handler(Looper.getMainLooper());
    private int _total = 0;
    private boolean _isRunning = false;

    public WriteTask(LuaEditor edit, String fileName) {
        this(edit, new File(fileName));
    }

    public WriteTask(LuaEditor edit, File file) {
        _editRef = new WeakReference<>(edit);
        _file = file;
        _len = file.length();

        ContentLoadingProgressBar progressBar = new ContentLoadingProgressBar(edit.getContext());
        progressBar.setMax(100);

        AlertDialog dlg = new AlertDialog.Builder(edit.getContext())
                .setTitle("正在保存")
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
            postResult(false);
            return;
        }

        BufferedWriter writer = null;
        try {
            String text = edit.getText().toString();
            writer = new BufferedWriter(
                    new OutputStreamWriter(
                            new BufferedOutputStream(
                                    new FileOutputStream(_file)
                            )
                    )
            );

            int chunkSize = 4096;
            int length = text.length();
            int written = 0;

            while (written < length) {
                int end = Math.min(written + chunkSize, length);
                writer.write(text, written, end - written);
                written = end;
                _total = written;
                if (_len > 0) {
                    final int progress = (int) ((long) written * 100 / _len);
                    postProgress(progress);
                }
            }

            writer.flush();
            postResult(true);

        } catch (Exception e) {
            postResult(false);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void postProgress(final int progress) {
        _handler.post(() -> {
            ContentLoadingProgressBar progressBar = _progressRef.get();
            if (progressBar != null && progress >= 0 && progress <= 100) {
                progressBar.setProgress(progress);
            }
        });
    }

    private void postResult(final boolean result) {
        _isRunning = false;
        _handler.post(() -> {
            AlertDialog dlg = _dlgRef.get();
            if (dlg != null && dlg.isShowing()) {
                dlg.dismiss();
            }
        });
    }
}