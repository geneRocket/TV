package tv.danmaku.ijk.media.player.ui;

import android.text.Html;
import android.text.TextUtils;

import androidx.media3.common.text.Cue;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class SubtitleParser {

    private static final Pattern BRACES_PATTERN = Pattern.compile("\\{([^}]*)\\}");
    private static final Pattern XML_NUMBER_ENTITY = Pattern.compile("&#(x?[0-9A-Fa-f]+);");
    private static final String DIALOGUE_LINE_PREFIX = "Dialogue:";

    public static List<Cue> parse(String text) {
        if (TextUtils.isEmpty(text) || text.length() >= 512) return null;
        if (text.startsWith(DIALOGUE_LINE_PREFIX)) text = parseDialogueLine(text);
        text = text.replace("\\N", "\n").replace("\\n", "\n").replace("\\h", "\u00A0");
        text = text.replaceAll("\\{\\\\.*?\\}", "");
        text = decodeXmlString(text);
        text = text.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");
        if (text.endsWith("\n")) text = text.substring(0, text.lastIndexOf("\n"));
        text = Html.escapeHtml(text).replace("\n", "<br>");
        return Arrays.asList(new Cue.Builder().setText(Html.fromHtml(text)).build());
    }

    private static String parseDialogueLine(String text) {
        String[] lineValues = text.substring(DIALOGUE_LINE_PREFIX.length()).split(",", 10);
        String rawText = lineValues[lineValues.length - 1];
        rawText = BRACES_PATTERN.matcher(rawText).replaceAll("");
        rawText = rawText.replace("\\N", "\n").replace("\\n", "\n").replace("\\h", "\u00A0");
        return rawText;
    }

    private static String decodeXmlString(String text) {
        if (TextUtils.isEmpty(text)) return "";
        text = text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'");
        text = decodeNumberEntities(text);
        return text.replace("&amp;", "&");
    }

    private static String decodeNumberEntities(String text) {
        StringBuffer buffer = new StringBuffer();
        java.util.regex.Matcher matcher = XML_NUMBER_ENTITY.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(1);
            try {
                int codePoint = value.startsWith("x") || value.startsWith("X") ? Integer.parseInt(value.substring(1), 16) : Integer.parseInt(value);
                matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
            } catch (Exception ignored) {
                matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
