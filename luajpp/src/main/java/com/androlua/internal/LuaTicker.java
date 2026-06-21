package com.androlua.internal;

import com.androlua.core.LuaContext;
import com.androlua.core.LuaGcable;

import java.util.concurrent.ScheduledFuture;

/**
 * Lua 周期性心跳器，基于 LuaScheduler 实现。
 */
public class LuaTicker implements LuaGcable {

    private final LuaContext mLuaContext;
    private OnTickListener mListener;
    private volatile long mPeriod = 1000;
    private volatile boolean mEnabled = true;
    private volatile boolean mRunning;
    private volatile boolean mGced;
    private ScheduledFuture<?> mFuture;

    public LuaTicker() {
        mLuaContext = null;
    }

    public LuaTicker(LuaContext context) {
        mLuaContext = context;
        if (context != null) {
            context.regGc(this);
        }
    }

    /**
     * 启动心跳
     */
    public void start() {
        if (mRunning) return;
        mRunning = true;
        mFuture = LuaScheduler.getInstance().scheduleAtFixedRate(this::tick, 0, mPeriod);
    }

    /**
     * 停止心跳
     */
    public void stop() {
        mRunning = false;
        if (mFuture != null) {
            mFuture.cancel(false);
            mFuture = null;
        }
    }

    private void tick() {
        if (!mEnabled || !mRunning || mGced) return;
        if (mListener != null) {
            try {
                mListener.onTick();
            } catch (Exception e) {
                if (mLuaContext != null) {
                    mLuaContext.sendError("LuaTicker", e);
                } else {
                    LuaConfig.logError("LuaTicker", e);
                }
            }
        }
    }

    public void setPeriod(long period) {
        mPeriod = period;
        if (mRunning) {
            stop();
            start();
        }
    }

    public long getPeriod() {
        return mPeriod;
    }

    public void setInterval(long period) {
        setPeriod(period);
    }

    public long getInterval() {
        return mPeriod;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public boolean getEnabled() {
        return mEnabled;
    }

    public boolean isRunning() {
        return mRunning;
    }

    public void setOnTickListener(OnTickListener listener) {
        mListener = listener;
    }

    @Override
    public void gc() {
        stop();
        mGced = true;
    }

    @Override
    public boolean isGc() {
        return mGced;
    }

    public interface OnTickListener {
        void onTick();
    }
}
