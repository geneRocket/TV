package com.fongmi.android.tv.utils;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.github.catvod.utils.Json;
import com.google.common.net.HttpHeaders;

import java.util.Map;

import jahirfiquitiva.libs.textdrawable.TextDrawable;

public class ImgUtil {

    private static final String TAG_HEADERS = "@Headers=";
    private static final String TAG_COOKIE = "@Cookie=";
    private static final String TAG_REFERER = "@Referer=";
    private static final String TAG_USER_AGENT = "@User-Agent=";

    private static ObjectKey getSignature(String url) {
        return new ObjectKey(url + "_" + Setting.getQuality());
    }

    public static void load(String url, CustomTarget<Bitmap> target) {
        if (!TextUtils.isEmpty(url)) Glide.with(App.get()).asBitmap().load(getUrl(url)).dontAnimate().signature(getSignature(url)).into(target);
    }

    public static void load(String url, int error, CustomTarget<Drawable> target) {
        if (TextUtils.isEmpty(url)) target.onLoadFailed(ResUtil.getDrawable(error));
        else Glide.with(App.get()).asDrawable().load(getUrl(url)).error(error).dontAnimate().signature(getSignature(url)).into(target);
    }

    public static void rect(String text, String url, ImageView view) {
        load(text, url, view, ImageView.ScaleType.CENTER, true);
    }

    public static void oval(String text, String url, ImageView view) {
        load(text, url, view, ImageView.ScaleType.CENTER, false);
    }

    public static void load(String text, String url, ImageView view, ImageView.ScaleType scaleType, boolean rect) {
        view.setScaleType(scaleType);
        Glide.with(view).clear(view);
        if (!TextUtils.isEmpty(url)) Glide.with(view).asBitmap().load(getUrl(url)).placeholder(R.drawable.ic_img_loading).dontAnimate().sizeMultiplier(Setting.getThumbnail()).signature(getSignature(url)).listener(getListener(view, scaleType)).into(view);
        else if (text.length() > 0) view.setImageDrawable(getTextDrawable(text.substring(0, 1), rect));
        else view.setImageResource(R.drawable.ic_img_error);
    }

    public static void loadVod(String text, String url, ImageView view) {
        view.setScaleType(ImageView.ScaleType.CENTER);
        Glide.with(view).clear(view);
        if (!TextUtils.isEmpty(url)) Glide.with(view).asBitmap().load(getUrl(url)).placeholder(R.drawable.ic_img_loading).dontAnimate().sizeMultiplier(Setting.getThumbnail()).signature(getSignature(url)).listener(getListener(view)).into(view);
        else if (text.length() > 0) view.setImageDrawable(getTextDrawable(text.substring(0, 1), true));
        else view.setImageResource(R.drawable.ic_img_error);
    }

    public static void loadLive(String url, ImageView view) {
        view.setVisibility(TextUtils.isEmpty(url) ? View.GONE : View.VISIBLE);
        Glide.with(view).clear(view);
        if (TextUtils.isEmpty(url)) view.setImageResource(R.drawable.ic_img_empty);
        else Glide.with(view).asBitmap().load(getUrl(url)).error(R.drawable.ic_img_empty).dontAnimate().signature(getSignature(url)).into(view);
    }

    public static void clear(ImageView view) {
        clear(view, ImageView.ScaleType.CENTER);
    }

    public static void clear(ImageView view, ImageView.ScaleType scaleType) {
        Glide.with(view).clear(view);
        view.setImageDrawable(null);
        view.setScaleType(scaleType);
    }

    private static Drawable getTextDrawable(String text, boolean rect) {
        TextDrawable.Builder builder = new TextDrawable.Builder().withBorder(ResUtil.dp2px(2), ColorGenerator.get700(text));
        if (rect) return builder.buildRoundRect(text, ColorGenerator.get500(text), ResUtil.dp2px(8));
        return builder.buildRound(text, ColorGenerator.get500(text));
    }

    public static Object getUrl(String url) {
        url = UrlUtil.convert(url);
        if (url.startsWith("data:")) return url;
        LazyHeaders.Builder builder = new LazyHeaders.Builder();
        String headers = getParam(url, TAG_HEADERS);
        String cookie = getParam(url, TAG_COOKIE);
        String referer = getParam(url, TAG_REFERER);
        String userAgent = getParam(url, TAG_USER_AGENT);
        if (!TextUtils.isEmpty(headers)) addHeader(builder, headers);
        if (!TextUtils.isEmpty(cookie)) builder.addHeader(HttpHeaders.COOKIE, cookie);
        if (!TextUtils.isEmpty(referer)) builder.addHeader(HttpHeaders.REFERER, referer);
        if (!TextUtils.isEmpty(userAgent)) builder.addHeader(HttpHeaders.USER_AGENT, userAgent);
        url = stripParam(url);
        return TextUtils.isEmpty(url) ? null : new GlideUrl(url, builder.build());
    }

    private static String getParam(String url, String tag) {
        int start = url.indexOf(tag);
        if (start < 0) return "";
        start += tag.length();
        int end = url.indexOf("@", start);
        return (end < 0 ? url.substring(start) : url.substring(start, end)).trim();
    }

    private static String stripParam(String url) {
        int index = url.indexOf("@");
        return index < 0 ? url : url.substring(0, index);
    }

    private static void addHeader(LazyHeaders.Builder builder, String header) {
        try {
            Map<String, String> map = Json.toMap(Json.parse(header));
            for (Map.Entry<String, String> entry : map.entrySet()) builder.addHeader(UrlUtil.fixHeader(entry.getKey()), entry.getValue());
        } catch (Exception ignored) {
        }
    }

    private static RequestListener<Bitmap> getListener(ImageView view) {
        return getListener(view, ImageView.ScaleType.CENTER);
    }

    private static RequestListener<Bitmap> getListener(ImageView view, ImageView.ScaleType scaleType) {
        return new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Bitmap> target, boolean isFirstResource) {
                view.setImageResource(R.drawable.ic_img_error);
                view.setScaleType(scaleType);
                return true;
            }

            @Override
            public boolean onResourceReady(@NonNull Bitmap resource, @NonNull Object model, Target<Bitmap> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                view.setScaleType(ImageView.ScaleType.CENTER_CROP);
                return false;
            }
        };
    }
}
