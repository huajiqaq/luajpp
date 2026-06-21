package com.androlua.drawable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.androlua.core.LuaContext;
import com.androlua.core.LuaGcable;
import com.androlua.view.NineBitmapDrawable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

public class LuaBitmapDrawable extends Drawable implements LuaGcable {

    public static final int MATRIX = 0;
    public static final int FIT_XY = 1;
    public static final int FIT_START = 2;
    public static final int FIT_CENTER = 3;
    public static final int FIT_END = 4;
    public static final int CENTER = 5;
    public static final int CENTER_CROP = 6;
    public static final int CENTER_INSIDE = 7;

    private final Context mContext;
    private Drawable mRealDrawable;
    private Drawable mErrorDrawable;
    private NineBitmapDrawable mNineBitmapDrawable;

    private int mScaleType = FIT_XY;
    private int mFillColor;
    private boolean mGc;

    // ==================== 构造方法 ====================

    public LuaBitmapDrawable(LuaContext context, String path, Drawable def) {
        this(context, path);
        if (def != null) mErrorDrawable = def;
    }

    public LuaBitmapDrawable(LuaContext context, String path) {
        mContext = context.getContext();
        load(context, path);
    }

    public LuaBitmapDrawable(Context context, String path) {
        mContext = context;
        load(path);
    }

    public LuaBitmapDrawable(String path) {
        mContext = null;
        load(path);
    }

    // ==================== 加载逻辑 ====================

    private void load(LuaContext luaContext, String path) {
        if (path == null || path.isEmpty()) {
            mRealDrawable = mErrorDrawable;
            return;
        }
        if (!path.startsWith("/") && !isNetworkUrl(path)) {
            path = luaContext.getLuaPath(path);
        }
        load(path);
    }

    private void load(String path) {
        if (path == null || path.isEmpty()) {
            mRealDrawable = mErrorDrawable;
            return;
        }
        if (mContext == null) {
            tryNinePatch(path);
            return;
        }

        Glide.with(mContext)
                .load(path)
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        mRealDrawable = resource;
                        invalidateSelf();
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        tryNinePatch(path);
                        if (mRealDrawable == null) {
                            mRealDrawable = errorDrawable != null ? errorDrawable : mErrorDrawable;
                            invalidateSelf();
                        }
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        mRealDrawable = null;
                        invalidateSelf();
                    }
                });
    }

    private void tryNinePatch(String path) {
        try {
            mNineBitmapDrawable = new NineBitmapDrawable(path);
            mRealDrawable = mNineBitmapDrawable;
            invalidateSelf();
        } catch (Exception ignored) {
        }
    }

    // ==================== 绘制 ====================

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mFillColor != 0) canvas.drawColor(mFillColor);

        if (mRealDrawable != null) {
            mRealDrawable.setBounds(calculateDestRect());
            mRealDrawable.draw(canvas);
        }
    }

    private Rect calculateDestRect() {
        Rect bounds = getBounds();
        int srcWidth = mRealDrawable.getIntrinsicWidth();
        int srcHeight = mRealDrawable.getIntrinsicHeight();
        if (srcWidth <= 0 || srcHeight <= 0) return bounds;

        int width = srcWidth;
        int height = srcHeight;

        if (mScaleType == FIT_XY) {
            width = bounds.width();
            height = bounds.height();
        } else if (mScaleType != MATRIX) {
            float scale = Math.min(
                    (float) bounds.width() / width,
                    (float) bounds.height() / height
            );
            width = (int) (width * scale);
            height = (int) (height * scale);
        }

        int left = bounds.left;
        int top = bounds.top;
        if (mScaleType == FIT_CENTER) {
            left += (bounds.width() - width) / 2;
            top += (bounds.height() - height) / 2;
        } else if (mScaleType == FIT_END) {
            left = bounds.right - width;
            top = bounds.bottom - height;
        }
        return new Rect(left, top, left + width, top + height);
    }

    // ==================== 属性 ====================

    public int getWidth() {
        return mRealDrawable != null ? mRealDrawable.getIntrinsicWidth() : 0;
    }

    public int getHeight() {
        return mRealDrawable != null ? mRealDrawable.getIntrinsicHeight() : 0;
    }

    @Override
    public int getIntrinsicWidth() {
        return getWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return getHeight();
    }

    public void setScaleType(int scaleType) {
        mScaleType = scaleType;
        invalidateSelf();
    }

    public void setFillColor(int fillColor) {
        mFillColor = fillColor;
        invalidateSelf();
    }

    public void setErrorDrawable(Drawable drawable) {
        mErrorDrawable = drawable;
    }

    @Override
    public void setAlpha(int alpha) {
        if (mRealDrawable != null) mRealDrawable.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        if (mRealDrawable != null) mRealDrawable.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    // ==================== 生命周期 ====================

    @Override
    public void gc() {
        if (mNineBitmapDrawable != null) {
            mNineBitmapDrawable.gc();
            mNineBitmapDrawable = null;
        }
        mRealDrawable = null;
        mGc = true;
    }

    @Override
    public boolean isGc() {
        return mGc;
    }

    private boolean isNetworkUrl(String path) {
        return path != null && (path.startsWith("http://") || path.startsWith("https://"));
    }
}