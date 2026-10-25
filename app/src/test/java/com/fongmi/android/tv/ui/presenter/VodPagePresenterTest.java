package com.fongmi.android.tv.ui.presenter;

import com.fongmi.android.tv.bean.*;
import com.fongmi.android.tv.player.vod.VodHistoryController;
import com.fongmi.android.tv.repository.*;
import com.fongmi.android.tv.utils.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class VodPagePresenterTest {
    @Test public void openUnscopedEntryRetainsItsConfigurationWhileExplicitSourceSwitchCanChangeIt() {
        Fixture f=new Fixture(); f.begin("first"); f.scope.set(PersistenceScope.single(2));
        assertEquals(1,f.page.configId("site","movie",2));
        assertEquals(2,f.page.configId("2@site","movie",2));
        assertEquals(2,f.page.configId("site","different-movie",2));
        f.begin("retry"); f.page.loadHistory(detail(),"retry"); f.flush();
        assertFalse(f.db.historyQueries.contains(2));
    }
    @Test public void delayedHistoryUsesOriginalScopeAndPreservesResumeProgress() {
        Fixture f=new Fixture(); History saved=MemoryPersistence.entry(1,"movie",45000); saved.setVodFlag("second"); f.storage.save(saved);
        f.begin("first"); f.page.loadHistory(detail(),"first"); f.scope.set(PersistenceScope.single(2)); f.flush();
        assertEquals(45000,f.page.history().getPosition()); assertEquals(1,f.delivered.size());
        assertFalse(f.db.historyQueries.contains(2));
    }
    @Test public void switchingDetailDiscardsAnAlreadyQueuedHistoryResult() {
        Fixture f=new Fixture(); f.begin("first"); f.page.loadHistory(detail(),"first"); f.worker.flush();
        f.begin("second"); f.scheduler.flush(); assertTrue(f.delivered.isEmpty());
        f.page.loadHistory(detail(),"second"); f.flush(); assertEquals(1,f.delivered.size());
    }
    @Test public void cancellingSourceRecoveryAlsoCancelsItsPendingHistoryDelivery() {
        Fixture f=new Fixture(); f.begin("first"); f.page.loadHistory(detail(),"first"); f.worker.flush();
        f.page.cancelRequest(); f.scheduler.flush(); assertTrue(f.delivered.isEmpty());
    }
    @Test public void closedPageCannotPublishOrStartAnotherHistoryLoad() {
        Fixture f=new Fixture(); f.begin("first"); f.page.loadHistory(detail(),"first"); f.worker.flush();
        f.page.close(); f.scheduler.flush(); f.page.loadHistory(detail(),"first"); assertTrue(f.delivered.isEmpty());
    }
    @Test public void savedRouteIsResolvedToTheCanonicalRouteWithoutMutatingTheSavedObject() {
        Fixture f=new Fixture(); f.page.detail(detail()); Flag saved=Flag.create("second");
        assertTrue(f.page.selectFlag(saved,true)); assertSame(f.page.flags().get(1),f.page.flag());
        Episode episode=f.page.chooseEpisode(saved,"episode 2",0);
        assertEquals("url-b2",episode.getUrl()); assertTrue(saved.getEpisodes().isEmpty());
        assertTrue(f.page.selectEpisode(episode)); assertEquals(1,f.page.episodeIndex());
    }
    @Test public void missingRouteFallsBackToFirstPlayableAndStaleEpisodeCannotReplaceSelection() {
        Fixture f=new Fixture(); f.page.detail(detail()); assertEquals(2,f.page.flags().size());
        Flag missing=Flag.create("removed"); assertFalse(f.page.selectFlag(missing,false));
        assertTrue(f.page.selectFlag(missing,true)); Episode selected=f.page.chooseEpisode(f.page.flag(),"unknown",99);
        assertTrue(f.page.selectEpisode(selected)); assertFalse(f.page.selectEpisode(Episode.create("other","stale-url")));
        assertSame(selected,f.page.flag().getEpisodes().get(f.page.episodeIndex()));
    }
    @Test public void reverseChangesPresentationOrderButRetainsSelectedEpisodeIdentity() {
        Fixture f=new Fixture(); f.page.detail(detail()); f.page.selectFlag(f.page.flags().get(1),false);
        Episode selected=f.page.chooseEpisode(f.page.flag(),"episode 2",0); f.page.selectEpisode(selected); f.page.reverseEpisodes();
        assertEquals(0,f.page.episodeIndex()); assertSame(selected,f.page.flag().getEpisodes().get(0));
    }
    @Test public void loadingHistoryCannotOverwriteLocallySavedPlaybackSettings() {
        Fixture f=new Fixture(); f.begin("first"); f.page.loadHistory(detail(),"first");
        History local=f.page.history(); local.setSpeed(2); f.controller.saveSnapshot(local);
        f.flush(); assertSame(local,f.page.history()); assertEquals(2,f.page.history().getSpeed(),0);
    }
    @Test public void favoriteToggleUsesEntryIdentityAfterConfigurationChanges() {
        Fixture f=new Fixture(); f.begin("first"); f.scope.set(PersistenceScope.single(2));
        String key="site@@@movie@@@1";
        assertTrue(f.page.toggleFavorite(key,1,"site","title","pic")); assertTrue(f.page.hasFavorite(key));
        assertEquals(1,f.keeps.getVod().get(0).getCid());
        assertFalse(f.page.toggleFavorite(key,1,"site","title","pic")); assertFalse(f.page.hasFavorite(key));
    }

    private static Vod detail() {
        Vod vod=new Vod(); vod.setVodName("movie");
        Flag empty=Flag.create("empty"), first=Flag.create("first"), second=Flag.create("second");
        first.getEpisodes().add(Episode.create("episode 1","url-a1"));
        second.getEpisodes().add(Episode.create("episode 1","url-b1")); second.getEpisodes().add(Episode.create("episode 2","url-b2"));
        vod.setVodFlags(new ArrayList<>(Arrays.asList(empty,first,second))); return vod;
    }
    private static final class Fixture {
        final MemoryPersistence db=new MemoryPersistence(); final Worker worker=new Worker(); final Scheduler scheduler=new Scheduler();
        final AtomicReference<PersistenceScope> scope=new AtomicReference<>(PersistenceScope.single(1));
        final HistoryStorage storage=new HistoryStorage(()->db.history,k->{},()->{},Runnable::run,Runnable::run,()->10000L,s->s);
        final KeepStorage keeps=new KeepStorage(()->db.keep,()->{},Runnable::run);
        final VodHistoryController controller=new VodHistoryController(new VodHistoryController.Persistence() {
            @Override public boolean enabled() { return true; }
            @Override public void save(History row,boolean async) { storage.save(row); }
        },()->10000L);
        final List<History> delivered=new ArrayList<>();
        final VodPagePresenter page=new VodPagePresenter(new HistoryRepository(storage,scope::get),
                new KeepRepository(keeps,scope::get,configs->Collections.emptyMap()),controller,
                new TaskScope(worker,scheduler,error->{ throw new AssertionError(error); }),5000,()->10000L,(history,vod)->delivered.add(history));
        void begin(String token) { page.begin(token,"site@@@movie@@@1",1,"",1,false); }
        void flush() { worker.flush(); scheduler.flush(); }
    }
    private static final class Worker extends AbstractExecutorService {
        final List<Runnable> work=new ArrayList<>(); boolean closed;
        @Override public void execute(Runnable command) { if(closed)throw new RejectedExecutionException();work.add(command); }
        void flush() { while(!work.isEmpty())work.remove(0).run(); }
        @Override public void shutdown() { closed=true; }
        @Override public List<Runnable> shutdownNow() { closed=true;List<Runnable> old=new ArrayList<>(work);work.clear();return old; }
        @Override public boolean isShutdown(){return closed;}
        @Override public boolean isTerminated(){return closed;}
        @Override public boolean awaitTermination(long n,TimeUnit unit){return closed;}
    }
    private static final class Scheduler implements TaskScheduler {
        final List<Runnable> ready=new ArrayList<>();
        @Override public void post(Runnable task,long delay) { if(delay==0)ready.add(task); }
        @Override public void remove(Runnable task) { ready.remove(task); }
        void flush() { while(!ready.isEmpty())ready.remove(0).run(); }
    }
}
