package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.bean.Subtitle;
import org.junit.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.Assert.*;

public class SubtitleSearchControllerTest {
    private final Queue<Runnable> delivery = new ArrayDeque<>();
    private final FakeGateway gateway = new FakeGateway();
    private final List<SubtitleSearchState> results = new ArrayList<>();
    private final SubtitleSearchController controller = new SubtitleSearchController(gateway, delivery::add, value -> { if (!value.loading()) results.add(value); });
    private final List<Subtitle> resolved = new ArrayList<>();

    @Test public void oldQueuedResultAndPaginationCannotReplaceNewSearch() {
        controller.search("old", 1); Pending<SubtitleGateway.Page> old = gateway.searches.get(0);
        old.success(page("old", true, 10));
        controller.search("new", 1); gateway.searches.get(1).success(page("new", true, 3));
        old.success(page("late", true, 100)); drain();
        assertEquals(1, results.size()); assertEquals("new", last().items.get(0).getName());
        controller.search("new", 4); drain();
        assertEquals(2, gateway.searches.size()); assertEquals("new", last().items.get(0).getName());
        assertEquals(1, last().page); assertTrue(old.cancelled);
    }

    @Test public void clearingDiscardsQueuedSearchAndPreventsNewRequests() {
        controller.search("movie", 1); gateway.searches.get(0).success(page("movie", false, 1));
        controller.close(); drain(); controller.search("another", 1);
        assertTrue(results.isEmpty()); assertTrue(gateway.searches.get(0).cancelled); assertEquals(1,gateway.searches.size());
    }

    @Test public void simplifiedSearchStaysOnSelectedSourceAndCannotRepeat() {
        controller.search("Movie S01E02 1080p", 1);
        gateway.searches.get(0).success(page(null, false, -1)); drain();
        assertEquals(Arrays.asList("Movie S01E02 1080p", "Movie"), gateway.queries);
        gateway.searches.get(1).success(page(null, false, -1)); drain();
        assertTrue(gateway.fallbacks.isEmpty());
        gateway.searches.get(0).success(page(null, false, -1)); drain();
        assertTrue(gateway.fallbacks.isEmpty());
        assertTrue(last().items.isEmpty());
        controller.search("Movie S01E02 1080p", 2); drain();
        assertEquals(2,gateway.searches.size()); assertEquals(1,last().page);
    }

    @Test public void retryCannotTakeOverANewerSearch() {
        controller.search("old S01E02",1); Pending<SubtitleGateway.Page> old=gateway.searches.get(0);
        old.success(page(null,false,-1)); controller.search("new",1); drain();
        assertEquals(2,gateway.searches.size()); assertTrue(gateway.fallbacks.isEmpty());
    }

    @Test public void laterPageFailurePreservesResultsAndDoesNotSwitchProvider() {
        controller.search("Movie",1); gateway.searches.get(0).success(page("Movie",true,3)); drain();
        controller.search("Movie",2); gateway.searches.get(1).failure(new IOException()); drain();
        assertTrue(gateway.fallbacks.isEmpty()); assertEquals(1,last().page);
        assertEquals("Movie",last().items.get(0).getName()); assertFalse(last().hasNext);
        assertEquals(SubtitleSearchState.Phase.ERROR,last().phase);
        assertEquals("Movie",results.get(0).items.get(0).getName());
    }

    @Test public void changedTitleWithOldPageNumberStartsAtPageOne() {
        controller.search("old",1); controller.search("new",5);
        assertEquals(Arrays.asList(1,1),gateway.pages);
    }

    @Test public void aFailedPageDoesNotMarkTheProviderAsExhaustedForRetry() {
        controller.search("Movie",1); gateway.searches.get(0).success(page("first",true,3)); drain();
        controller.search("Movie",2); gateway.searches.get(1).failure(new IOException()); drain();
        controller.search("Movie",2);
        assertEquals(3,gateway.searches.size());
        assertEquals(Integer.valueOf(2),gateway.pages.get(2));
        gateway.searches.get(2).success(page("second",true,3)); drain();
        assertEquals(2,last().page);
    }

    @Test public void nextPageUsesSimplifiedQueryAndCommitsOnlyAfterSuccess() {
        controller.search("Movie S01E02",1); gateway.searches.get(0).success(page(null,false,-1)); drain();
        gateway.searches.get(1).success(page("Movie",true,3)); drain();
        controller.search("Movie S01E02",2);
        assertEquals("Movie",gateway.queries.get(2)); assertEquals(Integer.valueOf(2),gateway.pages.get(2));
        gateway.searches.get(2).success(page("Movie 2",true,3)); drain(); assertEquals(2,last().page); assertEquals("Movie 2",last().items.get(0).getName());
    }

    @Test public void detailCancelsBothFallbackSearchAndResolve() {
        controller.source(SubtitleSource.WEBSITE); controller.search("movie",1);
        controller.resolve(sub("selected"),resolved::add);
        controller.details(sub("archive"));
        assertTrue(gateway.fallbacks.get(0).cancelled); assertTrue(gateway.resolves.get(0).cancelled);
        gateway.fallbacks.get(0).success(page("stale",false,1));
        gateway.details.get(0).success(new SubtitleGateway.Page(Collections.singletonList(sub("file")),false,false,-1)); drain();
        assertEquals("file",last().items.get(0).getName()); assertFalse(last().items.get(0).getIsZip());
        assertEquals(SubtitleSearchState.Screen.FILES,last().screen);
        assertFalse(last().canPrevious()); assertFalse(last().canNext());
    }

    @Test public void cancelResolveDiscardsQueuedDeliveryWithoutMutatingInput() {
        Subtitle selected=sub("selected"); controller.resolve(selected,resolved::add);
        gateway.resolves.get(0).success("https://example.com/redirect"); controller.cancelResolve(); drain();
        assertTrue(resolved.isEmpty()); assertEquals("https://example.com/selected",selected.getUrl());
        assertTrue(gateway.resolves.get(0).cancelled);
    }

    @Test public void newerResolveWinsAndCapturesSelection() {
        controller.resolve(sub("old"),resolved::add);
        Subtitle selected=sub("new"); controller.resolve(selected,resolved::add); selected.setUrl("https://changed");
        gateway.resolves.get(1).success("https://example.com/current");
        gateway.resolves.get(0).success("https://example.com/stale"); drain();
        assertEquals(1,resolved.size()); assertEquals("https://example.com/current",resolved.get(0).getUrl());
        assertEquals("https://changed",selected.getUrl());
    }

    @Test public void missingLocationAndNetworkFailureKeepCapturedOriginal() {
        controller.resolve(sub("one"),resolved::add); gateway.resolves.get(0).success(""); drain();
        controller.resolve(sub("two"),resolved::add); gateway.resolves.get(1).failure(new IOException()); drain();
        assertEquals("https://example.com/one",resolved.get(0).getUrl());
        assertEquals("https://example.com/two",resolved.get(1).getUrl());
    }

    @Test public void closeDiscardsQueuedResolveAndCancelsAllWork() {
        controller.search("movie",1); controller.resolve(sub("one"),resolved::add);
        gateway.resolves.get(0).failure(new IOException()); controller.close(); drain();
        assertTrue(resolved.isEmpty()); assertTrue(gateway.searches.get(0).cancelled); assertTrue(gateway.resolves.get(0).cancelled);
    }

    @Test public void emptySearchPublishesOnDeliveryQueueWithoutNetwork() {
        controller.search(" ",1); assertTrue(results.isEmpty()); drain();
        assertTrue(last().items.isEmpty()); assertEquals(1,last().page); assertTrue(gateway.searches.isEmpty());
    }

    @Test public void staleErrorCannotClearSuccessfulNewSearch() {
        controller.search("old",1); controller.search("new",1);
        gateway.searches.get(1).success(page("new",false,1)); gateway.searches.get(0).failure(new IOException()); drain();
        assertEquals(1,results.size()); assertEquals("new",last().items.get(0).getName());
    }

    @Test public void navigationIsDisabledWhileLoadingAndFilesHaveNoSearchPagination() {
        controller.search("movie",1); gateway.searches.get(0).success(page("movie",true,3)); drain();
        assertTrue(controller.state().canNext()); controller.next(); controller.next();
        assertEquals(2,gateway.searches.size()); assertFalse(controller.state().canNext());
        assertEquals(1,controller.state().page);
        gateway.searches.get(1).success(page("page two",false,2)); drain();
        assertEquals(2,controller.state().page); assertTrue(controller.state().canPrevious());
        controller.previous(); assertEquals(Integer.valueOf(1),gateway.pages.get(2));
        gateway.searches.get(2).success(page("movie",true,3)); drain();
        controller.details(sub("archive")); gateway.details.get(0).success(page("file",false,1)); drain();
        int count=gateway.searches.size(); controller.previous(); controller.next();
        assertEquals(count,gateway.searches.size()); assertFalse(controller.state().canPrevious());
    }

    @Test public void synchronousProviderAndReentrantCloseStillCancelTheReturnedRequest() {
        boolean[] cancelled={false};
        FakeGateway immediate=new FakeGateway() {
            @Override public Request search(String query,int page,Callback<Page> callback) {
                callback.success(SubtitleSearchControllerTest.page("movie",false,1));
                return () -> cancelled[0]=true;
            }
        };
        SubtitleSearchController[] owner=new SubtitleSearchController[1];
        owner[0]=new SubtitleSearchController(immediate,Runnable::run,value -> {
            if (value.phase==SubtitleSearchState.Phase.READY) owner[0].close();
        });
        owner[0].search("movie",1); assertTrue(cancelled[0]);
    }

    @Test public void cancellingResolveRestoresAcceptedScreenInsteadOfLeavingASpinner() {
        controller.search("movie",1); gateway.searches.get(0).success(page("movie",true,3)); drain();
        controller.resolve(sub("file"),resolved::add); assertTrue(controller.state().loading());
        controller.cancelResolve(); drain();
        assertEquals(SubtitleSearchState.Phase.READY,controller.state().phase);
        assertTrue(controller.state().canNext()); assertEquals("movie",controller.state().items.get(0).getName());
    }

    @Test public void changingSourceDiscardsQueuedResultsAndRestartsAtPageOne() {
        controller.search("movie",1);gateway.searches.get(0).success(page("old",true,3));
        controller.source(SubtitleSource.SUBHD);
        gateway.searches.get(1).success(page("subhd",true,3));drain();
        assertEquals(SubtitleSource.SUBHD,controller.state().source);
        assertEquals("subhd",controller.state().items.get(0).getName());assertEquals(1,controller.state().page);
        assertTrue(gateway.searches.get(0).cancelled);
    }

    @Test public void failedSourceSwitchDoesNotMislabelThePreviousSitesResults() {
        controller.search("movie",1);gateway.searches.get(0).success(page("assrt",true,3));drain();
        controller.source(SubtitleSource.SUBHD);gateway.searches.get(1).failure(new IOException());drain();
        assertEquals(SubtitleSource.SUBHD,controller.state().source);assertTrue(controller.state().items.isEmpty());
        assertEquals(SubtitleSearchState.Phase.ERROR,controller.state().phase);assertTrue(gateway.fallbacks.isEmpty());
    }

    @Test public void traditionalAliasRetriesAreBoundedAndRememberedForLaterPages() {
        SubtitleSearchController owner=new SubtitleSearchController(gateway,delivery::add,value->{},SubtitleSource.SUBHD,
                new SubtitleQueries(value->value.replace("無間道","无间道"),value->value.replace("无间道","無間道")));
        owner.search("无间道",1);gateway.searches.get(0).success(page(null,false,-1));drain();
        assertEquals(Arrays.asList("无间道","無間道"),gateway.queries);
        gateway.searches.get(1).success(page("found",true,3));drain();owner.next();
        assertEquals("無間道",gateway.queries.get(2)); assertEquals(Integer.valueOf(2),gateway.pages.get(2));
    }

    @Test public void websiteResultsNeverReachThePlayerResolveCallback() {
        Subtitle external=sub("website");external.setExternal(true);
        controller.resolve(external,resolved::add);drain();
        assertTrue(gateway.resolves.isEmpty());assertTrue(resolved.isEmpty());
    }

    @Test public void sortingDoesNotFetchAgainAndSurvivesCancellingAResolve() {
        Subtitle english=sub("English"),cantonese=sub("粤语");english.setMovieTitle("movie");cantonese.setMovieTitle("movie");
        english.setLanguage("English");cantonese.setLanguage("粵語");
        controller.search("movie",1);gateway.searches.get(0).success(new SubtitleGateway.Page(Arrays.asList(english,cantonese),false,false,1));drain();
        controller.preference(SubtitlePreference.ALL);drain();assertSame(english,controller.state().items.get(0));
        controller.resolve(english,resolved::add);controller.preference(SubtitlePreference.CANTONESE);controller.cancelResolve();drain();
        assertEquals(1,gateway.searches.size());assertEquals(SubtitlePreference.CANTONESE,controller.state().preference);
        assertSame(cantonese,controller.state().items.get(0));assertFalse(controller.state().loading());
    }

    @Test public void retryRepeatsTheFailedPageAndIgnoresDoubleClicksWhileLoading() {
        controller.search("Movie",1);gateway.searches.get(0).success(page("first",true,3));drain();
        controller.next();gateway.searches.get(1).failure(new IOException());drain();
        controller.retry();controller.retry();
        assertEquals(Arrays.asList(1,2,2),gateway.pages);
        gateway.searches.get(2).success(page("second",true,3));drain();
        assertEquals(2,last().page);assertEquals("second",last().items.get(0).getName());
    }

    @Test public void returningFromFilesRestoresTheSearchPageWithoutRefetching() {
        controller.search("Movie",1);gateway.searches.get(0).success(page("first",true,3));drain();
        controller.next();gateway.searches.get(1).success(page("second",true,3));drain();
        controller.details(sub("archive"));gateway.details.get(0).success(page("file",false,1));drain();
        assertTrue(controller.backToSearch());drain();
        assertEquals(2,last().page);assertEquals("second",last().items.get(0).getName());
        assertTrue(last().canNext());assertTrue(last().canPrevious());assertEquals(2,gateway.searches.size());
        controller.next();assertEquals(Integer.valueOf(3),gateway.pages.get(2));
    }

    @Test public void returningCancelsPendingFilesAndLateResolveCallbacks() {
        controller.search("Movie",1);gateway.searches.get(0).success(page("archive",false,1));drain();
        controller.details(sub("archive"));assertTrue(controller.backToSearch());
        gateway.details.get(0).success(page("stale file",false,1));drain();
        assertTrue(gateway.details.get(0).cancelled);assertEquals("archive",last().items.get(0).getName());
        controller.details(sub("archive"));gateway.details.get(1).success(page("file",false,1));drain();
        controller.resolve(sub("file"),resolved::add);assertTrue(controller.backToSearch());
        gateway.resolves.get(0).success("https://example.com/stale");drain();
        assertTrue(resolved.isEmpty());assertEquals(SubtitleSearchState.Screen.SEARCH,last().screen);
    }

    @Test public void fileFailureCanRetryOrReturnWithoutLosingTheSearch() {
        controller.search("Movie",1);gateway.searches.get(0).success(page("archive",true,3));drain();
        controller.details(sub("archive"));gateway.details.get(0).failure(new IOException());drain();
        controller.retry();assertEquals(2,gateway.details.size());
        gateway.details.get(1).failure(new IOException());drain();
        assertTrue(controller.backToSearch());drain();
        assertEquals(SubtitleSearchState.Phase.READY,last().phase);assertTrue(last().canNext());
        assertEquals("archive",last().items.get(0).getName());
    }

    @Test public void newQueryAndSourceDiscardOldArchiveParents() {
        controller.search("old",1);gateway.searches.get(0).success(page("old",false,1));drain();
        controller.details(sub("archive"));controller.search("new",1);
        assertFalse(controller.backToSearch());
        gateway.searches.get(1).success(page("new",false,1));drain();
        controller.details(sub("archive"));controller.source(SubtitleSource.SUBHD);
        assertFalse(controller.backToSearch());
        gateway.searches.get(2).success(page("subhd",false,1));drain();
        assertEquals(SubtitleSource.SUBHD,last().source);
    }

    @Test public void returningAppliesTheCurrentSortPreferenceToTheOriginalSearchItems() {
        Subtitle english=sub("English"), cantonese=sub("Cantonese");
        english.setMovieTitle("Movie");english.setLanguage("English");
        cantonese.setMovieTitle("Movie");cantonese.setLanguage("粤语");
        controller.preference(SubtitlePreference.ALL);
        controller.search("Movie",1);gateway.searches.get(0).success(new SubtitleGateway.Page(Arrays.asList(english,cantonese),false,true,3));drain();
        controller.details(sub("archive"));gateway.details.get(0).success(page("file",false,1));drain();
        controller.preference(SubtitlePreference.CANTONESE);
        assertTrue(controller.backToSearch());drain();
        assertSame(cantonese,last().items.get(0));assertEquals(1,gateway.searches.size());
        assertEquals(SubtitlePreference.CANTONESE,last().preference);
    }

    @Test public void returningToAFailedSearchKeepsItsOriginalRetryTarget() {
        controller.search("Movie",1);gateway.searches.get(0).success(page("archive",true,3));drain();
        controller.next();gateway.searches.get(1).failure(new IOException());drain();
        controller.details(sub("archive"));gateway.details.get(0).success(page("file",false,1));drain();
        assertTrue(controller.backToSearch());drain();
        assertEquals(SubtitleSearchState.Phase.ERROR,last().phase);
        controller.retry();assertEquals(Arrays.asList(1,2,2),gateway.pages);
    }

    private SubtitleSearchState last() { return results.get(results.size()-1); }
    private void drain() { while (!delivery.isEmpty()) delivery.remove().run(); }
    static Subtitle sub(String name) { return new Subtitle(name,"https://example.com/"+name,false); }
    static SubtitleGateway.Page page(String name,boolean next,int total) {
        return new SubtitleGateway.Page(name==null?Collections.emptyList():Collections.singletonList(sub(name)),true,next,total);
    }
    static class Pending<T> implements SubtitleGateway.Request {
        final SubtitleGateway.Callback<T> callback; boolean cancelled;
        Pending(SubtitleGateway.Callback<T> callback) { this.callback=callback; }
        public void cancel() { cancelled=true; }
        void success(T result) { callback.success(result); }
        void failure(Throwable error) { callback.failure(error); }
    }
    static class FakeGateway implements SubtitleGateway {
        final List<Pending<Page>> searches=new ArrayList<>(),fallbacks=new ArrayList<>(),details=new ArrayList<>();
        final List<Pending<String>> resolves=new ArrayList<>();
        final List<String> queries=new ArrayList<>(); final List<Integer> pages=new ArrayList<>();
        public Request search(String query,int page,Callback<Page> callback) {
            queries.add(query);pages.add(page);return add(searches,callback);
        }
        public Request fallback(String query,Callback<Page> callback) {return add(fallbacks,callback);}
        public Request details(Subtitle item,Callback<Page> callback) {return add(details,callback);}
        public Request resolve(Subtitle item,Callback<String> callback) {return add(resolves,callback);}
        private <T> Pending<T> add(List<Pending<T>> list,Callback<T> callback) {
            Pending<T> pending=new Pending<>(callback);list.add(pending);return pending;
        }
    }
}
