package com.fongmi.android.tv.player.exo;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;

import org.mozilla.universalchardet.UniversalDetector;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@UnstableApi
public final class CharsetSubtitleParserFactory implements SubtitleParser.Factory {

    private final DefaultSubtitleParserFactory delegate;

    public CharsetSubtitleParserFactory() {
        this.delegate = new DefaultSubtitleParserFactory();
    }

    @Override
    public boolean supportsFormat(Format format) {
        return delegate.supportsFormat(format);
    }

    @Override
    public int getCueReplacementBehavior(Format format) {
        return delegate.getCueReplacementBehavior(format);
    }

    @Override
    public SubtitleParser create(Format format) {
        SubtitleParser parser = delegate.create(convertInitializationData(format));
        return isTextSubtitle(format) ? new CharsetSubtitleParser(parser) : parser;
    }

    private Format convertInitializationData(Format format) {
        if (!Objects.equals(format.sampleMimeType, MimeTypes.TEXT_SSA) || format.initializationData.isEmpty()) return format;
        List<byte[]> data = new ArrayList<>(format.initializationData.size());
        for (byte[] item : format.initializationData) data.add(convertToUtf8(item, 0, item.length));
        return format.buildUpon().setInitializationData(data).build();
    }

    private static boolean isTextSubtitle(Format format) {
        return Objects.equals(format.sampleMimeType, MimeTypes.APPLICATION_SUBRIP) || Objects.equals(format.sampleMimeType, MimeTypes.TEXT_SSA) || Objects.equals(format.sampleMimeType, MimeTypes.TEXT_VTT);
    }

    private static byte[] convertToUtf8(byte[] data, int offset, int length) {
        Charset charset = detectCharset(data, offset, length);
        if (charset == null || StandardCharsets.UTF_8.equals(charset)) {
            byte[] copy = new byte[length];
            System.arraycopy(data, offset, copy, 0, length);
            return copy;
        }
        return new String(data, offset, length, charset).getBytes(StandardCharsets.UTF_8);
    }

    @Nullable
    private static Charset detectCharset(byte[] data, int offset, int length) {
        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData(data, offset, length);
        detector.dataEnd();
        String name = detector.getDetectedCharset();
        detector.reset();
        if (name == null) return null;
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return null;
        }
    }

    private static final class CharsetSubtitleParser implements SubtitleParser {

        private final SubtitleParser delegate;

        private CharsetSubtitleParser(SubtitleParser delegate) {
            this.delegate = delegate;
        }

        @Override
        public void parse(byte[] data, int offset, int length, OutputOptions outputOptions, Consumer<CuesWithTiming> output) {
            byte[] utf8 = convertToUtf8(data, offset, length);
            delegate.parse(utf8, 0, utf8.length, outputOptions, output);
        }

        @Override
        public int getCueReplacementBehavior() {
            return delegate.getCueReplacementBehavior();
        }

        @Override
        public void reset() {
            delegate.reset();
        }
    }
}
