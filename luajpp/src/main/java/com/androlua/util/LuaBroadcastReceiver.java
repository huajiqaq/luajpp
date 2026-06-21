package com.androlua.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Lua 广播接收器
 * 用于将 Android 广播转发到 Lua 层
 */
public class LuaBroadcastReceiver extends BroadcastReceiver {

    private final OnReceiveListener mListener;

    public LuaBroadcastReceiver(OnReceiveListener listener) {
        mListener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mListener != null) {
            mListener.onReceive(context, intent);
        }
    }

    public interface OnReceiveListener {
        void onReceive(Context context, Intent intent);
    }
}
