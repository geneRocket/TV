package tv.danmaku.ijk.media.player.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SubtitleParserTest {
    @Test public void speakerLabelsAndLiteralAnglesAreNotHtmlTags() {
        assertEquals("&lt;Alice&gt; Hello", SubtitleParser.toHtml("<Alice> Hello"));
        assertEquals("1 &lt; 2 &gt; 0", SubtitleParser.toHtml("1 < 2 > 0"));
        assertEquals("&lt;door slams&gt;", SubtitleParser.toHtml("<door slams>"));
    }

    @Test public void literalLabelsCanMixWithSupportedSubtitleFormatting() {
        assertEquals("<i>&lt;Alice&gt; Hello</i><br><u>world</u>",
                SubtitleParser.toHtml("<i><Alice> Hello</i>\n<u>world</u>"));
        assertEquals("<FONT color=\"red\">&lt;Bob&gt;</FONT>",
                SubtitleParser.toHtml("<FONT color=\"red\"><Bob></FONT>"));
        assertEquals("&lt;iframe&gt;x&lt;/iframe&gt;", SubtitleParser.toHtml("<iframe>x</iframe>"));
        assertEquals("<strong><em>&lt;Alice&gt;</em></strong>", SubtitleParser.toHtml("<strong><em><Alice></em></strong>"));
        assertEquals("<span style='color:red'>Hello</span>", SubtitleParser.toHtml("<span style='color:red'>Hello</span>"));
    }

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
