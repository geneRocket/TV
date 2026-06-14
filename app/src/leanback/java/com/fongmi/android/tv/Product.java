package com.fongmi.android.tv;

import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.HashMap;
import java.util.Map;

public class Product {

    private static final Map<String, int[]> cache = new HashMap<>();

    public static void clear() {
        cache.clear();
    }

    public static int getDeviceType() {
        return 0;
    }

    public static int getColumn() {
        return Math.abs(Setting.getSize() - 7);
    }

    public static int getColumn(Style style) {
        return style.isLand() ? getColumn() - 1 : getColumn();
    }

    public static int[] getSpec(Style style) {
        String key = style.getViewType() + "_" + style.getRatio() + "_" + Setting.getSize();
        if (cache.containsKey(key)) return cache.get(key);
        int column = getColumn(style);
        int space = ResUtil.dp2px(48) + ResUtil.dp2px(16 * (column - 1));
        if (style.isOval()) space += ResUtil.dp2px(column * 16);
        int[] spec = getSpec(space, column, style);
        cache.put(key, spec);
        return spec;
    }

    public static int[] getSpec(int space, int column, Style style) {
        int base = ResUtil.getScreenWidth() - space;
        int width = base / column;
        int height = (int) (width / style.getRatio());
        return new int[]{width, height};
    }

    public static int getEms() {
        return Math.min(ResUtil.getScreenWidth() / ResUtil.sp2px(24), 35);
    }
}
