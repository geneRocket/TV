package com.fongmi.android.tv.viewmodel;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import com.fongmi.android.tv.bean.SubtitleData;
import com.fongmi.android.tv.utils.TaskScheduler;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class SubtitleDeliveryTest {
    private final Queue<Runnable> main = new ArrayDeque<>();
    private SubtitleViewModel model;
    private AtomicInteger sequence;

    @Before public void setUp() throws Exception {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            public void executeOnDiskIO(Runnable task) { task.run(); }
            public void postToMainThread(Runnable task) { main.add(task); }
            public boolean isMainThread() { return true; }
        });
        model = new SubtitleViewModel(new OkHttpClient(), new TaskScheduler() {
            public void post(Runnable task, long delay) { main.add(task); }
            public void remove(Runnable task) { main.remove(task); }
        });
        Field field = SubtitleViewModel.class.getDeclaredField("requestSeq"); field.setAccessible(true);
        sequence = (AtomicInteger) field.get(model);
    }

    @After public void tearDown() {
        model.onCleared(); main.clear();
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test public void queuedOldResultIsDiscardedWhenANewSearchStarts() {
        int old = SubtitleViewModel.nextSearchRequest(sequence, -1);
        model.postSearchResult(old, new SubtitleData(), 10);
        SubtitleViewModel.nextSearchRequest(sequence, -1);
        drain();
        assertNull(model.searchResults().getValue());
    }

    @Test public void lateOldPublicationCannotReplaceTheNewResultOrPagination() throws Exception {
        int old = SubtitleViewModel.nextSearchRequest(sequence, -1);
        int current = SubtitleViewModel.nextSearchRequest(sequence, -1);
        SubtitleData expected = new SubtitleData();
        model.postSearchResult(current, expected, 3);
        model.postSearchResult(old, new SubtitleData(), 10);
        drain();
        assertSame(expected, model.searchResults().getValue());
        Field pages = SubtitleViewModel.class.getDeclaredField("pagesTotal"); pages.setAccessible(true);
        assertEquals(3, pages.getInt(model));
    }

    @Test public void currentResultStillPublishesOnTheMainQueue() {
        int current = SubtitleViewModel.nextSearchRequest(sequence, -1);
        SubtitleData expected = new SubtitleData();
        model.postSearchResult(current, expected, 3);
        assertNull(model.searchResults().getValue());
        drain();
        assertSame(expected, model.searchResults().getValue());
    }

    @Test public void clearingModelDiscardsAlreadyQueuedResults() {
        int current = SubtitleViewModel.nextSearchRequest(sequence, -1);
        model.postSearchResult(current, new SubtitleData(), 3);
        model.onCleared(); drain();
        assertNull(model.searchResults().getValue());
    }

    @Test public void staleErrorCannotClearTheNewResults() {
        int old = SubtitleViewModel.nextSearchRequest(sequence, -1);
        int current = SubtitleViewModel.nextSearchRequest(sequence, -1);
        SubtitleData expected = new SubtitleData();
        model.postSearchResult(current, expected, 3); drain();
        model.postSearchResult(old, null, null); drain();
        assertSame(expected, model.searchResults().getValue());
    }

    private void drain() { while (!main.isEmpty()) main.remove().run(); }
}
