package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Class;
import com.github.catvod.utils.Trans;

import java.util.ArrayList;
import java.util.List;

public class SiteCategoryUtil {

    private SiteCategoryUtil() {
    }

    public static List<Class> filter(List<String> categories, List<Class> types) {
        List<Class> items = new ArrayList<>();
        if (categories == null || categories.isEmpty()) return new ArrayList<>(types);
        for (String category : categories) {
            String normalized = Trans.s2t(category);
            for (Class item : types) {
                if (normalized.equals(item.getTypeName())) items.add(item);
            }
        }
        return items.isEmpty() ? new ArrayList<>(types) : items;
    }
}
