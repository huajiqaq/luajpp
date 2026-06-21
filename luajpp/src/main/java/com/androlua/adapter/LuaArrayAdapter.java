package com.androlua.adapter;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;

import com.androlua.core.LuaContext;
import com.androlua.internal.LuaLayout;
import com.androlua.internal.LuaLog;

import org.luaj.LuaError;
import org.luaj.LuaFunction;
import org.luaj.LuaTable;
import org.luaj.LuaValue;
import org.luaj.lib.jse.CoerceJavaToLua;

@SuppressWarnings("unused")
public class LuaArrayAdapter extends BaseAdapter implements Filterable {

    private final Object mLock = new Object();
    private LuaTable mData;
    private LuaTable mBaseData;
    private final LuaContext mContext;
    private final LuaTable mLayoutResource;
    private final LuaLayout mLayoutLoader;

    private Animation mAnimation;
    private LuaFunction mLuaFilter;
    private ArrayFilter mFilter;
    private boolean mNotifyOnChange = true;

    public LuaArrayAdapter(LuaContext context, LuaTable layoutResource) throws LuaError {
        this(context, layoutResource, new LuaTable());
    }

    public LuaArrayAdapter(LuaContext context, LuaTable layoutResource, LuaTable data) throws LuaError {
        mContext = context;
        mLayoutResource = layoutResource;
        mData = data;
        mBaseData = data;
        mLayoutLoader = new LuaLayout(context.getContext());
        mLayoutLoader.load(mLayoutResource, new LuaTable());
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

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getView(position, convertView, parent);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;

        if (convertView == null) {
            try {
                var holder = new LuaTable();
                var lview = mLayoutLoader.load(mLayoutResource, holder);
                view = lview.touserdata(View.class);
                view.setTag(holder);
            } catch (LuaError e) {
                LuaLog.getInstance().addError("LuaArrayAdapter", e);
                return new View(mContext.getContext());
            }
        } else {
            view = convertView;
        }

        AdapterHelper.setHelper(view, getItem(position));

        if (mAnimation != null) view.startAnimation(mAnimation);

        return view;
    }

    public LuaTable getData() {
        return mData;
    }

    public void setItem(int index, LuaValue value) {
        synchronized (mLock) {
            ensureBaseData();
            mBaseData.set(index + 1, value);
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void add(LuaValue item) {
        synchronized (mLock) {
            ensureBaseData();
            mBaseData.insert(mBaseData.length() + 1, item);
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void addAll(LuaTable items) {
        int length = items.length();
        synchronized (mLock) {
            ensureBaseData();
            for (int i = 1; i <= length; i++) {
                mBaseData.insert(mBaseData.length() + 1, items.get(i));
            }
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void insert(int position, Object item) {
        synchronized (mLock) {
            ensureBaseData();
            mBaseData.insert(position + 1, CoerceJavaToLua.coerce(item));
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void remove(int position) {
        synchronized (mLock) {
            ensureBaseData();
            mBaseData.remove(position + 1);
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void clear() {
        synchronized (mLock) {
            ensureBaseData();
            mBaseData.clear();
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void setNotifyOnChange(boolean notifyOnChange) {
        mNotifyOnChange = notifyOnChange;
    }

    public void setAnimation(Animation animation) {
        mAnimation = animation;
    }

    public Animation getAnimation() {
        return mAnimation;
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

    /**
     * 确保 mBaseData 不为 null，若为 null 则从 mData 重建
     */
    private void ensureBaseData() {
        if (mBaseData == null) {
            mBaseData = new LuaTable(mData);
        }
    }

    private class ArrayFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence prefix) {
            var results = new FilterResults();

            synchronized (mLock) {
                if (mBaseData == null) {
                    mBaseData = new LuaTable(mData);
                    results.values = mData;
                    results.count = mData.size();
                    return results;
                }
            }

            if (TextUtils.isEmpty(prefix)) {
                synchronized (mLock) {
                    results.values = new LuaTable(mBaseData);
                    results.count = mBaseData.size();
                    mBaseData = null;
                }
                return results;
            }

            if (mLuaFilter != null) {
                var newValues = new LuaTable();
                try {
                    mLuaFilter.jcall(new LuaTable(mBaseData), newValues, prefix);
                } catch (LuaError e) {
                    LuaLog.getInstance().addError("LuaArrayAdapter", e);
                }
                results.values = newValues;
                results.count = newValues.size();
                return results;
            }

            String prefixLower = prefix.toString().toLowerCase();
            LuaTable sourceValues;
            synchronized (mLock) {
                sourceValues = new LuaTable(mBaseData);
            }

            var filteredValues = new LuaTable();
            int count = sourceValues.size();

            for (int i = 1; i <= count; i++) {
                var value = sourceValues.get(i);
                String valueText = value.toString().toLowerCase();
                if (valueText.contains(prefixLower)) filteredValues.add(value);
            }

            results.values = filteredValues;
            results.count = filteredValues.size();
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