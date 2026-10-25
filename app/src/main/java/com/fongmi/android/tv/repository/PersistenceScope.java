package com.fongmi.android.tv.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** Captured at the application boundary, before work can be delayed or configuration can change. */
public final class PersistenceScope {
    private final int currentId;
    private final List<Integer> ids;
    public PersistenceScope(int currentId, List<Integer> ids) {
        this.currentId = currentId;
        List<Integer> values = new ArrayList<>(new LinkedHashSet<>(ids));
        if (values.isEmpty()) values.add(currentId);
        this.ids = Collections.unmodifiableList(values);
    }
    public int currentId() { return currentId; }
    public List<Integer> ids() { return ids; }
    public static PersistenceScope single(int id) { return new PersistenceScope(id, Collections.singletonList(id)); }
}
