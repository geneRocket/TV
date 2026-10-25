package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.bean.Subtitle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Owns search, provider fallback, pagination and resolve lifetimes independently of Android. */
public final class SubtitleSearchController implements AutoCloseable {
    private final SubtitleGateway gateway;
    private final Executor delivery;
    private final Consumer<SubtitleSearchState> results;
    private final SubtitleQueries queries;
    private SubtitleSource source;
    private SubtitlePreference preference = SubtitlePreference.CHINESE;
    private List<String> aliases = Collections.emptyList();
    private List<Subtitle> acceptedItems = Collections.emptyList();
    private Operation listing, resolving;
    private volatile SubtitleSearchState state = SubtitleSearchState.initial();
    private SubtitleSearchState beforeResolve;
    private String title = "", keyword = "";
    private int total = -1;
    private boolean exhausted, closed;

    public SubtitleSearchController(SubtitleGateway gateway, Executor delivery, Consumer<SubtitleSearchState> results) {
        this(gateway, delivery, results, SubtitleSource.ASSRT, SubtitleQueries.identity());
    }

    public SubtitleSearchController(SubtitleGateway gateway, Executor delivery, Consumer<SubtitleSearchState> results,
                                    SubtitleSource source, SubtitleQueries queries) {
        this.gateway = gateway; this.delivery = delivery; this.results = results;
        this.source = source; this.queries = queries;
        state = makeState("", 1, SubtitleSearchState.Screen.SEARCH, SubtitleSearchState.Phase.IDLE,
                Collections.emptyList(), false, Collections.emptyList());
    }

    public synchronized void search(String value, int page) {
        if (closed) return;
        String query = value == null ? "" : value.trim();
        int number = Math.max(1, page);
        if (number == 1 || !query.equals(title)) {
            title = query; aliases = queries.variants(query);
            keyword = aliases.isEmpty() ? "" : aliases.get(0); total = -1; exhausted = false;
            number = 1;
        }
        if (number > state.page && (exhausted || (total > 0 && number > total))) return;
        Operation operation = beginListing();
        boolean sameQuery = state.query.equals(query) && state.screen == SubtitleSearchState.Screen.SEARCH;
        if (!sameQuery) acceptedItems = Collections.emptyList();
        SubtitleSearchState previous = sameQuery ? state : SubtitleSearchState.initial();
        state = makeState(query, previous.page, SubtitleSearchState.Screen.SEARCH,
                query.isEmpty() ? SubtitleSearchState.Phase.IDLE : SubtitleSearchState.Phase.LOADING,
                sameQuery ? previous.items : Collections.emptyList(), false, Collections.emptyList());
        emit(operation);
        if (keyword.isEmpty()) {
            int requestedPage = number;
            deliver(operation, operation.step, () -> publish(operation,
                    new SubtitleGateway.Page(Collections.emptyList(), true, false, total), requestedPage, SubtitleSearchState.Screen.SEARCH));
        } else search(operation, keyword, number, 0);
    }

    private void search(Operation operation, String query, int page, int variant) {
        this.<SubtitleGateway.Page>launch(operation, callback -> gateway.search(source, query, page, callback), result -> {
            if (result.items.isEmpty() && page == 1) {
                if (variant + 1 < aliases.size()) {
                    keyword = aliases.get(variant + 1);
                    search(operation, keyword, page, variant + 1);
                } else publish(operation, result, page, SubtitleSearchState.Screen.SEARCH);
            } else publish(operation, result, page, SubtitleSearchState.Screen.SEARCH);
        }, error -> {
            // Pagination belongs to the provider that produced page 1. A later failure
            // must not replace those results with another provider's first page.
            fail(operation);
        });
    }

    public synchronized void details(Subtitle subtitle) {
        if (closed) return;
        Operation operation = beginListing();
        acceptedItems = Collections.emptyList();
        state = makeState(title, 1, SubtitleSearchState.Screen.FILES,
                SubtitleSearchState.Phase.LOADING, Collections.emptyList(), false, Collections.emptyList());
        emit(operation);
        if (subtitle == null || empty(subtitle.getUrl())) {
            deliver(operation, operation.step, () -> fail(operation)); return;
        }
        this.<SubtitleGateway.Page>launch(operation, callback -> gateway.details(copy(subtitle), callback),
                result -> publish(operation, result, 1, SubtitleSearchState.Screen.FILES), error -> fail(operation));
    }

    public synchronized void resolve(Subtitle subtitle, Consumer<Subtitle> loaded) {
        if (closed) return;
        cancelResolve();
        if (subtitle == null || subtitle.isExternal() || empty(subtitle.getUrl()) || loaded == null) return;
        if (listing != null) listing.cancel();
        Operation operation = resolving = new Operation();
        beforeResolve = state;
        state = makeState(state.query, state.page, state.screen,
                SubtitleSearchState.Phase.RESOLVING, state.items, false, state.unavailable);
        emit(operation);
        Subtitle selected = copy(subtitle);
        this.<String>launch(operation, callback -> gateway.resolve(selected, callback), location -> {
            if (!empty(location)) selected.setUrl(location);
            operation.finished = true;
            loaded.accept(selected);
        }, error -> { operation.finished = true; loaded.accept(selected); });
    }

    public synchronized void cancelResolve() {
        if (resolving != null) resolving.cancel();
        resolving = null;
        if (beforeResolve != null && state.phase == SubtitleSearchState.Phase.RESOLVING) {
            state = makeState(beforeResolve.query,beforeResolve.page,beforeResolve.screen,beforeResolve.phase,
                    SubtitleRanking.order(acceptedItems,aliases,preference),beforeResolve.hasNext,beforeResolve.unavailable);
            emitState();
        }
        beforeResolve = null;
    }

    private Operation beginListing() {
        if (listing != null) listing.cancel();
        cancelResolve();
        return listing = new Operation();
    }

    public SubtitleSearchState state() { return state; }
    public synchronized void previous() { if (state.canPrevious()) search(title, state.page - 1); }
    public synchronized void next() { if (state.canNext()) search(title, state.page + 1); }

    public synchronized void source(SubtitleSource value) {
        if (closed || value == null || value == source) return;
        cancelResolve();
        source = value; acceptedItems = Collections.emptyList();
        state = makeState(title,1,SubtitleSearchState.Screen.SEARCH,SubtitleSearchState.Phase.IDLE,
                acceptedItems,false,Collections.emptyList());
        search(title, 1);
    }
    public synchronized void preference(SubtitlePreference value) {
        if (closed || value == null || value == preference) return;
        preference = value;
        state = makeState(state.query,state.page,state.screen,state.phase,
                SubtitleRanking.order(acceptedItems,aliases,value),state.hasNext,state.unavailable);
        emitState();
    }
    private SubtitleSearchState makeState(String query, int page, SubtitleSearchState.Screen screen,
                                         SubtitleSearchState.Phase phase, List<Subtitle> items,
                                         boolean next, List<String> unavailable) {
        return new SubtitleSearchState(query,page,screen,phase,items,next,source,preference,unavailable);
    }

    private void emit(Operation operation) {
        SubtitleSearchState value = state;
        delivery.execute(() -> {
            synchronized (SubtitleSearchController.this) {
                if (!closed && !operation.cancelled && state == value) results.accept(value);
            }
        });
    }

    private void emitState() {
        SubtitleSearchState value = state;
        delivery.execute(() -> {
            synchronized (SubtitleSearchController.this) {
                if (!closed && state == value) results.accept(value);
            }
        });
    }

    private void publish(Operation operation, SubtitleGateway.Page page, int number, SubtitleSearchState.Screen screen) {
        operation.finished = true;
        acceptedItems = new ArrayList<>(page.items);
        if (number == 1) total = page.total;
        exhausted = !page.hasNext;
        state = makeState(title, number, screen, SubtitleSearchState.Phase.READY, SubtitleRanking.order(acceptedItems, aliases, preference), page.hasNext, page.unavailable);
        results.accept(state);
    }

    private void fail(Operation operation) {
        operation.finished = true;
        exhausted = true;
        state = makeState(state.query, state.page, state.screen,
                SubtitleSearchState.Phase.ERROR, state.items, false, state.unavailable);
        results.accept(state);
    }

    private interface Start<T> { SubtitleGateway.Request run(SubtitleGateway.Callback<T> callback); }
    private <T> void launch(Operation operation, Start<T> start, Consumer<T> success, Consumer<Throwable> failure) {
        if (closed || operation.cancelled || operation.finished) return;
        int step = ++operation.step;
        SubtitleGateway.Callback<T> callback = new SubtitleGateway.Callback<T>() {
            public void success(T value) { deliver(operation, step, () -> success.accept(value)); }
            public void failure(Throwable error) { deliver(operation, step, () -> failure.accept(error)); }
        };
        try {
            SubtitleGateway.Request request = start.run(callback);
            if (request != null) {
                if (operation.cancelled || closed) request.cancel();
                else operation.requests.add(request);
            }
        } catch (RuntimeException error) { callback.failure(error); }
    }

    private void deliver(Operation operation, int step, Runnable action) {
        delivery.execute(() -> {
            synchronized (SubtitleSearchController.this) {
                if (closed || operation.cancelled || operation.finished || operation.step != step) return;
                action.run();
            }
        });
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        if (listing != null) listing.cancel();
        cancelResolve();
    }

    private static boolean empty(String value) { return value == null || value.isEmpty(); }
    private static Subtitle copy(Subtitle value) {
        Subtitle copy = new Subtitle(value.getName(), value.getUrl(), value.getIsZip());
        copy.setSource(value.getSource()); copy.setLanguage(value.getLanguage()); copy.setExternal(value.isExternal());
        copy.setMovieTitle(value.getMovieTitle()); return copy;
    }
    private static final class Operation {
        final List<SubtitleGateway.Request> requests = new ArrayList<>();
        boolean cancelled, finished;
        int step;
        void cancel() {
            cancelled = true;
            for (SubtitleGateway.Request request : requests) request.cancel();
            requests.clear();
        }
    }
}
