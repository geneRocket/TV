package com.fongmi.android.tv.repository;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.db.dao.HistoryDao;
import com.fongmi.android.tv.db.dao.KeepDao;
import java.util.*;

/** Deterministic DAO implementation: exercises real repository/store behavior without Room globals. */
public final class MemoryPersistence {
    public final Map<String, History> historyRows = new LinkedHashMap<>();
    public final Map<String, Keep> keepRows = new LinkedHashMap<>();
    public final List<Integer> historyQueries = new ArrayList<>();
    private String key(int cid, String key) { return cid + ":" + key; }
    public final HistoryDao history = new HistoryDao() {
        @Override public List<History> find(int cid) { return find(Collections.singletonList(cid)); }
        @Override public List<History> find(List<Integer> cids) {
            historyQueries.addAll(cids); List<History> result = new ArrayList<>();
            for (History row : historyRows.values()) if (cids.contains(row.getCid())) result.add(row.copy());
            return result;
        }
        @Override public History find(int cid, String key) {
            historyQueries.add(cid); History row = historyRows.get(key(cid,key)); return row == null ? null : row.copy();
        }
        @Override public List<History> findByName(int cid,String name) { return findByName(Collections.singletonList(cid),name); }
        @Override public List<History> findByName(List<Integer> cids,String name) {
            List<History> rows = find(cids); rows.removeIf(row -> !Objects.equals(name,row.getVodName())); return rows;
        }
        @Override public void delete(int cid,String key) { historyRows.remove(key(cid,key)); }
        @Override public void delete(int cid) { delete(Collections.singletonList(cid)); }
        @Override public void delete(List<Integer> cids) { historyRows.values().removeIf(row -> cids.contains(row.getCid())); }
        @Override public void delete() { historyRows.clear(); }
        @Override public Long insert(History row) {
            String key = key(row.getCid(),row.getKey()); if (historyRows.containsKey(key)) return -1L;
            historyRows.put(key,row.copy()); return 1L;
        }
        @Override public List<Long> insert(List<History> rows) { List<Long> ids=new ArrayList<>(); for(History row:rows) ids.add(insert(row)); return ids; }
        @Override public void update(History row) { historyRows.put(key(row.getCid(),row.getKey()),row.copy()); }
        @Override public void update(List<History> rows) { for(History row:rows) update(row); }
    };
    public final KeepDao keep = new KeepDao() {
        @Override public List<Keep> getVod() { return type(0); }
        @Override public List<Keep> getLive() { return type(1); }
        private List<Keep> type(int type) {
            List<Keep> result=new ArrayList<>(); for(Keep row:keepRows.values()) if(row.getType()==type) result.add(row.copy()); return result;
        }
        @Override public Keep find(int cid,String key) { Keep row=keepRows.get(key(cid,key)); return row==null?null:row.copy(); }
        @Override public Keep find(String key) { for(Keep row:keepRows.values()) if(row.getType()==1&&row.getKey().equals(key))return row.copy(); return null; }
        @Override public void delete(String key) { keepRows.values().removeIf(row->row.getType()==1&&row.getKey().equals(key)); }
        @Override public void delete(int cid,String key) { keepRows.remove(key(cid,key)); }
        @Override public void delete(int cid) { keepRows.values().removeIf(row->row.getType()==0&&row.getCid()==cid); }
        @Override public void delete() { keepRows.values().removeIf(row->row.getType()==0); }
        @Override public Long insert(Keep row) { String key=key(row.getCid(),row.getKey()); if(keepRows.containsKey(key))return -1L; keepRows.put(key,row.copy()); return 1L; }
        @Override public List<Long> insert(List<Keep> rows) { List<Long> ids=new ArrayList<>(); for(Keep row:rows)ids.add(insert(row)); return ids; }
        @Override public void update(Keep row) { keepRows.put(key(row.getCid(),row.getKey()),row.copy()); }
        @Override public void update(List<Keep> rows) { for(Keep row:rows)update(row); }
    };
    public static History entry(int cid,String id,long position) {
        History row=new History(); row.setCid(cid); row.setKey("site@@@"+id+"@@@"+cid);
        row.setVodName(id); row.setPosition(position); row.setCreateTime(100); return row;
    }
}
