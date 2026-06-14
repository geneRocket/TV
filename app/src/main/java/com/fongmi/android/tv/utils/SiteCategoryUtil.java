package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Class;
import com.github.catvod.utils.Trans;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SiteCategoryUtil {

    private SiteCategoryUtil() {
    }

    public static List<Class> filter(List<String> categories, List<Class> types) {
        if (categories == null || categories.isEmpty()) return new ArrayList<>(types);
        Map<String, Class> typeMap = new HashMap<>();
        for (Class item : types) typeMap.put(item.getTypeName(), item);
        List<Class> items = new ArrayList<>();
        for (String category : categories) {
            Class item = typeMap.get(Trans.s2t(category));
            if (item != null) items.add(item);
        }
        return items.isEmpty() ? new ArrayList<>(types) : items;
    }
}
