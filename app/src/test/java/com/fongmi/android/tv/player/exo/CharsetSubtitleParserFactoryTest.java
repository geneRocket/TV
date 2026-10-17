package com.fongmi.android.tv.player.exo;

import org.junit.Test;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class CharsetSubtitleParserFactoryTest {
    private static final String TEXT = "今天我们一起观看精彩的电影，了解世界各地的文化历史，感受生活中的美好。这个故事讲述朋友之间的相互帮助，以及面对困难时坚持不懈的勇气。中文简体字幕应该完整显示，没有乱码。";

    @Test public void chineseLegacySubtitleIsConvertedWithoutMojibake() {
        byte[] data = TEXT.getBytes(Charset.forName("GB18030"));
        assertEquals(TEXT, new String(CharsetSubtitleParserFactory.convertToUtf8(data, 0, data.length), StandardCharsets.UTF_8));
    }

    @Test public void utf16SubtitleBomRemainsReadableAfterConversion() {
        byte[] data = TEXT.getBytes(StandardCharsets.UTF_16);
        String converted = new String(CharsetSubtitleParserFactory.convertToUtf8(data, 0, data.length), StandardCharsets.UTF_8);
        // A Unicode BOM is valid in UTF-8 too; subtitle readers consume it before parsing.
        if (converted.startsWith("\uFEFF")) converted = converted.substring(1);
        assertEquals(TEXT, converted);
    }

    @Test public void utf8SampleUsesOnlyTheRequestedRange() {
        byte[] data = ("prefix" + TEXT + "suffix").getBytes(StandardCharsets.UTF_8);
        assertEquals(TEXT, new String(CharsetSubtitleParserFactory.convertToUtf8(data, 6, TEXT.getBytes(StandardCharsets.UTF_8).length), StandardCharsets.UTF_8));
    }
}
