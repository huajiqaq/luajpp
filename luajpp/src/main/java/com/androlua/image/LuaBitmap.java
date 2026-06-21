package com.androlua.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.androlua.internal.LuaConfig;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 基于 Glide 的图片加载工具类
 */
public final class LuaBitmap {

    private static final ConcurrentHashMap<String, String> sHeaders = new ConcurrentHashMap<>();

    private LuaBitmap() {
        throw new UnsupportedOperationException("Cannot instantiate utility class");
    }

    // ==================== 请求头配置 ====================

    public static void setHeader(String key, String value) {
        sHeaders.put(key, value);
    }

    public static void setHeaders(@Nullable Map<String, String> headers) {
        sHeaders.clear();
        if (headers != null) {
            sHeaders.putAll(headers);
        }
    }

    public static void removeHeader(String key) {
        sHeaders.remove(key);
    }

    public static void clearHeaders() {
        sHeaders.clear();
    }

    // ==================== 加载到 ImageView ====================

    public static void load(@NonNull Context context, @NonNull String url, @NonNull ImageView imageView) {
        load(context, url, imageView, null);
    }

    public static void load(@NonNull Context context, @NonNull String url, @NonNull ImageView imageView,
                            @Nullable RequestOptions options) {
        var builder = Glide.with(context).load(buildGlideUrl(url));
        if (options != null) builder.apply(options);
        builder.into(imageView);
    }

    public static void load(@NonNull Context context, @NonNull File file, @NonNull ImageView imageView) {
        Glide.with(context).load(file).into(imageView);
    }

    public static void load(@NonNull Context context, int resourceId, @NonNull ImageView imageView) {
        Glide.with(context).load(resourceId).into(imageView);
    }

    // ==================== 获取 Bitmap ====================

    public static void getBitmap(@NonNull Context context, @NonNull String url, @NonNull BitmapCallback callback) {
        Glide.with(context)
                .asBitmap()
                .load(buildGlideUrl(url))
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        callback.onSuccess(resource);
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        callback.onFailed(new Exception("Failed to load bitmap: " + url));
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        callback.onFailed(new Exception("Load cleared: " + url));
                    }
                });
    }

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public static Bitmap getBitmapSync(@NonNull Context context, @NonNull String url) throws Exception {
        // 检查是否在后台线程
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return loadWithFuture(context, url);
        }

        // 主线程使用 Future + 超时
        Future<Bitmap> future = executor.submit(() -> loadWithFuture(context, url));

        try {
            return future.get(LuaConfig.getHttpTimeout() + 500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new Exception("Bitmap loading timeout: " + url, e);
        } catch (ExecutionException e) {
            throw new Exception("Failed to load bitmap: " + url, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Interrupted: " + url, e);
        }
    }

    private static Bitmap loadWithFuture(Context context, String url) throws Exception {
        FutureTarget<Bitmap> target = Glide.with(context.getApplicationContext())
                .asBitmap()
                .load(url)
                .submit();

        try {
            return target.get(LuaConfig.getHttpTimeout(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            target.cancel(true);
            throw e;
        }
    }

    // ==================== 预加载与缓存 ====================

    public static void preload(@NonNull Context context, @NonNull String url) {
        Glide.with(context).load(buildGlideUrl(url)).preload();
    }

    public static void preload(@NonNull Context context, @NonNull String url, int width, int height) {
        Glide.with(context).load(buildGlideUrl(url)).preload(width, height);
    }

    public static void downloadOnly(@NonNull Context context, @NonNull String url) {
        Glide.with(context).downloadOnly().load(buildGlideUrl(url)).preload();
    }

    public static void clearMemoryCache(@NonNull Context context) {
        Glide.get(context).clearMemory();
    }

    public static void clearDiskCache(@NonNull Context context) {
        // Glide的clearDiskCache必须在后台线程执行
        new Thread(() -> Glide.get(context).clearDiskCache()).start();
    }

    public static void clearAllCache(@NonNull Context context) {
        clearMemoryCache(context);
        clearDiskCache(context);
    }

    // ==================== 请求生命周期 ====================

    public static void pauseRequests(@NonNull Context context) {
        Glide.with(context).pauseRequests();
    }

    public static void resumeRequests(@NonNull Context context) {
        Glide.with(context).resumeRequests();
    }

    // ==================== 内部方法 ====================

    private static Object buildGlideUrl(String url) {
        if (sHeaders.isEmpty()) return url;
        var builder = new LazyHeaders.Builder();
        sHeaders.forEach(builder::addHeader);
        return new GlideUrl(url, builder.build());
    }

    // ==================== 回调接口 ====================

    public interface BitmapCallback {
        void onSuccess(@NonNull Bitmap bitmap);

        void onFailed(@NonNull Exception e);
    }
}
