package com.androlua.internal;

import com.androlua.core.LuaContext;
import com.androlua.core.LuaGcable;

import org.luaj.LuaConstants;
import org.luaj.LuaError;
import org.luaj.LuaValue;

import java.util.concurrent.ScheduledFuture;

/**
 * Lua 定时器，基于 ScheduledExecutorService 实现。
 */
public class LuaTimer implements LuaGcable {

    private final LuaContext mLuaContext;
    private final LuaValue mFunction;
    private LuaValue[] mArgs;
    private volatile boolean mEnabled = true;
    private volatile boolean mGced;
    private volatile long mPeriod;
    private ScheduledFuture<?> mFuture;

    public LuaTimer(LuaContext context, LuaValue function, LuaValue[] args) throws LuaError {
        mLuaContext = context;
        mFunction = function;
        mArgs = args != null ? args.clone() : LuaConstants.NOVALS;
        if (context != null) {
            context.regGc(this);
        }
    }

    /**
     * 启动定时器
     */
    public void start(long delay, long period) {
        stop();
        mPeriod = period;
        mFuture = LuaScheduler.getInstance().scheduleAtFixedRate(this::tick, delay, period);
    }

    /**
     * 启动一次性定时器
     */
    public void start(long delay) {
        stop();
        mFuture = LuaScheduler.getInstance().schedule(this::tick, delay);
    }

    /**
     * 停止定时器
     */
    public void stop() {
        if (mFuture != null) {
            mFuture.cancel(false);
            mFuture = null;
        }
    }

    private void tick() {
        if (!mEnabled || mGced) return;
        try {
            mFunction.invoke(mArgs);
        } catch (Exception e) {
            if (mLuaContext != null) {
                mLuaContext.sendError("LuaTimer", e);
            } else {
                LuaConfig.logError("LuaTimer", e);
            }
        }
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public boolean getEnabled() {
        return mEnabled;
    }

    public void setPeriod(long period) {
        mPeriod = period;
    }

    public long getPeriod() {
        return mPeriod;
    }

    public boolean isRunning() {
        return mFuture != null && !mFuture.isCancelled();
    }

    public void setArgs(LuaValue... args) {
        mArgs = args != null ? args.clone() : LuaConstants.NOVALS;
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
}
