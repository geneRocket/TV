package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.bean.Subtitle;
import okhttp3.*;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import org.junit.Test;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class SubtitleProviderTest {
    @Test public void assrtSearchRanksDeduplicatesAndParsesPaging() {
        SubtitleGateway.Page page=AssrtSubtitleParser.search("<a class='introtitle' href='/other'>Other</a>"
                +"<a class='introtitle' href='//assrt.net/movie'>Movie</a>"
                +"<a class='introtitle' href='//assrt.net/movie'>duplicate</a>"
                +"<div class='pagination'><a>1 / 3</a><a>下一页</a></div>","Movie",1);
        assertEquals(2,page.items.size()); assertEquals("Movie",page.items.get(0).getName());
        assertEquals("https://assrt.net/movie",page.items.get(0).getUrl()); assertTrue(page.hasNext); assertEquals(3,page.total);
        assertTrue(page.items.get(0).getIsZip());
    }

    @Test public void archiveNamesAreEncodedAsOnePathSegment() {
        SubtitleGateway.Page page=AssrtSubtitleParser.details("<div id='detail-filelist'><a class='waves-effect' "
                +"onclick='onthefly(\"12\",\"34\",\"中 文/a.srt\")'><span id='filelist-name'>字幕</span></a></div>","https://assrt.net/sub/1");
        Subtitle item=page.items.get(0); assertEquals("字幕",item.getName()); assertFalse(item.getIsZip());
        assertEquals("https://secure.assrt.net/download/12/-/34/%E4%B8%AD%20%E6%96%87%2Fa.srt",item.getUrl());
    }

    @Test public void websiteParsesMissingOptionalFieldsAndDeduplicates() {
        SubtitleGateway.Page page=WebsiteSubtitleParser.search("{\"data\":[null,"
                +"{\"download_url\":\"/movie.srt\",\"release\":\"Movie\",\"language\":{\"name\":\"中文\"}},"
                +"{\"download_url\":\"/movie.srt\",\"release\":\"Duplicate\"},"
                +"{\"download_url\":\"https://cdn.test/other.srt\",\"film_title\":\"Other\"},{}]}","Movie");
        assertEquals(2,page.items.size()); assertEquals("https://subtitles.website/strapi/movie.srt",page.items.get(0).getUrl());
        assertEquals("Movie · 中文 · Subtitles.website",page.items.get(0).getName()); assertFalse(page.hasNext);
        assertFalse(page.items.get(0).getIsZip());
    }

    @Test public void malformedDocumentsFailInsteadOfPublishingPartialResults() {
        try { WebsiteSubtitleParser.search("not json","Movie"); fail(); } catch (RuntimeException expected) { }
        try { AssrtSubtitleParser.details("<html>challenge</html>","https://assrt.net/sub/1"); fail(); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void httpSearchClosesBodyAndEncodesQueryWithoutAnyNetwork() throws Exception {
        TrackingBody body=new TrackingBody("<a class='introtitle' href='/sub/1'>中 文</a>");
        AtomicReference<Request> sent=new AtomicReference<>();
        OkHttpClient client=client(200,body,sent);
        try {
            Probe<SubtitleGateway.Page> callback=new Probe<>();
            new HttpSubtitleGateway(client).search("中 文 & x",2,callback); callback.await();
            assertNull(callback.error); assertEquals(1,callback.value.items.size()); assertTrue(body.closed);
            assertEquals("中 文 & x",sent.get().url().queryParameter("searchword"));
            assertEquals("2",sent.get().url().queryParameter("page")); assertNotNull(sent.get().header("Referer"));
        } finally { shutdown(client); }
    }

    @Test public void httpFailureClosesBodyAndReportsFailureOnce() throws Exception {
        TrackingBody body=new TrackingBody("unavailable"); OkHttpClient client=client(503,body,new AtomicReference<>());
        try {
            Probe<SubtitleGateway.Page> callback=new Probe<>();
            new HttpSubtitleGateway(client).fallback("Movie",callback); callback.await();
            assertNotNull(callback.error); assertNull(callback.value); assertTrue(body.closed);
        } finally { shutdown(client); }
    }

    @Test public void aggregateRoutesEachProviderAndKeepsResultsWhenAssrtFails() throws Exception {
        java.util.List<TrackingBody> bodies=java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        OkHttpClient client=new OkHttpClient.Builder().addInterceptor(chain -> {
            String host=chain.request().url().host();int code=200;String content;
            if(host.equals("secure.assrt.net")){code=503;content="unavailable";}
            else if(host.equals("subhd.me")) content="<div class='clearfix'><a class='align-middle'>無間道</a><div class='view-text'><a href='/a/abc'>粤语版</a></div><div class='f11'><b class='fw-bold'>粤语</b></div></div>";
            else content="{\"data\":[{\"download_url\":\"/sub.srt\",\"release\":\"无间道\",\"language\":{\"name\":\"Chinese\"}}]}";
            TrackingBody body=new TrackingBody(content);bodies.add(body);
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(body).build();
        }).build();
        try {
            Probe<SubtitleGateway.Page> callback=new Probe<>();new HttpSubtitleGateway(client).search(SubtitleSource.ALL,"无间道",1,callback);callback.await();
            assertNull(callback.error);assertEquals(2,callback.value.items.size());
            assertEquals(java.util.Collections.singletonList("ASSRT"),callback.value.unavailable);
            assertEquals(3,bodies.size());for(TrackingBody body:bodies)assertTrue(body.closed);
        } finally { shutdown(client); }
    }

    @Test public void websiteDoesNotFetchTheFirstPageAgainWhenAnotherSourceMovesToPageTwo() throws Exception {
        java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();
        OkHttpClient client=new OkHttpClient.Builder().addInterceptor(chain -> {requests.incrementAndGet();throw new IOException("Must not fetch");}).build();
        try {
            Probe<SubtitleGateway.Page> callback=new Probe<>();new HttpSubtitleGateway(client).search(SubtitleSource.WEBSITE,"Movie",2,callback);callback.await();
            assertEquals(0,requests.get());assertTrue(callback.value.items.isEmpty());assertFalse(callback.value.hasNext);
        } finally { shutdown(client); }
    }

    @Test public void callTimeoutIsReportedEvenThoughOkHttpMarksTheCallCancelled() throws Exception {
        OkHttpClient client=new OkHttpClient.Builder().callTimeout(20,TimeUnit.MILLISECONDS).addInterceptor(chain -> {
            try {Thread.sleep(100);}catch(InterruptedException error){Thread.currentThread().interrupt();}
            throw new IOException("fixture timeout");
        }).build();
        AtomicReference<Call> sent=new AtomicReference<>();
        try {
            Probe<SubtitleGateway.Page> callback=new Probe<>();
            new HttpSubtitleGateway(request -> {Call call=client.newCall(request);sent.set(call);return call;}).search("Movie",1,callback);
            callback.await();assertTrue(sent.get().isCanceled());assertNotNull(callback.error);
        } finally { shutdown(client); }
    }

    @Test public void requestDeadlineCompletesEvenIfUpstreamNeverNotifiesAndExplicitCancelStaysSilent() throws Exception {
        java.util.Queue<Runnable> timers=new java.util.ArrayDeque<>();
        com.fongmi.android.tv.utils.TaskScheduler scheduler=new com.fongmi.android.tv.utils.TaskScheduler(){
            public void post(Runnable task,long delay){timers.add(task);}public void remove(Runnable task){timers.remove(task);}
        };
        java.util.List<Call> calls=new java.util.ArrayList<>();
        Call.Factory silent=request->{Call call=new SilentCall(request);calls.add(call);return call;};
        HttpSubtitleGateway gateway=new HttpSubtitleGateway(silent,scheduler,100);
        Probe<SubtitleGateway.Page> first=new Probe<>();gateway.search("Movie",1,first);
        timers.remove().run();first.await();assertTrue(first.error instanceof java.net.SocketTimeoutException);assertTrue(calls.get(0).isCanceled());
        Probe<SubtitleGateway.Page> cancelled=new Probe<>();SubtitleGateway.Request request=gateway.search("Movie",1,cancelled);
        request.cancel();assertTrue(timers.isEmpty());assertNull(cancelled.error);assertNull(cancelled.value);
    }

    private static final class SilentCall implements Call {
        final Request request;boolean cancelled;SilentCall(Request request){this.request=request;}
        public Request request(){return request;}public Response execute(){throw new UnsupportedOperationException();}
        public void enqueue(okhttp3.Callback callback){} public void cancel(){cancelled=true;}
        public boolean isExecuted(){return true;}public boolean isCanceled(){return cancelled;}
        public okio.Timeout timeout(){return new okio.Timeout();}public Call clone(){return new SilentCall(request);}
        public <T> T tag(Class<? extends T> type){return null;}
        public <T> T tag(kotlin.reflect.KClass<T> type){return null;}
        public <T> T tag(Class<T> type,kotlin.jvm.functions.Function0<? extends T> factory){return factory.invoke();}
        public <T> T tag(kotlin.reflect.KClass<T> type,kotlin.jvm.functions.Function0<? extends T> factory){return factory.invoke();}
    }

    private static OkHttpClient client(int code,TrackingBody body,AtomicReference<Request> sent) {
        return new OkHttpClient.Builder().addInterceptor(chain -> {
            sent.set(chain.request()); return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(code).message("fixture").body(body).build();
        }).build();
    }
    private static void shutdown(OkHttpClient client) {client.dispatcher().executorService().shutdownNow();client.connectionPool().evictAll();}
    private static final class TrackingBody extends ResponseBody {
        volatile boolean closed; final BufferedSource source;
        TrackingBody(String text) {
            source=Okio.buffer(new ForwardingSource(new Buffer().writeUtf8(text)) {
                @Override public void close() throws IOException {closed=true;super.close();}
            });
        }
        public MediaType contentType(){return MediaType.parse("text/plain");}
        public long contentLength(){return -1;}
        public BufferedSource source(){return source;}
    }
    private static final class Probe<T> implements SubtitleGateway.Callback<T> {
        final CountDownLatch latch=new CountDownLatch(1); T value; Throwable error;
        public void success(T value){this.value=value;latch.countDown();}
        public void failure(Throwable error){this.error=error;latch.countDown();}
        void await() throws Exception {assertTrue(latch.await(5,TimeUnit.SECONDS));}
    }
}
