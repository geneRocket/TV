package tv.danmaku.ijk.media.player.ui;

import android.text.Html;
import android.text.TextUtils;

import androidx.media3.common.text.Cue;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class SubtitleParser {

    private static final Pattern BRACES_PATTERN = Pattern.compile("\\{([^}]*)\\}");
    private static final String DIALOGUE_LINE_PREFIX = "Dialogue:";

    public static List<Cue> parse(String text) {
        if (TextUtils.isEmpty(text) || text.length() >= 512) return null;
        return Arrays.asList(new Cue.Builder().setText(Html.fromHtml(toHtml(text))).build());
    }

    static String toHtml(String text) {
        if (text.startsWith(DIALOGUE_LINE_PREFIX)) text = parseDialogueLine(text);
        text = text.replace("\\N", "\n").replace("\\n", "\n").replace("\\h", "\u00A0");
        text = text.replaceAll("\\{\\\\.*?\\}", "");
        text = text.replace("\r\n", "\n").replace("\r", "\n");
        if (text.endsWith("\n")) text = text.substring(0, text.lastIndexOf("\n"));
        // Let Html.fromHtml decode entities once and retain supported subtitle styling.
        return text.replace("\n", "<br>");
    }

    private static String parseDialogueLine(String text) {
        String[] lineValues = text.substring(DIALOGUE_LINE_PREFIX.length()).split(",", 10);
        String rawText = lineValues[lineValues.length - 1];
        rawText = BRACES_PATTERN.matcher(rawText).replaceAll("");
        rawText = rawText.replace("\\N", "\n").replace("\\n", "\n").replace("\\h", "\u00A0");
        return rawText;
    }

}
