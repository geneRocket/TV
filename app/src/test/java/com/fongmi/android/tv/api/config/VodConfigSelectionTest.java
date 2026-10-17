package com.fongmi.android.tv.api.config;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.utils.ConfigUrlParser;
import com.github.catvod.Init;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class VodConfigSelectionTest {
    private final Map<String, Object> values = new HashMap<>();
    private Object init, previousContext;
    private Field contextField;
    private TestContext context;

    @Before public void installPreferences() throws Exception {
        Method get = Init.class.getDeclaredMethod("get"); get.setAccessible(true);
        init = get.invoke(null);
        contextField = Init.class.getDeclaredField("context"); contextField.setAccessible(true);
        previousContext = contextField.get(init);
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
        context = (TestContext) unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), TestContext.class);
        SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SharedPreferences.Editor.class}, (proxy, method, args) -> {
                    if (method.getName().equals("putString")) { values.put((String) args[0], args[1]); return proxy; }
                    if (method.getName().equals("apply")) return null;
                    throw new UnsupportedOperationException(method.getName());
                });
        context.preferences = (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                    if (method.getName().equals("edit")) return editor;
                    if (method.getName().equals("getString")) return values.getOrDefault(args[0], args[1]);
                    throw new UnsupportedOperationException(method.getName());
                });
        Init.set(context);
    }

    @After public void restoreContext() throws Exception {
        if (contextField != null) contextField.set(init, previousContext);
    }

    @Test public void startingALoadDoesNotReplaceTheSelectionBeforeSuccess() {
        values.put("vod_config_urls", "https://working.example/config");
        values.put("vod_config_desc", "Working selection");

        new VodConfig.Selection(config("https://broken.example/config", "Unverified selection"));

        assertEquals("https://working.example/config", Setting.getVodConfigUrls());
        assertEquals("Working selection", Setting.getVodConfigDesc());
    }

    @Test public void successfulDepotResolutionCommitsTheActuallyLoadedChild() {
        VodConfig.Selection selection = new VodConfig.Selection(config("https://depot.example/list", "Depot"));
        selection.commit(config("https://child.example/config", "Resolved selection"));

        assertEquals("https://child.example/config", Setting.getVodConfigUrls());
        assertEquals("Resolved selection", Setting.getVodConfigDesc());
    }

    @Test public void emptyConfigCompletionKeepsTheExistingMultiSelection() {
        String urls = "https://old.example/one\nhttps://old.example/two";
        values.put("vod_config_urls", urls);
        values.put("vod_config_desc", "Existing selection");
        VodConfig.Selection selection = new VodConfig.Selection(config("", ""));
        selection.commit(config("https://old.example/one", "Fallback"));

        assertEquals(urls, Setting.getVodConfigUrls());
        assertEquals("Existing selection", Setting.getVodConfigDesc());
    }

    private Config config(String url, String description) {
        Config config = new Config() {
            @Override public String getDesc() { return description; }
        };
        config.setUrl(url);
        return config;
    }

    @Test public void newerManualSelectionSurvivesOlderLoadCompletion() {
        VodConfig.Selection old = new VodConfig.Selection(config("https://older.example/config", "Older"));
        Setting.putVodConfigUrls("https://newer.example/config");
        Setting.putVodConfigDesc("Newer");
        old.commit(config("https://older.example/config", "Older"));
        assertEquals("https://newer.example/config", Setting.getVodConfigUrls());
        assertEquals("Newer", Setting.getVodConfigDesc());
    }

    @Test public void newerPendingLoadWinsEvenIfOlderCompletionArrivesFirst() {
        values.put("vod_config_urls", "https://working.example/config");
        VodConfig.Selection old = new VodConfig.Selection(config("https://older.example/config", "Older"));
        VodConfig.Selection newer = new VodConfig.Selection(config("https://newer.example/config", "Newer"));
        old.commit(config("https://older.example/config", "Older"));
        assertEquals("https://working.example/config", Setting.getVodConfigUrls());
        newer.commit(config("https://newer.example/config", "Newer"));
        assertEquals("https://newer.example/config", Setting.getVodConfigUrls());
    }

    @Test public void returningToOriginalUrlStillInvalidatesOlderCompletion() {
        values.put("vod_config_urls", "https://original.example/config");
        VodConfig.Selection old = new VodConfig.Selection(config("https://older.example/config", "Older"));
        Setting.putVodConfigUrls("https://other.example/config");
        Setting.putVodConfigUrls("https://original.example/config");
        old.commit(config("https://older.example/config", "Older"));
        assertEquals("https://original.example/config", Setting.getVodConfigUrls());
    }

    @Test public void newerDescriptionIntentAlsoInvalidatesOlderCompletion() {
        values.put("vod_config_urls", "https://original.example/config");
        VodConfig.Selection old = new VodConfig.Selection(config("https://older.example/config", "Older"));
        Setting.putVodConfigDesc("Newer selection being prepared");
        old.commit(config("https://older.example/config", "Older"));
        assertEquals("https://original.example/config", Setting.getVodConfigUrls());
        assertEquals("Newer selection being prepared", Setting.getVodConfigDesc());
    }

    @Test public void emptyConfigFallbackKeepsTheExistingEnabledSelection() {
        String urls = "https://old.example/one\nhttps://old.example/two";
        values.put("vod_config_urls", urls);
        values.put("vod_config_desc", "Existing selection");

        Config empty = new Config() {
            @Override public String getDesc() { return ""; }
        };
        new VodConfig.Selection(empty).commit(empty);

        assertEquals(urls, Setting.getVodConfigUrls());
        assertEquals("Existing selection", Setting.getVodConfigDesc());
    }

    @Test public void explicitSingleConfigSwitchUpdatesTheSelectionUsedAfterRestart() {
        values.put("vod_config_urls", "https://old.example/one\nhttps://old.example/two");
        values.put("vod_config_desc", "Old selection");
        values.put("live_config_urls", "https://live.example/config");
        Config incoming = new Config() {
            @Override public String getDesc() { return "Remote selection"; }
        };
        incoming.setUrl("https://new.example/config");

        new VodConfig.Selection(incoming).commit(incoming);

        assertEquals(Collections.singletonList(incoming.getUrl()), ConfigUrlParser.parse(Setting.getVodConfigUrls()));
        assertEquals("Remote selection", Setting.getVodConfigDesc());
        assertEquals("https://live.example/config", Setting.getLiveConfigUrls());
    }

    private static class TestContext extends ContextWrapper {
        SharedPreferences preferences;
        private TestContext() { super(null); }
        @Override public Context getApplicationContext() { return this; }
        @Override public String getPackageName() { return "com.fongmi.android.tv.test"; }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) { return preferences; }
    }
}
