package tv.danmaku.ijk.media.player.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SubtitleParserTest {
    @Test public void htmlFormattingRemainsMarkupWhileSrtLinesBecomeBreaks() {
        assertEquals("<i>Hello</i><br><b>world</b>", SubtitleParser.toHtml("<i>Hello</i>\n<b>world</b>"));
        assertEquals("<font color='#ff0000'>Red</font>", SubtitleParser.toHtml("<font color='#ff0000'>Red</font>"));
    }

    @Test public void entitiesAreLeftForOneHtmlDecodingPass() {
        String text = "&lt;b&gt;literal&lt;/b&gt; &amp;lt; &#x1F600; &#128512;";
        assertEquals(text, SubtitleParser.toHtml(text));
    }

    @Test public void assDialogueKeepsTextCommasAndEscapedWhitespace() {
        String text = "Dialogue: 0,0:00:00.00,0:00:02.00,Default,,0,0,0,,{\\pos(1,2)}Hello,world\\Nnext\\hword";
        assertEquals("Hello,world<br>next\u00A0word", SubtitleParser.toHtml(text));
    }

    @Test public void windowsAndMacLineEndingsKeepTheirLineBreaks() {
        assertEquals("A<br>B<br>C", SubtitleParser.toHtml("A\r\nB\rC\n"));
    }
}
