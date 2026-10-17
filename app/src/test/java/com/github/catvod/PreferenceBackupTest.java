package com.github.catvod;

import com.github.catvod.utils.Prefers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class PreferenceBackupTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void validPreferenceValuesCanBeValidatedWithoutAnAndroidContext() throws Exception {
        assertTrue(valid("{\"name\":\"中文\",\"enabled\":true,\"count\":3,\"size\":1.5,\"time\":2147483648}"));
    }

    @Test public void malformedOrNonObjectPreferenceBackupsAreRejected() throws Exception {
        for (String value : new String[]{"not json", "null", "[1,2]", "{\"broken\":"}) assertFalse(valid(value));
    }

    @Test public void unrepresentableNumbersAreRejectedBeforeEditingPreferences() throws Exception {
        assertFalse(valid("{\"valid\":true,\"overflow\":99999999999999999999999999999999}"));
    }

    private boolean valid(String value) throws Exception {
        File file = temporary.newFile();
        Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
        return Prefers.validateBackup(file);
    }
}
