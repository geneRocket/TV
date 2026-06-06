package com.fongmi.android.tv.player.exo;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.CacheDataSink;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.TsExtractor;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;

import java.util.HashMap;
import java.util.Map;

public class MediaSourceFactory implements MediaSource.Factory {

    private final DefaultMediaSourceFactory defaultMediaSourceFactory;
    private ExtractorsFactory extractorsFactory;
    private DrmSessionManagerProvider drmSessionManagerProvider;
    private LoadErrorHandlingPolicy loadErrorHandlingPolicy;

    public MediaSourceFactory() {
        defaultMediaSourceFactory = new DefaultMediaSourceFactory(buildCacheDataSource(new DefaultDataSource.Factory(App.get(), new MyOkhttpDataSource.Factory(OkHttp.client()))), getExtractorsFactory());
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
        boolean forceLive = ExoUtil.isForceLive(mediaItem);
        MediaSource.Factory factory = buildMediaSourceFactory(getHeaders(mediaItem), forceLive);
        if (drmSessionManagerProvider != null) factory.setDrmSessionManagerProvider(drmSessionManagerProvider);
        if (loadErrorHandlingPolicy != null) factory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
        if (mediaItem.mediaId.contains("***") && mediaItem.mediaId.contains("|||")) {
            return createConcatenatingMediaSource(factory, mediaItem);
        } else {
            return factory.createMediaSource(mediaItem);
        }
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

    private MediaSource createConcatenatingMediaSource(MediaSource.Factory factory, MediaItem mediaItem) {
        ConcatenatingMediaSource2.Builder builder = new ConcatenatingMediaSource2.Builder();
        for (String split : mediaItem.mediaId.split("\\*\\*\\*")) {
            String[] info = split.split("\\|\\|\\|");
            if (info.length >= 2) {
                try {
                    builder.add(factory.createMediaSource(mediaItem.buildUpon().setUri(Uri.parse(info[0])).build()), Long.parseLong(info[1]));
                } catch (Exception ignored) {
                }
            }
        }
        return builder.build();
    }

    private ExtractorsFactory getExtractorsFactory() {
        if (extractorsFactory == null) extractorsFactory = new DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true).setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS).setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 3);
        return extractorsFactory;
    }

    private MediaSource.Factory buildMediaSourceFactory(Map<String, String> headers, boolean forceLive) {
        HttpDataSource.Factory httpFactory = new MyOkhttpDataSource.Factory(OkHttp.client());
        if (headers != null && !headers.isEmpty()) httpFactory.setDefaultRequestProperties(headers);
        DataSource.Factory upstreamFactory = new DefaultDataSource.Factory(App.get(), httpFactory);
        if (forceLive) return new DefaultMediaSourceFactory(upstreamFactory, getExtractorsFactory());
        return new DefaultMediaSourceFactory(buildCacheDataSource(upstreamFactory), getExtractorsFactory());
    }

    private CacheDataSource.Factory buildCacheDataSource(DataSource.Factory upstreamFactory) {
        return new CacheDataSource.Factory()
                .setCache(CacheManager.get().getCache())
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheWriteDataSinkFactory(new CacheDataSink.Factory().setCache(CacheManager.get().getCache()))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }
}
