package com.fongmi.android.tv.player.danmu;

import android.graphics.Color;
import android.text.TextUtils;

import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.bean.Danmu;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import master.flame.danmaku.danmaku.model.AlphaValue;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.Duration;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.SpecialDanmaku;
import master.flame.danmaku.danmaku.model.android.DanmakuFactory;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.util.DanmakuUtils;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;

public class Parser extends BaseDanmakuParser {

    private static final Pattern XML_NUMBER_ENTITY = Pattern.compile("&#(x?[0-9A-Fa-f]+);");
    private static final int MAX_DANMAKU_ITEMS = 6000;
    private static final int MAX_TEXT_LENGTH = 300;

    private final Danmu danmu;
    private BaseDanmaku item;
    private float scaleX;
    private float scaleY;
    private int index;

    public Parser(String path) {
        this.danmu = Danmu.from(getContent(path));
    }

    private String getContent(String path) {
        if (TextUtils.isEmpty(path)) return "";
        path = UrlUtil.convert(path);
        if (path.startsWith("file")) return Path.read(path);
        if (path.startsWith("http") || path.startsWith("//")) return request(path);
        if (new File(path).exists()) return Path.read(path);
        return path;
    }

    private String request(String path) {
        String url = UrlUtil.stripTag(UrlUtil.normalize(path, ""));
        Map<String, String> headers = UrlUtil.getTagHeaders(path);
        try (Response response = OkHttp.client(Constant.TIMEOUT_PLAY).newCall(new Request.Builder().url(url).headers(Headers.of(headers)).build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) return "";
            return response.body().string();
        } catch (IOException e) {
            return "";
        }
    }

    @Override
    protected Danmakus parse() {
        Danmakus result = new Danmakus(IDanmakus.ST_BY_TIME);
        index = 0;
        item = null;
        for (Danmu.Data data : danmu.getData()) {
            if (index >= MAX_DANMAKU_ITEMS) break;
            String[] values = data.getParam().split(",");
            if (values.length < 4) continue;
            if (!setParam(values)) continue;
            setText(data.getText());
            if (item == null) continue;
            synchronized (result.obtainSynchronizer()) {
                result.addItem(item);
            }
        }
        return result;
    }

    @Override
    public BaseDanmakuParser setDisplayer(IDisplayer display) {
        super.setDisplayer(display);
        scaleX = mDispWidth / DanmakuFactory.BILI_PLAYER_WIDTH;
        scaleY = mDispHeight / DanmakuFactory.BILI_PLAYER_HEIGHT;
        return this;
    }

    private boolean setParam(String[] values) {
        try {
            int type = Integer.parseInt(values[1]);
            long time = (long) (Float.parseFloat(values[0]) * 1000);
            if (time < 0) return false;
            float size = Float.parseFloat(values[2]) * (mDispDensity - 0.6f);
            int color = (int) ((0x00000000ff000000L | Long.parseLong(values[3])) & 0x00000000ffffffffL);
            item = mContext.mDanmakuFactory.createDanmaku(type, mContext);
            if (item == null) return false;
            item.setTime(time);
            item.setTimer(mTimer);
            item.textSize = size;
            item.textColor = color;
            item.textShadowColor = color <= Color.BLACK ? Color.WHITE : Color.BLACK;
            item.flags = mContext.mGlobalFlagValues;
            return true;
        } catch (Exception e) {
            item = null;
            return false;
        }
    }

    private void setText(String text) {
        text = decodeXmlString(text);
        if (TextUtils.isEmpty(text)) {
            item = null;
            return;
        }
        item.index = index++;
        DanmakuUtils.fillText(item, text);
        if (item.getType() == BaseDanmaku.TYPE_SPECIAL && text.startsWith("[") && text.endsWith("]")) setSpecial();
    }

    private void setSpecial() {
        try {
            String[] textArr = null;
            JSONArray jsonArray = new JSONArray(item.text);
            textArr = new String[jsonArray.length()];
            for (int i = 0; i < textArr.length; i++) {
                textArr[i] = jsonArray.getString(i);
            }
            if (textArr == null || textArr.length < 5 || TextUtils.isEmpty(textArr[4])) {
                item = null;
                return;
            }
            DanmakuUtils.fillText(item, textArr[4]);
            float beginX = Float.parseFloat(textArr[0]);
            float beginY = Float.parseFloat(textArr[1]);
            float endX = beginX;
            float endY = beginY;
            String[] alphaArr = textArr[2].split("-");
            int beginAlpha = (int) (AlphaValue.MAX * Float.parseFloat(alphaArr[0]));
            int endAlpha = beginAlpha;
            if (alphaArr.length > 1) {
                endAlpha = (int) (AlphaValue.MAX * Float.parseFloat(alphaArr[1]));
            }
            long alphaDuraion = (long) (Float.parseFloat(textArr[3]) * 1000);
            long translationDuration = alphaDuraion;
            long translationStartDelay = 0;
            float rotateY = 0, rotateZ = 0;
            if (textArr.length >= 7) {
                rotateZ = Float.parseFloat(textArr[5]);
                rotateY = Float.parseFloat(textArr[6]);
            }
            if (textArr.length >= 11) {
                endX = Float.parseFloat(textArr[7]);
                endY = Float.parseFloat(textArr[8]);
                if (!"".equals(textArr[9])) {
                    translationDuration = Integer.parseInt(textArr[9]);
                }
                if (!"".equals(textArr[10])) {
                    translationStartDelay = (long) (Float.parseFloat(textArr[10]));
                }
            }
            if (isPercentageNumber(textArr[0])) {
                beginX *= DanmakuFactory.BILI_PLAYER_WIDTH;
            }
            if (isPercentageNumber(textArr[1])) {
                beginY *= DanmakuFactory.BILI_PLAYER_HEIGHT;
            }
            if (textArr.length >= 8 && isPercentageNumber(textArr[7])) {
                endX *= DanmakuFactory.BILI_PLAYER_WIDTH;
            }
            if (textArr.length >= 9 && isPercentageNumber(textArr[8])) {
                endY *= DanmakuFactory.BILI_PLAYER_HEIGHT;
            }
            item.duration = new Duration(alphaDuraion);
            item.rotationZ = rotateZ;
            item.rotationY = rotateY;
            mContext.mDanmakuFactory.fillTranslationData(item, beginX, beginY, endX, endY, translationDuration, translationStartDelay, scaleX, scaleY);
            mContext.mDanmakuFactory.fillAlphaData(item, beginAlpha, endAlpha, alphaDuraion);
            if (textArr.length >= 12 && !TextUtils.isEmpty(textArr[11]) && "true".equalsIgnoreCase(textArr[11])) {
                item.textShadowColor = Color.TRANSPARENT;
            }
            if (textArr.length >= 14) {
                ((SpecialDanmaku) item).isQuadraticEaseOut = ("0".equals(textArr[13]));
            }
            if (textArr.length >= 15 && !TextUtils.isEmpty(textArr[14])) {
                String motionPathString = textArr[14].length() > 1 ? textArr[14].substring(1) : "";
                if (!TextUtils.isEmpty(motionPathString)) {
                    String[] pointStrArray = motionPathString.split("L");
                    if (pointStrArray.length > 0) {
                        float[][] points = new float[pointStrArray.length][2];
                        for (int i = 0; i < pointStrArray.length; i++) {
                            String[] pointArray = pointStrArray[i].split(",");
                            if (pointArray.length >= 2) {
                                points[i][0] = Float.parseFloat(pointArray[0]);
                                points[i][1] = Float.parseFloat(pointArray[1]);
                            }
                        }
                        DanmakuFactory.fillLinePathData(item, points, scaleX, scaleY);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
            item = null;
        } catch (Exception e) {
            item = null;
        }
    }

    private boolean isPercentageNumber(String number) {
        return number != null && number.contains(".");
    }

    private String decodeXmlString(String title) {
        if (TextUtils.isEmpty(title)) return "";
        if (title.length() > MAX_TEXT_LENGTH) title = title.substring(0, MAX_TEXT_LENGTH);
        if (title.contains("&amp;")) title = title.replace("&amp;", "&");
        if (title.contains("&quot;")) title = title.replace("&quot;", "\"");
        if (title.contains("&gt;")) title = title.replace("&gt;", ">");
        if (title.contains("&lt;")) title = title.replace("&lt;", "<");
        if (title.contains("&apos;")) title = title.replace("&apos;", "'");
        Matcher matcher = XML_NUMBER_ENTITY.matcher(title);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(1);
            try {
                int codePoint = value.startsWith("x") || value.startsWith("X")
                        ? Integer.parseInt(value.substring(1), 16)
                        : Integer.parseInt(value);
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
            } catch (Exception e) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
