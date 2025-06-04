package com.fongmi.android.tv.ui.custom;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.net.OkHttp;
import com.tencent.smtt.export.external.interfaces.SslError;
import com.tencent.smtt.export.external.interfaces.SslErrorHandler;
import com.tencent.smtt.export.external.interfaces.WebResourceRequest;
import com.tencent.smtt.export.external.interfaces.WebResourceResponse;
import com.tencent.smtt.sdk.CookieManager;
import com.tencent.smtt.sdk.QbSdk;
import com.tencent.smtt.sdk.WebChromeClient;
import com.tencent.smtt.sdk.WebView;
import com.tencent.smtt.sdk.WebViewClient;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.ui.dialog.WebDialog;
import com.fongmi.android.tv.utils.Sniffer;
import com.github.catvod.crawler.Spider;
import com.google.common.net.HttpHeaders;
import com.orhanobut.logger.Logger;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class CustomWebView extends WebView implements DialogInterface.OnDismissListener {

    private static final String TAG = CustomWebView.class.getSimpleName();
    private static final String BLANK = "about:blank";

    private WebResourceResponse empty;
    private ParseCallback callback;
    private WebDialog dialog;
    private Runnable timer;
    private boolean detect;
    private String click;
    private String from;
    private String key;

    public static CustomWebView create(@NonNull Context context) {
        initTbs();
        return new CustomWebView(context);
    }

    public CustomWebView(@NonNull Context context) {
        super(context);
        initSettings();
        showTbs();
    }

    private static void initTbs() {
        if (Setting.getParseWebView() == 0) QbSdk.forceSysWebView();
        else QbSdk.unForceSysWebView();
    }

    private void showTbs() {
        if (this.getIsX5Core())  Notify.show(R.string.x5webview_parsing);
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void initSettings() {
        this.timer = () -> stop(true);
        this.empty = new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
        getSettings().setSupportZoom(true);
        getSettings().setUseWideViewPort(true);
        getSettings().setDatabaseEnabled(true);
        getSettings().setBlockNetworkImage(true);
        getSettings().setDomStorageEnabled(true);
        getSettings().setJavaScriptEnabled(true);
        getSettings().setBuiltInZoomControls(true);
        getSettings().setDisplayZoomControls(false);
        getSettings().setLoadWithOverviewMode(true);
        getSettings().setUserAgentString(Setting.getUa());
        getSettings().setMediaPlaybackRequiresUserGesture(false);
        getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        setWebViewClient(webViewClient());
        setWebChromeClient(webChromeClient());
    }

    public CustomWebView start(String key, String from, Map<String, String> headers, String url, String click, ParseCallback callback, boolean detect) {
        App.post(timer, Constant.TIMEOUT_PARSE_WEB);
        this.callback = callback;
        this.detect = detect;
        this.click = click;
        this.from = from;
        this.key = key;
        start(url, headers);
        return this;
    }

    private void start(String url, Map<String, String> headers) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);
        checkHeader(url, headers);
        loadUrl(url, headers);
    }

    private void checkHeader(String url, Map<String, String> headers) {
        for (String key : headers.keySet()) {
            if (HttpHeaders.USER_AGENT.equalsIgnoreCase(key)) getSettings().setUserAgentString(headers.get(key));
        }
    }

    private WebViewClient webViewClient() {
        return new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String host = request.getUrl().getHost();
                if (TextUtils.isEmpty(host) || isAd(host)) return empty;
                Map<String, String> headers = request.getRequestHeaders();
                if (url.contains("challenges.cloudflare.com/turnstile")) App.post(() -> showDialog());
                if (detect && url.contains("player/?url=")) onParseAdd(headers, url);
                else if (isVideoFormat(url)) onParseSuccess(headers, url);
                return shouldInterceptRequestByProxy(view, request);
            }

            public WebResourceResponse shouldInterceptRequestByProxy(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                try {
                    if (url.matches(".*\\.(css|woff|woff2|ttf|otf|eot|svg|mp4|webm|avi|gif)$")) {
                        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
                    }
                    Request.Builder builder = new Request.Builder().url(url);

                    // 设置请求头
                    for (Map.Entry<String, String> header : request.getRequestHeaders().entrySet()) {
                        builder.addHeader(header.getKey(), header.getValue());
                    }

                    Response response = OkHttp.client().newCall(builder.build()).execute();

                    if (!response.isSuccessful()) {
                        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
                    }

                    // 获取Content-Type 和 encoding
                    String contentType = response.header("Content-Type", "text/plain");
                    String mimeType = getMimeTypeFromContentType(contentType);
                    String encoding = getEncodingFromContentType(contentType);

                    InputStream inputStream = body.byteStream();

                    return new WebResourceResponse(mimeType, encoding, inputStream);
                } catch (Exception e) {
                    Logger.t(TAG).e(e, "OkHttp proxy request failed for url: %s", url);
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
                }
            }

            private String getMimeTypeFromContentType(String contentType) {
                if (contentType == null) return "text/plain";
                String[] parts = contentType.split(";");
                return parts[0].trim();
            }

            private String getEncodingFromContentType(String contentType) {
                if (contentType == null) return "utf-8";
                String[] parts = contentType.split(";");
                for (String part : parts) {
                    part = part.trim().toLowerCase();
                    if (part.startsWith("charset=")) {
                        return part.substring(8);
                    }
                }
                return "utf-8";
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url.equals(BLANK)) return;
                evaluate(getScript(url));
            }

            @Override
            @SuppressLint("WebViewClientOnReceivedSslError")
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        };
    }

    private WebChromeClient webChromeClient() {
        return new WebChromeClient() {
            @Override
            public Bitmap getDefaultVideoPoster() {
                try {
                    return BitmapFactory.decodeResource(App.get().getResources(), R.drawable.ic_logo);
                } catch (Throwable e) {
                    return super.getDefaultVideoPoster();
                }
            }
        };
    }

    private void showDialog() {
        if (dialog != null || App.activity() == null) return;
        if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
        dialog = new WebDialog(this).show();
        App.removeCallbacks(timer);
    }

    private void hideDialog() {
        if (dialog != null) dialog.dismiss();
        dialog = null;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        stop(true);
    }

    private List<String> getScript(String url) {
        List<String> script = new ArrayList<>(Sniffer.getScript(Uri.parse(url)));
        if (TextUtils.isEmpty(click) || script.contains(click)) return script;
        script.add(0, click);
        return script;
    }

    private void evaluate(List<String> script) {
        if (script.isEmpty()) return;
        if (TextUtils.isEmpty(script.get(0))) {
            evaluate(script.subList(1, script.size()));
        } else {
            evaluateJavascript(script.get(0), value -> evaluate(script.subList(1, script.size())));
        }
    }

    private boolean isAd(String host) {
        for (String ad : VodConfig.get().getAds()) if (host.contains(ad)) return true;
        for (String ad : LiveConfig.get().getAds()) if (host.contains(ad)) return true;
        for (String ad : VodConfig.get().getAds()) if (Pattern.compile(ad).matcher(host).find()) return true;
        for (String ad : LiveConfig.get().getAds()) if (Pattern.compile(ad).matcher(host).find()) return true;
        return false;
    }

    private boolean isVideoFormat(String url) {
        try {
            Logger.t(TAG).d(url);
            Spider spider = VodConfig.get().getSite(key).spider();
            if (spider.manualVideoCheck()) return spider.isVideoFormat(url);
            return Sniffer.isVideoFormat(url);
        } catch (Exception ignored) {
            return Sniffer.isVideoFormat(url);
        }
    }

    private void onParseAdd(Map<String, String> headers, String url) {
        App.post(() -> CustomWebView.create(App.get()).start(key, from, headers, url, click, callback, false));
    }

    private void onParseSuccess(Map<String, String> headers, String url) {
        if (callback != null) callback.onParseSuccess(headers, url, from);
        App.post(() -> stop(false));
        callback = null;
    }

    private void onParseError() {
        if (callback != null) callback.onParseError();
        callback = null;
    }

    public void stop(boolean error) {
        hideDialog();
        stopLoading();
        loadUrl(BLANK);
        App.removeCallbacks(timer);
        if (error) onParseError();
        else callback = null;
    }
}
