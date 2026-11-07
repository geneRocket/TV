package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.server.Nano;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.IStatus;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class Proxy implements Process {

    private static final String INVALID_RESPONSE = "Invalid proxy response";

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String path) {
        return "/proxy".equals(path);
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String path, Map<String, String> files) {
        try {
            Map<String, String> params = session.getParms();
            params.putAll(session.getHeaders());
            return toResponse(BaseLoader.get().proxyLocal(params));
        } catch (Exception e) {
            return Nano.error(e.getMessage() == null ? INVALID_RESPONSE : e.getMessage());
        }
    }

    static Response toResponse(Object[] result) throws IOException {
        if (result == null || result.length == 0) return Nano.error(INVALID_RESPONSE);
        if (result[0] instanceof Response) return (Response) result[0];
        if (result.length < 3 || (result[2] != null && !(result[2] instanceof InputStream))) return Nano.error(INVALID_RESPONSE);

        int code = result[0] instanceof Integer ? (Integer) result[0] : 0;
        InputStream body = (InputStream) result[2];
        if (code < 100 || code > 599 || (result[1] != null && !(result[1] instanceof String))) {
            if (body != null) body.close();
            return Nano.error(INVALID_RESPONSE);
        }
        Response response = NanoHTTPD.newChunkedResponse(toStatus(code), (String) result[1], body);
        if (result.length > 3 && result[3] instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) result[3]).entrySet()) {
                if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) continue;
                String name = (String) entry.getKey();
                if (name.equalsIgnoreCase("Content-Length") || name.equalsIgnoreCase("Transfer-Encoding")) continue;
                response.addHeader(name, (String) entry.getValue());
            }
        }
        return response;
    }

    private static IStatus toStatus(int code) {
        Status known = Status.lookup(code);
        if (known != null) return known;
        return new IStatus() {
            @Override
            public String getDescription() {
                return code + " Proxy Status";
            }

            @Override
            public int getRequestStatus() {
                return code;
            }
        };
    }

}
