package com.androlua.dialog;

import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.androlua.internal.LuaConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * LuaJ 对话框封装类
 * 支持列表、单选、多选、按钮回调等功能
 * Created by Administrator on 2017/02/04 0004.
 */
@SuppressWarnings("unused")
public class LuaDialog extends AlertDialog implements DialogInterface.OnClickListener {

    private final Context mContext;
    private final ListView mListView;
    private String mTitle;
    private String mMessage;
    private View mCustomView;
    private OnClickListener mOnClickListener;

    public LuaDialog(Context context) {
        super(context);
        mContext = context;
        mListView = new ListView(mContext);
    }

    public LuaDialog(Context context, int theme) {
        super(context, theme);
        mContext = context;
        mListView = new ListView(mContext);
    }

    // ==================== 按钮设置 ====================

    public void setButton(CharSequence text) {
        setOkButton(text);
    }

    public void setButton1(CharSequence text) {
        setButton(DialogInterface.BUTTON_POSITIVE, text, this);
    }

    public void setButton2(CharSequence text) {
        setButton(DialogInterface.BUTTON_NEGATIVE, text, this);
    }

    public void setButton3(CharSequence text) {
        setButton(DialogInterface.BUTTON_NEUTRAL, text, this);
    }

    public void setPosButton(CharSequence text) {
        setButton(DialogInterface.BUTTON_POSITIVE, text, this);
    }

    public void setNegButton(CharSequence text) {
        setButton(DialogInterface.BUTTON_NEGATIVE, text, this);
    }

    public void setNeuButton(CharSequence text) {
        setButton(DialogInterface.BUTTON_NEUTRAL, text, this);
    }

    public void setOkButton(CharSequence text) {
        setButton(DialogInterface.BUTTON_POSITIVE, text, this);
    }

    public void setCancelButton(CharSequence text) {
        setButton(DialogInterface.BUTTON_NEGATIVE, text, this);
    }

    public void setOnClickListener(OnClickListener listener) {
        mOnClickListener = listener;
    }

    // ==================== 内容设置 ====================

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title != null ? title.toString() : null;
        super.setTitle(title);
    }

    public String getTitle() {
        return mTitle;
    }

    @Override
    public void setMessage(CharSequence message) {
        mMessage = message != null ? message.toString() : null;
        super.setMessage(message);
    }

    public String getMessage() {
        return mMessage;
    }

    @Override
    public void setIcon(Drawable icon) {
        super.setIcon(icon);
    }

    @Override
    public void setView(View view) {
        mCustomView = view;
        super.setView(view);
    }

    public View getView() {
        return mCustomView;
    }

    // ==================== 列表适配器 ====================

    public void setItems(String[] items) {
        List<String> list = new ArrayList<>(Arrays.asList(items));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(mContext, android.R.layout.simple_list_item_1, list);
        setAdapter(adapter);
        mListView.setChoiceMode(ListView.CHOICE_MODE_NONE);
    }

    public void setAdapter(ListAdapter adapter) {
        if (!mListView.equals(mCustomView)) {
            setView(mListView);
        }
        mListView.setAdapter(adapter);
    }

    public void setSingleChoiceItems(CharSequence[] items) {
        setSingleChoiceItems(items, 0);
    }

    public void setSingleChoiceItems(CharSequence[] items, int checkedItem) {
        List<CharSequence> list = new ArrayList<>(Arrays.asList(items));
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(mContext, android.R.layout.simple_list_item_single_choice, list);
        setAdapter(adapter);
        mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        if (checkedItem >= 0 && checkedItem < items.length) {
            mListView.setItemChecked(checkedItem, true);
        }
    }

    public void setMultiChoiceItems(CharSequence[] items) {
        setMultiChoiceItems(items, new int[0]);
    }

    public void setMultiChoiceItems(CharSequence[] items, int[] checkedItems) {
        List<CharSequence> list = new ArrayList<>(Arrays.asList(items));
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(mContext, android.R.layout.simple_list_item_multiple_choice, list);
        setAdapter(adapter);
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        for (int position : checkedItems) {
            if (position >= 0 && position < items.length) {
                mListView.setItemChecked(position, true);
            }
        }
    }

    // ==================== ListView 事件 ====================

    public ListView getListView() {
        return mListView;
    }

    public void setOnItemClickListener(AdapterView.OnItemClickListener listener) {
        mListView.setOnItemClickListener(listener);
    }

    public void setOnItemLongClickListener(AdapterView.OnItemLongClickListener listener) {
        mListView.setOnItemLongClickListener(listener);
    }

    public void setOnItemSelectedListener(AdapterView.OnItemSelectedListener listener) {
        mListView.setOnItemSelectedListener(listener);
    }

    // ==================== 显示控制 ====================

    /**
     * 在 Service 中显示对话框（需要指定 token）
     *
     * @param token 窗口 token
     */
    public void show(IBinder token) {
        Window window = getWindow();
        if (window == null) return;

        WindowManager.LayoutParams attrs = window.getAttributes();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Settings.canDrawOverlays(mContext)) {
            attrs.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            attrs.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        }
        attrs.token = token;
        window.setAttributes(attrs);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        super.show();
    }

    @Override
    public void show() {
        Window window = getWindow();
        if (mContext instanceof Service) {
            if (window == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            } else {
                window.setType(WindowManager.LayoutParams.TYPE_PHONE);
            }
        }
        try {
            super.show();
        } catch (Exception e) {
            LuaConfig.logError("LuaDialog", e);
        }
    }

    @Override
    public void hide() {
        super.hide();
    }

    @Override
    public boolean isShowing() {
        return super.isShowing();
    }

    // ==================== 按钮回调 ====================

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (mOnClickListener != null) {
            mOnClickListener.onClick(this, getButton(which));
        }
    }

    // ==================== 内部接口 ====================

    public interface OnClickListener {
        void onClick(LuaDialog dialog, Button button);
    }
}
