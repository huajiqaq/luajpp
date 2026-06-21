package com.androlua.internal;

import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import java.util.HashMap;

/**
 * 动态资源管理类，支持运行时注入 text/drawable/color/font 等资源。
 */
public class LuaResources extends Resources {

    private Resources mSuperResources;
    private int mNextId = 0x7f050000;

    private final HashMap<String, Integer> mTextMap = new HashMap<>();
    private final HashMap<String, Integer> mDrawableMap = new HashMap<>();
    private final HashMap<String, Integer> mColorMap = new HashMap<>();
    private final HashMap<String, Integer> mTypefaceMap = new HashMap<>();
    private final HashMap<String, Integer> mBooleanMap = new HashMap<>();
    private final HashMap<String, Integer> mIntMap = new HashMap<>();
    private final SparseArray<Object> mValues = new SparseArray<>();

    public LuaResources(AssetManager assets, DisplayMetrics metrics, Configuration config) {
        super(assets, metrics, config);
    }

    public void setSuperResources(Resources resources) {
        mSuperResources = resources;
    }

    private synchronized int nextId() {
        return mNextId++;
    }

    // ==================== 资源注入 ====================

    public int put(String type, String name, Object value) {
        HashMap<String, Integer> map = getMapForType(type);
        if (map == null) return 0;

        Integer existing = map.get(name);
        if (existing != null) {
            mValues.put(existing, value);
            return existing;
        }

        int id = nextId();
        map.put(name, id);
        mValues.put(id, value);
        return id;
    }

    private HashMap<String, Integer> getMapForType(String type) {
        return switch (type) {
            case "string" -> mTextMap;
            case "drawable" -> mDrawableMap;
            case "color" -> mColorMap;
            case "typeface" -> mTypefaceMap;
            case "boolean" -> mBooleanMap;
            case "int" -> mIntMap;
            default -> null;
        };
    }

    // ==================== 资源查询 ====================

    @NonNull
    @Override
    public CharSequence getText(int id) throws NotFoundException {
        if (mTextMap.containsValue(id)) {
            Object value = mValues.get(id);
            return value != null ? value.toString() : "";
        }
        return mSuperResources != null ? mSuperResources.getText(id) : super.getText(id);
    }

    @NonNull
    @Override
    public CharSequence[] getTextArray(int id) throws NotFoundException {
        return mSuperResources != null ? mSuperResources.getTextArray(id) : super.getTextArray(id);
    }

    @Nullable
    public Drawable getDrawable(int id, @Nullable Resources.Theme theme) {
        if (mDrawableMap.containsValue(id)) {
            Object value = mValues.get(id);
            if (value instanceof Drawable d) return d;
        }
        return ResourcesCompat.getDrawable(mSuperResources != null ? mSuperResources : this, id, theme);
    }

    public int getColor(int id, @Nullable Resources.Theme theme) {
        if (mColorMap.containsValue(id)) {
            Object value = mValues.get(id);
            if (value instanceof Integer i) return i;
        }
        return ResourcesCompat.getColor(mSuperResources != null ? mSuperResources : this, id, theme);
    }

    @Nullable
    public Typeface getTypeface(int id) {
        if (mTypefaceMap.containsValue(id)) {
            Object value = mValues.get(id);
            if (value instanceof Typeface t) return t;
        }
        return null;
    }

    @Override
    public boolean getBoolean(int id) throws NotFoundException {
        if (mBooleanMap.containsValue(id)) {
            Object value = mValues.get(id);
            if (value instanceof Boolean b) return b;
        }
        return mSuperResources != null ? mSuperResources.getBoolean(id) : super.getBoolean(id);
    }

    @Override
    public int getInteger(int id) throws NotFoundException {
        if (mIntMap.containsValue(id)) {
            Object value = mValues.get(id);
            if (value instanceof Integer i) return i;
        }
        return mSuperResources != null ? mSuperResources.getInteger(id) : super.getInteger(id);
    }

    // ==================== 便捷方法 ====================

    public int putText(String name, String value) {
        return put("string", name, value);
    }

    public int putDrawable(String name, Drawable value) {
        return put("drawable", name, value);
    }

    public int putColor(String name, int value) {
        return put("color", name, value);
    }

    public int putTypeface(String name, Typeface value) {
        return put("typeface", name, value);
    }

    public int putBoolean(String name, boolean value) {
        return put("boolean", name, value);
    }

    public int putInt(String name, int value) {
        return put("int", name, value);
    }
}