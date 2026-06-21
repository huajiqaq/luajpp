package com.androlua.adapter;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.BaseAdapter;

import com.androlua.core.LuaContext;
import com.androlua.internal.LuaConfig;
import com.androlua.internal.LuaLayout;

import org.luaj.Globals;
import org.luaj.LuaError;
import org.luaj.LuaTable;
import org.luaj.LuaValue;
import org.luaj.lib.jse.CoerceJavaToLua;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class LuaMultiAdapter extends BaseAdapter {

    private final LuaContext mContext;
    private final LuaValue mLayouts;
    private final LuaTable mData;
    private LuaValue mTheme;
    private final LuaLayout mLayoutLoader;
    private final LuaValue mInsertFunc;
    private final LuaValue mRemoveFunc;
    private final LuaValue mLayoutParams;

    private LuaValue mAnimationUtil;
    private final Map<View, Animation> mAnimCache = new HashMap<>();
    private final Map<View, Boolean> mStyleCache = new HashMap<>();

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mNotifyOnChange = true;
    private boolean mUpdating;

    public LuaMultiAdapter(LuaContext context, LuaValue layouts) throws LuaError {
        this(context, null, layouts);
    }

    public LuaMultiAdapter(LuaContext context, LuaTable data, LuaValue layouts) throws LuaError {
        mContext = context;
        mLayouts = layouts;
        Globals mGlobals = context.getLuaState();
        mData = data != null ? data : new LuaTable();
        mLayoutLoader = new LuaLayout(context.getContext());

        var table = mGlobals.get("table");
        mInsertFunc = table.get("insert");
        mRemoveFunc = table.get("remove");
        mLayoutParams = CoerceJavaToLua.coerce(AdapterView.LayoutParams.class);

        int layoutCount = mLayouts.length();
        for (int i = 1; i <= layoutCount; i++) {
            mLayoutLoader.load(mLayouts.get(i), new LuaTable(), mLayoutParams);
        }
    }

    @Override
    public int getViewTypeCount() {
        return mLayouts.length();
    }

    @Override
    public int getItemViewType(int position) {
        try {
            int type = mData.get(position + 1).get("__type").toint();
            return Math.max(type - 1, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public int getCount() {
        return mData.length();
    }

    @Override
    public Object getItem(int position) {
        return mData.get(position + 1);
    }

    @Override
    public long getItemId(int position) {
        return position + 1;
    }

    public LuaTable getData() {
        return mData;
    }

    public void setAnimationUtil(LuaValue animation) {
        mAnimCache.clear();
        mAnimationUtil = animation;
    }

    public void add(LuaValue item) {
        mInsertFunc.jcall(mData, item);
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void addAll(LuaValue items) {
        int length = items.length();
        for (int i = 1; i <= length; i++) mInsertFunc.jcall(mData, items.get(i));
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void insert(int position, LuaValue item) {
        mInsertFunc.jcall(mData, position + 1, item);
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void remove(int position) {
        mRemoveFunc.jcall(mData, position + 1);
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void clear() {
        mData.clear();
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

    public void setStyle(LuaValue theme) {
        mStyleCache.clear();
        mTheme = theme;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;
        LuaTable holder;
        var itemData = mData.get(position + 1);

        int type = Math.max(itemData.get("__type").toint(), 1);
        var layout = mLayouts.get(type);

        if (convertView == null) {
            try {
                holder = new LuaTable();
                view = mLayoutLoader.load(layout, holder, mLayoutParams).touserdata(View.class);
                view.setTag(holder);
            } catch (LuaError e) {
                return new View(mContext.getContext());
            }
        } else {
            view = convertView;
            holder = (LuaTable) view.getTag();
        }

        var data = itemData.checktable();
        boolean isNewView = mStyleCache.get(view) == null;
        if (isNewView) mStyleCache.put(view, true);

        for (LuaValue key : data.keys()) {
            try {
                var keyStr = key.tojstring();
                var value = data.jget(keyStr);
                var targetObj = holder.get(keyStr);
                if (targetObj.isuserdata()) {
                    var targetView = targetObj.touserdata(View.class);
                    if (mTheme != null && isNewView)
                        AdapterHelper.setHelper(targetView, mTheme.jget(keyStr));
                    AdapterHelper.setHelper(targetView, value);
                }
            } catch (Exception e) {
                LuaConfig.logError("LuaMultiAdapter", e);
            }
        }

        if (mUpdating) return view;

        if (mAnimationUtil != null && convertView != null) {
            Animation anim = mAnimCache.get(convertView);
            if (anim == null) {
                try {
                    anim = mAnimationUtil.get(type).call().touserdata(Animation.class);
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
}
