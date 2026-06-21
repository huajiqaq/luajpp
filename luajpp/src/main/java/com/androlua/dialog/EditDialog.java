package com.androlua.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.os.IBinder;
import android.text.InputFilter;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatEditText;

import java.util.Objects;

/**
 * 文本输入对话框。
 */

@SuppressWarnings("unused")
public class EditDialog implements DialogInterface.OnClickListener {
    private final AppCompatEditText mEditText;
    private final EditDialogCallback mCallback;
    private final AlertDialog mDialog;
    private int mMaxLength;

    public EditDialog(Context context, String title, String defaultText, EditDialogCallback callback) {
        this(context, title, defaultText, callback, 0);
    }

    public EditDialog(Context context, String title, String defaultText, EditDialogCallback callback, int maxLength) {
        mCallback = callback;
        mMaxLength = maxLength;

        mEditText = new AppCompatEditText(context) {
            @Override
            protected void onTextChanged(CharSequence text, int start, int before, int count) {
                super.onTextChanged(text, start, before, count);
                if (mDialog != null && mMaxLength > 0) {
                    mDialog.setTitle(title + " " + text.length() + "/" + mMaxLength);
                }
            }
        };

        if (maxLength > 0) {
            mEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        }

        mDialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(mEditText)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, this)
                .setCancelable(false)
                .create();

        Window win = mDialog.getWindow();
        if (win != null) {
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        mEditText.setText(defaultText);
    }

    public void show() {
        if (mDialog == null) return;
        mDialog.show();
        mEditText.setFocusable(true);
        mEditText.requestFocus();
    }

    public void show(IBinder token) {
        if (mDialog == null) return;
        if (token == null) {
            show();
            return;
        }
        Window win = mDialog.getWindow();
        if (win == null) return;
        WindowManager.LayoutParams attrs = win.getAttributes();
        attrs.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        win.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        attrs.token = token;
        win.setAttributes(attrs);
        mDialog.show();
    }

    public void show(int maxLength) {
        mMaxLength = maxLength;
        if (maxLength > 0) {
            mEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        }
        show();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (mCallback != null) {
            mCallback.onCallback(Objects.requireNonNull(mEditText.getText()).toString());
        }
    }

    public interface EditDialogCallback {
        void onCallback(String text);
    }
}
