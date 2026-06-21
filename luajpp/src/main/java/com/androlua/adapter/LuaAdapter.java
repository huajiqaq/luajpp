package com.androlua.adapter;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import com.androlua.core.LuaContext;
import com.androlua.internal.LuaConfig;
import com.androlua.internal.LuaLayout;
import com.androlua.internal.LuaLog;
import com.bumptech.glide.Glide;

import org.luaj.LuaError;
import org.luaj.LuaFunction;
import org.luaj.LuaTable;
import org.luaj.LuaValue;
import org.luaj.lib.jse.CoerceJavaToLua;

import java.util.HashMap;

@SuppressWarnings("unused")
public class LuaAdapter extends BaseAdapter implements Filterable {

    private final LuaTable mBaseData;
    private final LuaContext mContext;

    private LuaTable mLayout;
    private LuaTable mData;
    private LuaTable mTheme;

    private CharSequence mPrefix;

    private final LuaLayout loadlayout;

    private LuaFunction mAnimationUtil;

    private final HashMap<View, Animation> mAnimCache = new HashMap<>();
    private final HashMap<View, Boolean> mStyleCache = new HashMap<>();

    private boolean mNotifyOnChange = true;
    private boolean mUpdating;

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 0) {
                notifyDataSetChanged();
            } else {
                try {
                    LuaTable newValues = new LuaTable();
                    mLuaFilter.jcall(mBaseData, newValues, mPrefix);
                    mData = newValues;
                    notifyDataSetChanged();
                } catch (LuaError e) {
                    LuaConfig.logError("LuaAdapter", e);
                    mContext.sendError("performFiltering", e);
                }
            }
        }
    };

    private ArrayFilter mFilter;
    private LuaFunction mLuaFilter;

    public LuaAdapter(LuaContext context, LuaTable layout) throws LuaError {
        this(context, null, layout);
    }

    public LuaAdapter(LuaContext context, LuaTable data, LuaTable layout) throws LuaError {
        mContext = context;
        if (data == null) data = new LuaTable();
        if (layout.length() == layout.size() && data.length() != data.size()) {
            mLayout = data;
            data = layout;
            layout = mLayout;
        }
        mLayout = layout;
        mData = data;
        mBaseData = mData;
        loadlayout = new LuaLayout(mContext.getContext());
        loadlayout.load(mLayout, new LuaTable());
    }

    public void setAnimation(LuaFunction animation) {
        setAnimationUtil(animation);
    }

    public void setAnimationUtil(LuaFunction animation) {
        mAnimCache.clear();
        mAnimationUtil = animation;
    }

    @Override
    public int getCount() {
        return mData.length();
    }

    @Override
    public Object getItem(int position) {
        return CoerceJavaToLua.coerce(mData.get(position + 1));
    }

    @Override
    public long getItemId(int position) {
        return position + 1;
    }

    public LuaTable getData() {
        return mData;
    }

    public void setItem(int index, LuaValue object) {
        synchronized (mBaseData) {
            mBaseData.set(index + 1, object);
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void add(LuaTable item) {
        mBaseData.insert(mBaseData.length() + 1, item);
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void addAll(LuaTable items) {
        int len = items.length();
        for (int i = 1; i <= len; i++) {
            mBaseData.insert(mBaseData.length() + 1, items.get(i));
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void insert(int position, LuaTable item) {
        mBaseData.insert(position + 1, item);
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void remove(int position) {
        mBaseData.remove(position + 1);
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void clear() {
        mBaseData.clear();
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void setNotifyOnChange(boolean notifyOnChange) {
        mNotifyOnChange = notifyOnChange;
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        if (!mUpdating) {
            mUpdating = true;
            mHandler.postDelayed(() -> mUpdating = false, 500);
        }
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getView(position, convertView, parent);
    }

    public void setStyle(LuaTable theme) {
        mStyleCache.clear();
        mTheme = theme;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;
        LuaTable holder;

        if (convertView == null) {
            try {
                holder = new LuaTable();
                var lview = loadlayout.load(mLayout, holder);
                view = lview.touserdata(View.class);
                view.setTag(holder);
            } catch (LuaError e) {
                return new View(mContext.getContext());
            }
        } else {
            view = convertView;
            holder = (LuaTable) view.getTag();
        }

        var itemData = mData.get(position + 1);
        if (!itemData.istable()) {
            LuaLog.getInstance().addError("setHelper error: " + position,
                    new Exception(position + " is not a table"));
            return view;
        }

        boolean isNewView = mStyleCache.get(view) == null;
        if (isNewView) mStyleCache.put(view, true);

        for (var key : ((LuaTable) itemData).keys()) {
            try {
                var keyStr = key.tojstring();
                var value = itemData.jget(keyStr);
                var obj = holder.get(keyStr);

                if (obj.isuserdata()) {
                    var targetView = obj.touserdata(View.class);
                    if (mTheme != null && isNewView) setHelper(targetView, mTheme.get(keyStr));
                    setHelper(targetView, value);
                }
            } catch (Exception e) {
                LuaConfig.logError("LuaAdapter", e);
                LuaLog.getInstance().addError("setHelper error: " + position, e);
            }
        }

        if (mUpdating) return view;

        if (mAnimationUtil != null && convertView != null) {
            var anim = mAnimCache.get(convertView);
            if (anim == null) {
                try {
                    anim = mAnimationUtil.call().touserdata(Animation.class);
                    mAnimCache.put(convertView, anim);
                } catch (Exception e) {
                    mContext.sendError("setAnimation", e);
                }
            }
            if (anim != null) {
                view.clearAnimation();
                view.startAnimation(anim);
            }
        }
        return view;
    }

    private void setFields(View view, LuaTable fields) {
        for (var key : fields.keys()) {
            var keyStr = key.tojstring();
            var value = fields.jget(keyStr);
            if ("src".equalsIgnoreCase(keyStr)) {
                setHelper(view, value);
            } else {
                CoerceJavaToLua.coerce(view).jset(keyStr, value);
            }
        }
    }

    private void setHelper(View view, Object value) {
        try {
            if (value instanceof LuaTable table) {
                setFields(view, table);
            } else if (view instanceof TextView tv) {
                tv.setText(value instanceof CharSequence ? (CharSequence) value : String.valueOf(value));
            } else if (view instanceof ImageView iv) {
                if (value instanceof Bitmap) {
                    iv.setImageBitmap((Bitmap) value);
                } else if (value instanceof String path) {
                    Glide.with(iv).load(path).into(iv);
                } else if (value instanceof Drawable) {
                    iv.setImageDrawable((Drawable) value);
                } else if (value instanceof Number) {
                    iv.setImageResource(((Number) value).intValue());
                }
            }
        } catch (Exception e) {
            LuaConfig.logError("LuaAdapter", e);
            mContext.sendError("setHelper", e);
        }
    }

    @Override
    public Filter getFilter() {
        if (mFilter == null) mFilter = new ArrayFilter();
        return mFilter;
    }

    public void filter(CharSequence constraint) {
        getFilter().filter(constraint);
    }

    public void setFilter(LuaFunction filter) {
        mLuaFilter = filter;
    }

    private class ArrayFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence prefix) {
            mPrefix = prefix;
            if (mData == null) return new FilterResults();

            if (mLuaFilter != null) {
                mHandler.sendEmptyMessage(1);
                return null;
            }

            var results = new FilterResults();
            results.values = mData;
            results.count = mData.length();
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            mData = (LuaTable) results.values;
            if (results.count > 0) notifyDataSetChanged();
            else notifyDataSetInvalidated();
        }
    }
}
