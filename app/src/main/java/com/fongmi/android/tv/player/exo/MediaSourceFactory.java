package com.fongmi.android.tv.player.exo;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.TsExtractor;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.interceptor.AuthInterceptor;

import java.util.HashMap;
import java.util.Map;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class MediaSourceFactory implements MediaSource.Factory {

    private final DefaultMediaSourceFactory defaultMediaSourceFactory;
    private final SubtitleParser.Factory subtitleParserFactory = new CharsetSubtitleParserFactory();
    private ExtractorsFactory extractorsFactory;
    private DrmSessionManagerProvider drmSessionManagerProvider;
    private LoadErrorHandlingPolicy loadErrorHandlingPolicy;

    public MediaSourceFactory() {
        defaultMediaSourceFactory = new DefaultMediaSourceFactory(new DefaultDataSource.Factory(App.get(), new MyOkhttpDataSource.Factory(OkHttp.client())), getExtractorsFactory(), subtitleParserFactory);
    }

    @NonNull
    @Override
    public MediaSource.Factory setDrmSessionManagerProvider(@NonNull DrmSessionManagerProvider drmSessionManagerProvider) {
        this.drmSessionManagerProvider = drmSessionManagerProvider;
        return this;
    }

    @NonNull
    @Override
    public MediaSource.Factory setLoadErrorHandlingPolicy(@NonNull LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
        this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
        return this;
    }

    @NonNull
    @Override
    public @C.ContentType int[] getSupportedTypes() {
        return defaultMediaSourceFactory.getSupportedTypes();
    }

    @NonNull
    @Override
    public MediaSource createMediaSource(@NonNull MediaItem mediaItem) {
        Map<String, String> headers = getHeaders(mediaItem);
        if (mediaItem.mediaId.contains("***") && mediaItem.mediaId.contains("|||")) {
            return createConcatenatingMediaSource(mediaItem, headers);
        }
        return buildMediaSourceFactory(headers, authenticationOrigin(mediaItem)).createMediaSource(mediaItem);
    }

    private Map<String, String> getHeaders(MediaItem mediaItem) {
        Map<String, String> headers = new HashMap<>();
        if (mediaItem.requestMetadata == null || mediaItem.requestMetadata.extras == null || mediaItem.requestMetadata.extras.isEmpty()) return headers;
        for (String key : mediaItem.requestMetadata.extras.keySet()) {
            if ("__force_live".equals(key)) continue;
            Object value = mediaItem.requestMetadata.extras.get(key);
            if (value != null) headers.put(key, value.toString());
        }
        return headers;
    }

    private MediaSource createConcatenatingMediaSource(MediaItem mediaItem, Map<String, String> headers) {
        ConcatenatingMediaSource2.Builder builder = new ConcatenatingMediaSource2.Builder();
        MediaSource.Factory unscoped = buildMediaSourceFactory(headers, null);
        for (String split : mediaItem.mediaId.split("\\*\\*\\*")) {
            String[] info = split.split("\\|\\|\\|");
            if (info.length >= 2) {
                try {
                    MediaItem child = mediaItem.buildUpon().setUri(Uri.parse(info[0])).build();
                    HttpUrl origin = authenticationOrigin(child);
                    MediaSource.Factory factory = origin == null ? unscoped : buildMediaSourceFactory(headers, origin);
                    builder.add(factory.createMediaSource(child), Long.parseLong(info[1]));
                } catch (Exception ignored) {
                }
            }
        }
        return builder.build();
    }

    private ExtractorsFactory getExtractorsFactory() {
        if (extractorsFactory == null) extractorsFactory = new DefaultExtractorsFactory().setSubtitleParserFactory(subtitleParserFactory).setConstantBitrateSeekingEnabled(true).setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS).setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 3);
        return extractorsFactory;
    }

    private HttpUrl authenticationOrigin(MediaItem item) {
        HttpUrl url = item.localConfiguration == null ? null : HttpUrl.parse(item.localConfiguration.uri.toString());
        return url != null && AuthInterceptor.hasCredentials(url) ? url : null;
    }

    private MediaSource.Factory buildMediaSourceFactory(Map<String, String> headers, HttpUrl mediaUrl) {
        OkHttpClient client = OkHttp.client();
        HttpDataSource.Factory httpFactory = new MyOkhttpDataSource.Factory(
                mediaUrl == null ? client : request -> client.newCall(AuthInterceptor.apply(request, mediaUrl)));
        if (!headers.isEmpty()) httpFactory.setDefaultRequestProperties(headers);
        DataSource.Factory upstreamFactory = new DefaultDataSource.Factory(App.get(), httpFactory);
        MediaSource.Factory factory = new DefaultMediaSourceFactory(upstreamFactory, getExtractorsFactory(), subtitleParserFactory);
        if (drmSessionManagerProvider != null) factory.setDrmSessionManagerProvider(drmSessionManagerProvider);
        if (loadErrorHandlingPolicy != null) factory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
        return factory;
    }
}
