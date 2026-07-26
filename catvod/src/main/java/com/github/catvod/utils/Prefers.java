package com.github.catvod.utils;

import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.github.catvod.Init;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.internal.LazilyParsedNumber;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.Map;

public class Prefers {

    public static SharedPreferences getPrefers() {
        return PreferenceManager.getDefaultSharedPreferences(Init.context());
    }

    public static String getString(String key) {
        return getString(key, "");
    }

    public static String getString(String key, String defaultValue) {
        try {
            return getPrefers().getString(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static int getInt(String key) {
        return getInt(key, 0);
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return getPrefers().getInt(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static float getFloat(String key) {
        return getFloat(key, 0f);
    }

    public static float getFloat(String key, float defaultValue) {
        SharedPreferences preferences = getPrefers();
        try {
            return preferences.getFloat(key, defaultValue);
        } catch (ClassCastException e) {
            Object value = preferences.getAll().get(key);
            if (!(value instanceof Number)) return defaultValue;
            float result = ((Number) value).floatValue();
            preferences.edit().putFloat(key, result).apply();
            return result;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        try {
            return getPrefers().getBoolean(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static void put(String key, Object obj) {
        SharedPreferences.Editor editor = getPrefers().edit();
        if (put(editor, key, obj)) editor.apply();
    }

    private static boolean put(SharedPreferences.Editor editor, String key, Object obj) {
        if (obj == null) return false;
        if (obj instanceof String) {
            editor.putString(key, (String) obj);
        } else if (obj instanceof Boolean) {
            editor.putBoolean(key, (Boolean) obj);
        } else if (obj instanceof Float) {
            editor.putFloat(key, (Float) obj);
        } else if (obj instanceof Integer) {
            editor.putInt(key, (Integer) obj);
        } else if (obj instanceof Long) {
            editor.putLong(key, (Long) obj);
        } else if (obj instanceof LazilyParsedNumber) {
            editor.putInt(key, ((LazilyParsedNumber) obj).intValue());
        } else return false;
        return true;
    }

    public static void remove(String key) {
        getPrefers().edit().remove(key).apply();
    }

    public static void backup(File file) {
        Path.write(file, new Gson().toJson(getPrefers().getAll()).getBytes());
    }

    public static void restore(File file) {
        try {
            Gson gson = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LAZILY_PARSED_NUMBER).create();
            Map<String, Object> map = gson.fromJson(Path.read(file), new TypeToken<Map<String, Object>>() {}.getType());
            SharedPreferences.Editor editor = getPrefers().edit();
            boolean changed = false;
            for (Map.Entry<String, ?> entry : map.entrySet()) changed |= put(editor, entry.getKey(), convert(entry));
            if (changed) editor.apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Object convert(Map.Entry<String, ?> entry) {
        Object value = entry.getValue();
        if (!(value instanceof LazilyParsedNumber)) return value;
        String number = value.toString();
        if (number.contains(".") || number.contains("e") || number.contains("E")) return Float.parseFloat(number);
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException ignored) {
            return Long.parseLong(number);
        }
    }
}
