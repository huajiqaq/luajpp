package com.androlua.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.androlua.internal.LuaConfig;

import org.luaj.LuaError;
import org.luaj.LuaFunction;
import org.luaj.LuaValue;

/**
 * Lua 可绘制对象
 * 允许在 Lua 中自定义绘制逻辑
 */
@SuppressWarnings("unused")
public class LuaDrawable extends Drawable {

    private final LuaValue mDraw;
    private final Paint mPaint;
    private LuaFunction mOnDraw;

    public LuaDrawable(LuaFunction drawFunc) {
        mDraw = drawFunc;
        mPaint = new Paint();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        try {
            if (mOnDraw == null) {
                Object result = mDraw.jcall(canvas, mPaint, this);
                if (result instanceof LuaFunction) {
                    mOnDraw = (LuaFunction) result;
                }
            }
            if (mOnDraw != null) {
                mOnDraw.jcall(canvas);
            }
        } catch (LuaError e) {
            LuaConfig.logError("LuaDrawable", e);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    public Paint getPaint() {
        return mPaint;
    }
}
