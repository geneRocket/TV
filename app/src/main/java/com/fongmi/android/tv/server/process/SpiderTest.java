package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.repository.SpiderRepository;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.utils.ThreadPools;
import com.github.catvod.net.HttpCallScope;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

/** On-device diagnostics for an already configured source. */
public final class SpiderTest implements Process {

    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), ThreadPools.newThreadFactory("spider-test"));

    @Override
    public boolean isRequest(IHTTPSession session, String path) {
        return "/spider-test".equals(path);
    }

    @Override
    public Response doResponse(IHTTPSession session, String path, Map<String, String> files) {
        String host = session.getHeaders().get("host");
        if (session.getMethod() != NanoHTTPD.Method.POST || !"127.0.0.1".equals(session.getRemoteIpAddress())
                || host == null || !host.matches("127\\.0\\.0\\.1(?::[0-9]{1,5})?")
                || session.getHeaders().containsKey("origin")
                || !"1".equals(session.getHeaders().get("x-tv-spider-test"))) {
            return Nano.error(Status.FORBIDDEN, "Local diagnostic POST required");
        }
        Map<String, String> params = session.getParms();
        String operation = param(params, "op");
        String key = param(params, "site");
        if (key.isEmpty() || !validOperation(operation, params)) {
            return Nano.error(Status.BAD_REQUEST, "Expected site and op=home|category|detail|search|player with operation arguments");
        }
        Future<Result> task = null;
        try (HttpCallScope calls = new HttpCallScope()) {
            task = worker.submit(() -> calls.call(() -> {
                Site site = VodConfig.get().getSite(key);
                return site.isEmpty() ? null : run(site, operation, params);
            }));
            Result result = task.get(Constant.TIMEOUT_PLAY, TimeUnit.MILLISECONDS);
            if (result == null) return Nano.error(Status.NOT_FOUND, "Configured site not found");
            Status status = result.isRequestFailed() || result.hasErrorMsg() ? Status.INTERNAL_ERROR : Status.OK;
            Response response = NanoHTTPD.newFixedLengthResponse(status, "application/json; charset=utf-8", result.toString());
            response.addHeader("Cache-Control", "no-store");
            return response;
        } catch (RejectedExecutionException e) {
            return Nano.error(Status.SERVICE_UNAVAILABLE, "Another diagnostic is running");
        } catch (TimeoutException e) {
            return Nano.error(Status.REQUEST_TIMEOUT, operation + " timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Nano.error(Status.SERVICE_UNAVAILABLE, operation + " interrupted");
        } catch (ExecutionException e) {
            return Nano.error(Status.INTERNAL_ERROR, operation + " failed: " + e.getCause().getClass().getSimpleName());
        } finally {
            if (task != null && !task.isDone()) task.cancel(true);
        }
    }

    private static boolean validOperation(String operation, Map<String, String> params) {
        switch (operation) {
            case "home": return true;
            case "category": return !param(params, "tid").isEmpty();
            case "search": return !param(params, "keyword").isEmpty();
            case "detail":
            case "player": return !param(params, "id").isEmpty();
            default: return false;
        }
    }

    private static Result run(Site site, String operation, Map<String, String> params) throws Exception {
        SpiderRepository repository = SpiderRepository.get();
        String key = site.getKey();
        String page = params.getOrDefault("page", "1");
        switch (operation) {
            case "home": return repository.homeContent(key);
            case "category": return repository.categoryContent(key, param(params, "tid"), page, true, new HashMap<>());
            case "detail": return repository.detailContent(key, param(params, "id"));
            case "search": return repository.searchContent(site, param(params, "keyword"), false, page);
            case "player": return repository.playerContent(key, param(params, "flag"), param(params, "id"));
            default: throw new IllegalArgumentException(operation);
        }
    }

    private static String param(Map<String, String> params, String key) {
        return params.getOrDefault(key, "");
    }
}
