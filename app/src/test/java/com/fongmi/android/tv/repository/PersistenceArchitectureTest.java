package com.fongmi.android.tv.repository;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class PersistenceArchitectureTest {
    @Test public void roomEntitiesContainOnlyTheExistingSerializedDataFields() {
        assertFields(History.class, Set.of("key", "vodPic", "vodName", "vodFlag", "vodRemarks", "episodeUrl",
                "revSort", "revPlay", "createTime", "opening", "ending", "position", "duration", "speed", "player", "scale", "cid"));
        assertFields(Keep.class, Set.of("key", "siteName", "vodName", "vodPic", "createTime", "type", "cid"));
    }
    @Test public void historyCopiesKeepTheSameSerializedFormatAndDetachMutableProgress() {
        History original = new History(); original.setKey("site@@@opaque@@@id@@@7"); original.setCid(7);
        original.setPosition(480_000); original.setDuration(900_000); original.setSpeed(2); original.setRevPlay(true);
        History copy = original.copy();
        Gson gson = new Gson();
        assertEquals(gson.toJsonTree(original), gson.toJsonTree(copy));
        original.setPosition(0);
        assertEquals(480_000, copy.getPosition());
    }
    @Test public void favoriteCopiesDoNotResolveMissingSiteMetadataThroughGlobalConfiguration() {
        Keep original = new Keep(); original.setKey("site@@@id@@@7"); original.setCid(7);
        original.setVodName("saved");
        Keep copy = original.copy();
        Gson gson = new Gson();
        assertEquals(gson.toJsonTree(original), gson.toJsonTree(copy));
        original.setVodName("draft");
        assertEquals("saved", copy.getVodName());
    }
    private static void assertFields(Class<?> entity, Set<String> expected) {
        Set<String> actual = new HashSet<>();
        for (Field field : entity.getDeclaredFields()) {
            if (field.isSynthetic()) continue;
            assertFalse("Runtime state must live in the store: " + field, Modifier.isStatic(field.getModifiers()));
            SerializedName name = field.getAnnotation(SerializedName.class);
            assertNotNull(field.toString(), name);
            actual.add(name.value());
        }
        assertEquals(expected, actual);
    }
}
