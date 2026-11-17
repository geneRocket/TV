package com.fongmi.android.tv.repository;

import com.fongmi.android.tv.bean.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScopedPersistenceTest {
    private HistoryStorage storage(MemoryPersistence db,List<Runnable> writes,AtomicInteger changes) {
        return new HistoryStorage(()->db.history,key->{},changes::incrementAndGet,writes::add,writes::add,()->5000L,value->value);
    }
    @Test public void capturedRepositoryDoesNotFollowLaterConfigurationSelection() {
        MemoryPersistence db=new MemoryPersistence(); List<Runnable> writes=new ArrayList<>();
        HistoryStorage storage=storage(db,writes,new AtomicInteger());
        storage.save(MemoryPersistence.entry(1,"movie",900)); storage.save(MemoryPersistence.entry(2,"movie",200));
        AtomicReference<PersistenceScope> active=new AtomicReference<>(PersistenceScope.single(1));
        HistoryRepository repository=new HistoryRepository(storage,active::get);
        HistoryRepository request=repository.in(repository.captureScope()); active.set(PersistenceScope.single(2));
        assertEquals(900,request.all().get(0).getPosition()); assertEquals(200,repository.all().get(0).getPosition());
    }
    @Test public void queuedDeleteOnlyDeletesTheScopeCapturedWhenUserConfirmed() {
        MemoryPersistence db=new MemoryPersistence(); HistoryStorage storage=storage(db,new ArrayList<>(),new AtomicInteger());
        storage.save(MemoryPersistence.entry(1,"a",10)); storage.save(MemoryPersistence.entry(2,"b",20));
        AtomicReference<PersistenceScope> active=new AtomicReference<>(PersistenceScope.single(1));
        HistoryRepository repository=new HistoryRepository(storage,active::get);
        Runnable delete=repository.prepareDeleteLoaded(); active.set(PersistenceScope.single(2)); delete.run();
        assertTrue(storage.get(1).isEmpty()); assertEquals(1,storage.get(2).size());
    }
    @Test public void independentStoresDoNotShareUnsavedCacheOrCancellation() {
        MemoryPersistence db=new MemoryPersistence(); List<Runnable> work=new ArrayList<>();
        HistoryStorage first=storage(db,work,new AtomicInteger()), second=storage(db,new ArrayList<>(),new AtomicInteger());
        History row=MemoryPersistence.entry(1,"movie",99); first.saveAsync(row);
        assertNotNull(first.find(row.getKey(),1)); assertNull(second.find(row.getKey(),1));
        second.cancelPendingWrites(); work.remove(0).run(); second.clearCache();
        assertEquals(99,second.find(row.getKey(),1).getPosition());
    }
    @Test public void asyncWriteOwnsSnapshotAndCancellationPreventsResurrection() {
        MemoryPersistence db=new MemoryPersistence(); List<Runnable> work=new ArrayList<>();
        AtomicInteger changes=new AtomicInteger(); HistoryStorage storage=storage(db,work,changes);
        History row=MemoryPersistence.entry(7,"movie",99); storage.saveAsync(row); row.setPosition(1);
        work.remove(0).run(); storage.clearCache(); assertEquals(99,storage.find(row.getKey(),7).getPosition());
        row.setCreateTime(200); storage.saveAsync(row); storage.delete(7); work.remove(0).run();
        assertTrue(db.historyRows.isEmpty()); assertEquals(2,changes.get());
    }
    @Test public void favoriteImportCapturesMappingAndRowsBeforeScheduling() {
        MemoryPersistence db=new MemoryPersistence(); List<Runnable> work=new ArrayList<>();
        KeepStorage storage=new KeepStorage(()->db.keep,()->{},work::add);
        Keep row=new Keep(); row.setCid(99); row.setKey("site@@@opaque@@@99"); row.setVodName("saved");
        Map<Integer,Integer> ids=new HashMap<>(); ids.put(99,7);
        storage.sync(ids,Collections.singletonList(row),false); ids.put(99,8); row.setVodName("draft");
        work.remove(0).run(); Keep saved=storage.find(7,"site@@@opaque@@@7");
        assertEquals("saved",saved.getVodName()); assertNull(storage.find(8,"site@@@opaque@@@8"));
    }
    @Test public void scopeIsImmutableAndDeDuplicatesIds() {
        List<Integer> ids=new ArrayList<>(Arrays.asList(1,1,2)); PersistenceScope scope=new PersistenceScope(1,ids); ids.clear();
        assertEquals(Arrays.asList(1,2),scope.ids());
        try { scope.ids().clear(); fail(); } catch(UnsupportedOperationException expected) { }
    }

    @Test public void deletingHistoryKeepsADifferentWorkWithTheSameTitle() {
        MemoryPersistence db = new MemoryPersistence();
        HistoryStorage storage = storage(db, new ArrayList<>(), new AtomicInteger());
        History first = MemoryPersistence.entry(10, "film-1933", 120000);
        first.setVodName("King Kong");
        first.setDuration(100 * 60000L);
        History second = MemoryPersistence.entry(20, "film-2005", 180000);
        second.setVodName("King Kong");
        second.setDuration(187 * 60000L);
        storage.save(first);
        storage.save(second);

        storage.delete(first, new PersistenceScope(10, java.util.Arrays.asList(10, 20)));

        assertNull(db.history.find(10, first.getKey()));
        assertEquals(180000L, db.history.find(20, second.getKey()).getPosition());
    }
}
