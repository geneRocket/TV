package com.fongmi.android.tv;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import com.github.catvod.Init;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class SettingReadFallbackTest {
    private final Map<String, Object> values = new HashMap<>();
    private Object init;
    private Field contextField;
    private Object previousContext;
    private TestContext context;

    @Before public void installPreferences() throws Exception {
        Method get = Init.class.getDeclaredMethod("get"); get.setAccessible(true);
        init = get.invoke(null);
        contextField = Init.class.getDeclaredField("context"); contextField.setAccessible(true);
        previousContext = contextField.get(init);
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
        // Skip the android.jar stub constructor; all context calls below are implemented here.
        context = (TestContext) unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), TestContext.class);
        context.preferences = (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getAll")) return new HashMap<>(values);
                    if (method.getName().equals("contains")) return values.containsKey(args[0]);
                    Object value = values.containsKey(args[0]) ? values.get(args[0]) : args[1];
                    switch (method.getName()) {
                        case "getInt": return (Integer) value;
                        case "getBoolean": return (Boolean) value;
                        case "getString": return (String) value;
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
        Init.set(context);
    }

    @After public void restoreContext() throws Exception {
        if (contextField != null) contextField.set(init, previousContext);
    }

    @Test public void incorrectlyTypedPlayerFallsBackWithoutOverwritingTheBackupValue() {
        values.put("player", "1");
        assertEquals(2, Setting.getPlayer());
        assertEquals("1", values.get("player"));
    }

    @Test public void incorrectlyTypedBooleanFallsBack() {
        values.put("exo_tunnel", "true");
        assertFalse(Setting.isTunnel());
    }

    @Test public void incorrectlyTypedStringFallsBack() {
        values.put("doh", 123);
        assertEquals("", Setting.getDoh());
    }

    @Test public void newSettingsAlsoTolerateIncorrectTypes() {
        values.put("loudness", 1.5f);
        assertEquals(Setting.LOUDNESS_DEFAULT, Setting.getLoudness());
    }

    @Test public void correctlyTypedSettingsKeepTheirValuesAndBounds() {
        values.put("player", 1); values.put("exo_tunnel", true); values.put("doh", "https://dns.example/query");
        values.put("render", 999);
        assertEquals(1, Setting.getPlayer()); assertTrue(Setting.isTunnel());
        assertEquals("https://dns.example/query", Setting.getDoh()); assertEquals(1, Setting.getRender());
    }

    @Test public void restoredOutOfRangeOptionsUseDefaultsWithoutRewritingTheBackup() throws Exception {
        String[] keys = {"background", "reset", "language", "backup_mode", "size", "quality", "config_cache"};
        String[] getters = {"getBackground", "getReset", "getLanguage", "getBackupMode", "getSize", "getQuality", "getConfigCache"};
        int[] defaults = {2, 0, com.fongmi.android.tv.utils.LanguageUtil.locale(), 1, 2, 2, 0};
        for (int i = 0; i < keys.length; i++) {
            for (int invalid : new int[]{Integer.MIN_VALUE, -1, 999, Integer.MAX_VALUE}) {
                values.put(keys[i], invalid);
                assertEquals(keys[i], defaults[i], Setting.class.getMethod(getters[i]).invoke(null));
                assertEquals(invalid, values.get(keys[i]));
            }
            values.clear();
        }
    }

    @Test public void everySupportedOptionStillSelectsExactlyTheUsersChoice() throws Exception {
        String[] keys = {"background", "reset", "language", "backup_mode", "size", "quality", "config_cache"};
        String[] getters = {"getBackground", "getReset", "getLanguage", "getBackupMode", "getSize", "getQuality", "getConfigCache"};
        int[] sizes = {3, 2, 3, 2, 4, 3, 3};
        for (int i = 0; i < keys.length; i++) {
            for (int selected = 0; selected < sizes[i]; selected++) {
                values.put(keys[i], selected);
                assertEquals(keys[i], selected, Setting.class.getMethod(getters[i]).invoke(null));
            }
            values.clear();
        }
    }

    private static class TestContext extends ContextWrapper {
        SharedPreferences preferences;
        private TestContext() { super(null); }
        @Override public Context getApplicationContext() { return this; }
        @Override public String getPackageName() { return "com.fongmi.android.tv.test"; }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) { return preferences; }
    }
}
