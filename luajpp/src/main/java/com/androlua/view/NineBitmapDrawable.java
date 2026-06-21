package com.androlua.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.androlua.LuaApplication;
import com.androlua.core.LuaGcable;
import com.bumptech.glide.Glide;

/**
 * 九宫格图片 Drawable
 * 支持 .9.png 格式图片的拉伸绘制
 */
public class NineBitmapDrawable extends Drawable implements LuaGcable {

    private static final int MARKER_COLOR = Color.BLACK;

    private Bitmap mBitmap;
    private final Paint mPaint;

    private Rect mRect1, mRect2, mRect3;
    private Rect mRect4, mRect5, mRect6;
    private Rect mRect7, mRect8, mRect9;

    private int mStretchX1, mStretchX2;
    private int mStretchY1, mStretchY2;

    private int mContentLeft, mContentRight;
    private int mContentTop, mContentBottom;

    private int mImageWidth, mImageHeight;
    private float mScale = 1f;
    private boolean mIsRecycled;

    public NineBitmapDrawable(String path) throws Exception {
        // 内部直接使用全局 Context 调用 Glide，对外不暴露 Context 参数
        this(loadWithGlide(path));
    }

    public NineBitmapDrawable(Bitmap bitmap) {
        mPaint = new Paint();
        mPaint.setColor(Color.WHITE);
        initFromBitmap(bitmap);
    }

    public NineBitmapDrawable(Bitmap bitmap, int x1, int y1, int x2, int y2) {
        mPaint = new Paint();
        mPaint.setColor(Color.WHITE);
        initWithStretchArea(bitmap, x1, y1, x2, y2);
    }

    private static Bitmap loadWithGlide(String path) throws Exception {
        try {
            // 使用全局 Context 同步获取 Bitmap，自动享受 Glide 的磁盘/内存缓存
            return Glide.with(LuaApplication.getInstance())
                    .asBitmap()
                    .load(path)
                    .submit()
                    .get();
        } catch (Exception e) {
            throw new Exception("Failed to load NineBitmapDrawable: " + path, e);
        }
    }

    private void initFromBitmap(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int x1 = findFirstMarkerX(bitmap, width);
        int x2 = findSecondMarkerX(bitmap, x1, width);
        int y1 = findFirstMarkerY(bitmap, height);
        int y2 = findSecondMarkerY(bitmap, y1, height);

        parseContentArea(bitmap, width, height);
        initWithStretchArea(bitmap, x1, y1, x2, y2);
    }

    private int findFirstMarkerX(Bitmap bitmap, int width) {
        for (int i = 0; i < width; i++) {
            int pixel = bitmap.getPixel(i, 0);
            if (pixel == MARKER_COLOR) return i;
            if (pixel != Color.TRANSPARENT && pixel != MARKER_COLOR) break;
        }
        throw new IllegalArgumentException("No stretch marker found on top edge");
    }

    private int findSecondMarkerX(Bitmap bitmap, int start, int width) {
        for (int i = start; i < width; i++) {
            if (bitmap.getPixel(i, 0) != MARKER_COLOR) return i;
        }
        throw new IllegalArgumentException("No end stretch marker found on top edge");
    }

    private int findFirstMarkerY(Bitmap bitmap, int height) {
        for (int i = 0; i < height; i++) {
            int pixel = bitmap.getPixel(0, i);
            if (pixel == MARKER_COLOR) return i;
            if (pixel != Color.TRANSPARENT && pixel != MARKER_COLOR) break;
        }
        throw new IllegalArgumentException("No stretch marker found on left edge");
    }

    private int findSecondMarkerY(Bitmap bitmap, int start, int height) {
        for (int i = start; i < height; i++) {
            if (bitmap.getPixel(0, i) != MARKER_COLOR) return i;
        }
        throw new IllegalArgumentException("No end stretch marker found on left edge");
    }

    private void parseContentArea(Bitmap bitmap, int width, int height) {
        mContentLeft = 0;
        mContentRight = 0;
        for (int i = 0; i < width; i++) {
            int pixel = bitmap.getPixel(i, height - 1);
            if (pixel == MARKER_COLOR) {
                mContentLeft = i;
                break;
            }
            if (pixel != Color.TRANSPARENT && pixel != MARKER_COLOR) break;
        }
        for (int i = mContentLeft; i < width; i++) {
            if (bitmap.getPixel(i, height - 1) != MARKER_COLOR) {
                mContentRight = width - i;
                break;
            }
        }

        mContentTop = 0;
        mContentBottom = 0;
        for (int i = 0; i < height; i++) {
            int pixel = bitmap.getPixel(width - 1, i);
            if (pixel == MARKER_COLOR) {
                mContentTop = i;
                break;
            }
            if (pixel != Color.TRANSPARENT && pixel != MARKER_COLOR) break;
        }
        for (int i = mContentTop; i < height; i++) {
            if (bitmap.getPixel(width - 1, i) != MARKER_COLOR) {
                mContentBottom = height - i;
                break;
            }
        }
    }

    private void initWithStretchArea(Bitmap bitmap, int x1, int y1, int x2, int y2) {
        mBitmap = bitmap;
        mImageWidth = bitmap.getWidth();
        mImageHeight = bitmap.getHeight();

        int right = mImageWidth - x2;
        int bottom = mImageHeight - y2;

        mRect1 = new Rect(1, 1, x1, y1);
        mRect2 = new Rect(x1, 1, x2, y1);
        mRect3 = new Rect(x2, 1, right, y1);

        mRect4 = new Rect(1, y1, x1, y2);
        mRect5 = new Rect(x1, y1, x2, y2);
        mRect6 = new Rect(x2, y1, right, y2);

        mRect7 = new Rect(1, y2, x1, bottom);
        mRect8 = new Rect(x1, y2, x2, bottom);
        mRect9 = new Rect(x2, y2, right, bottom);

        mStretchX1 = x1;
        mStretchY1 = y1;
        mStretchX2 = mImageWidth - x2;
        mStretchY2 = mImageHeight - y2;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mBitmap == null || mBitmap.isRecycled()) return;

        var bounds = getBounds();
        int targetWidth = bounds.width();
        int targetHeight = bounds.height();

        mScale = Math.min(targetWidth * 1f / mImageWidth, targetHeight * 1f / mImageHeight);
        if (mScale > 2) mScale = 2;

        int scaledX1 = (int) (mStretchX1 * mScale);
        int scaledX2 = (int) (mStretchX2 * mScale);
        int scaledY1 = (int) (mStretchY1 * mScale);
        int scaledY2 = (int) (mStretchY2 * mScale);

        var destRects = calculateDestRects(targetWidth, targetHeight, scaledX1, scaledX2, scaledY1, scaledY2);

        canvas.drawBitmap(mBitmap, mRect1, destRects[0], mPaint);
        canvas.drawBitmap(mBitmap, mRect2, destRects[1], mPaint);
        canvas.drawBitmap(mBitmap, mRect3, destRects[2], mPaint);

        canvas.drawBitmap(mBitmap, mRect4, destRects[3], mPaint);
        canvas.drawBitmap(mBitmap, mRect5, destRects[4], mPaint);
        canvas.drawBitmap(mBitmap, mRect6, destRects[5], mPaint);

        canvas.drawBitmap(mBitmap, mRect7, destRects[6], mPaint);
        canvas.drawBitmap(mBitmap, mRect8, destRects[7], mPaint);
        canvas.drawBitmap(mBitmap, mRect9, destRects[8], mPaint);
    }

    private Rect[] calculateDestRects(int w, int h, int x1, int x2, int y1, int y2) {
        var rects = new Rect[9];

        rects[0] = new Rect(0, 0, x1, y1);
        rects[1] = new Rect(x1, 0, w - x2, y1);
        rects[2] = new Rect(w - x2, 0, w, y1);

        rects[3] = new Rect(0, y1, x1, h - y2);
        rects[4] = new Rect(x1, y1, w - x2, h - y2);
        rects[5] = new Rect(w - x2, y1, w, h - y2);

        rects[6] = new Rect(0, h - y2, x1, h);
        rects[7] = new Rect(x1, h - y2, w - x2, h);
        rects[8] = new Rect(w - x2, h - y2, w, h);

        return rects;
    }

    @Override
    public boolean getPadding(@NonNull Rect padding) {
        if (mContentRight > 0) {
            padding.set(
                    (int) (mContentLeft * mScale),
                    (int) (mContentTop * mScale),
                    (int) (mContentRight * mScale),
                    (int) (mContentBottom * mScale)
            );
            return true;
        }
        return super.getPadding(padding);
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        mPaint.setColorFilter(filter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void gc() {
        if (mBitmap != null && !mBitmap.isRecycled()) mBitmap.recycle();
        mIsRecycled = true;
    }

    @Override
    public boolean isGc() {
        return mIsRecycled;
    }
}
