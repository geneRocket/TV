package com.fongmi.android.tv.bean;

import org.junit.Test;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.junit.Assert.*;

public class RulePatternsTest {
    @Test public void concurrentVideoMatchersCannotReadPartiallyCompiledPatterns() throws Exception {
        concurrentRead(false);
    }

    @Test public void concurrentExclusionsCannotReadPartiallyCompiledPatterns() throws Exception {
        concurrentRead(true);
    }

    @Test public void failedCompilationDoesNotCacheAPartialRule() {
        for (boolean exclude : new boolean[]{false, true}) {
            Supplier<List<Pattern>> read = reader(Arrays.asList("video", "["), exclude);
            for (int attempt = 0; attempt < 2; attempt++) {
                assertThrows(PatternSyntaxException.class, read::get);
            }
        }
    }

    private void concurrentRead(boolean exclude) throws Exception {
        CountDownLatch compiling = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        List<String> source = new AbstractList<String>() {
            @Override public int size() { return 2; }
            @Override public String get(int index) {
                if (first.compareAndSet(true, false)) {
                    compiling.countDown();
                    try {
                        if (!resume.await(5, TimeUnit.SECONDS)) throw new AssertionError("Compilation was not resumed");
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(error);
                    }
                }
                return "pattern" + index;
            }
        };
        Supplier<List<Pattern>> read = reader(source, exclude);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<List<Pattern>> original = workers.submit(read::get);
            assertTrue(compiling.await(3, TimeUnit.SECONDS));
            Future<List<Pattern>> concurrent = workers.submit(read::get);
            try {
                assertEquals(2, concurrent.get(200, TimeUnit.MILLISECONDS).size());
            } catch (TimeoutException waitingForCompilation) {
                // Serializing compilation is also valid; returning a partial list is not.
            } finally {
                resume.countDown();
            }
            assertEquals(2, original.get(3, TimeUnit.SECONDS).size());
            assertEquals(2, concurrent.get(3, TimeUnit.SECONDS).size());
            assertEquals(2, read.get().size());
        } finally {
            resume.countDown();
            workers.shutdownNow();
        }
    }

    private Supplier<List<Pattern>> reader(List<String> source, boolean exclude) {
        Rule rule = new Rule("test") {
            @Override public List<String> getRegex() { return source; }
            @Override public List<String> getExclude() { return source; }
        };
        return exclude ? rule::getExcludePatterns : rule::getRegexPatterns;
    }
}
