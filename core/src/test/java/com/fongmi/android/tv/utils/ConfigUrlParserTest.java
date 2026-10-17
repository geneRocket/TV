package com.fongmi.android.tv.utils;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ConfigUrlParserTest {
    @Test public void encryptedConfigurationDescriptorRemainsOneAddress() {
        String descriptor = "https://example.com/config;0123456789abcdef;decryption-key";
        assertEquals(List.of(descriptor), ConfigUrlParser.parse(descriptor));
    }

    @Test public void queryAndPathSeparatorsArePartOfTheAddress() {
        String url = "https://example.com/a,b/config?ids=1,2&token=a|b";
        assertEquals(List.of(url), ConfigUrlParser.parse(url));
    }

    @Test public void storedSelectionRoundTripsCompleteAddressesInOrder() {
        List<String> urls = List.of("https://example.com/config?ids=1,2",
                "file:///storage/config;backup.json", "assets://config,a.json");
        assertEquals(urls, ConfigUrlParser.parse(String.join("\n", urls)));
    }

    @Test public void inlineSeparatorsStillRecognizeTheNextAddress() {
        assertEquals(List.of("https://one/config;checksum;key", "https://two", "file:///config.json"),
                ConfigUrlParser.parse("https://one/config;checksum;key, https://two；file:///config.json"));
    }
}
