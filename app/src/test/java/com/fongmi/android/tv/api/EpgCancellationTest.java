package com.fongmi.android.tv.api;

import com.fongmi.android.tv.bean.Live;
import com.github.catvod.net.HttpCallScope;
import org.junit.Test;

import java.io.InterruptedIOException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class EpgCancellationTest {
    @Test public void cancelledRequestDoesNotBeginReadingLiveSources() throws Exception {
        boolean[] accessed = {false};
        Live live = new Live() {
            @Override public List<String> getEpgXml() {
                accessed[0] = true;
                return Collections.emptyList();
            }
        };
        expectCancellation(() -> EpgParser.start(live));
        assertFalse(accessed[0]);
    }

    @Test public void cancelledRequestDoesNotBeginParsingXml() throws Exception {
        expectCancellation(() -> EpgParser.getEpg("malformed XML", "channel"));
    }

    private void expectCancellation(java.util.concurrent.Callable<?> task) throws Exception {
        HttpCallScope scope = new HttpCallScope();
        try {
            scope.call(() -> {
                scope.close();
                return task.call();
            });
            fail("Cancelled EPG operation continued");
        } catch (InterruptedIOException expected) {
            assertFalse(Thread.currentThread().isInterrupted());
        }
    }
}
