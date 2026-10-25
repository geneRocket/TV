package com.github.catvod.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class TransSearchTest {
    @Test public void aliasesConvertEvenWhenTheDisplayLocaleUsesSimplifiedChinese() {
        assertEquals("無間道2",Trans.toTraditional("无间道2"));
        assertEquals("英雄本色 龍虎風雲",Trans.toTraditional("英雄本色 龙虎风云"));
        assertEquals("无间道2",Trans.toSimplified("無間道2"));
        assertEquals("重庆森林",Trans.toSimplified("重慶森林"));
    }
    @Test public void nullAndEnglishTitlesArePreserved() {
        assertEquals("",Trans.toTraditional(null)); assertEquals("",Trans.toSimplified(null));
        assertEquals("Infernal Affairs II",Trans.toTraditional("Infernal Affairs II"));
    }
}
