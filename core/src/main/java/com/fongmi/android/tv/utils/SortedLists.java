package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SortedLists {

    private SortedLists() {
    }

    public static <T> List<T> merge(List<T> left, List<T> right, Comparator<? super T> comparator) {
        List<T> merged = new ArrayList<>(left.size() + right.size());
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.size() && rightIndex < right.size()) {
            if (comparator.compare(left.get(leftIndex), right.get(rightIndex)) <= 0) merged.add(left.get(leftIndex++));
            else merged.add(right.get(rightIndex++));
        }
        while (leftIndex < left.size()) merged.add(left.get(leftIndex++));
        while (rightIndex < right.size()) merged.add(right.get(rightIndex++));
        return merged;
    }
}
