package com.androlua.util;

import java.text.Collator;
import java.util.Comparator;

/**
 * 基于 Locale 的字符串比较器。
 */

@SuppressWarnings("unused")
public class LocaleComparator implements Comparator<String> {

    private final Collator mCollator;

    public LocaleComparator() {
        mCollator = Collator.getInstance(java.util.Locale.getDefault());
    }

    @Override
    public int compare(String o1, String o2) {
        if (o1 == null && o2 == null) return 0;
        if (o1 == null) return -1;
        if (o2 == null) return 1;
        return mCollator.compare(o1, o2);
    }
}
