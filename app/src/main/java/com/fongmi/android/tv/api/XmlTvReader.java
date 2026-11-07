package com.fongmi.android.tv.api;

import com.github.catvod.net.HttpCallScope;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Reads one XMLTV entry at a time; ownership of the input stays with the caller. */
final class XmlTvReader {

    interface Consumer<T> {
        void accept(T value) throws Exception;
    }

    static final class Channel {
        String id = "";
        String logo = "";
        final List<String> names = new ArrayList<>();
    }

    static final class Programme {
        String channel = "";
        String start = "";
        String stop = "";
        String title = "";
    }

    static String read(InputStream input, Consumer<Channel> channels, Consumer<Programme> programmes) throws Exception {
        XmlPullParser parser = newParser();
        parser.setInput(input, null);
        return read(parser, channels, programmes);
    }

    static String read(Reader input, Consumer<Channel> channels, Consumer<Programme> programmes) throws Exception {
        XmlPullParser parser = newParser();
        parser.setInput(input);
        return read(parser, channels, programmes);
    }

    private static XmlPullParser newParser() throws XmlPullParserException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        // XMLTV may declare an external DTD, but parsing must not load it.
        if (parser.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL)) {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
        }
        return parser;
    }

    private static String read(XmlPullParser parser, Consumer<Channel> channels, Consumer<Programme> programmes) throws Exception {
        int event;
        do {
            event = next(parser);
        } while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT);
        parser.require(XmlPullParser.START_TAG, null, "tv");
        String date = attribute(parser, "date");
        while ((event = next(parser)) != XmlPullParser.END_TAG) {
            if (event == XmlPullParser.END_DOCUMENT) throw new XmlPullParserException("Unclosed tv element");
            if (event != XmlPullParser.START_TAG) continue;
            if (parser.getName().equals("channel") && channels != null) channels.accept(channel(parser));
            else if (parser.getName().equals("programme") && programmes != null) programmes.accept(programme(parser));
            else skip(parser);
        }
        // Validate the tail before the caller publishes any schedules.
        while (next(parser) != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG) throw new XmlPullParserException("Multiple root elements");
        }
        return date;
    }

    private static Channel channel(XmlPullParser parser) throws Exception {
        Channel channel = new Channel();
        channel.id = attribute(parser, "id");
        while (next(parser) != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            if (parser.getName().equals("display-name")) channel.names.add(text(parser));
            else {
                if (parser.getName().equals("icon") && channel.logo.isEmpty()) channel.logo = attribute(parser, "src");
                skip(parser);
            }
        }
        return channel;
    }

    private static Programme programme(XmlPullParser parser) throws Exception {
        Programme programme = new Programme();
        programme.channel = attribute(parser, "channel");
        programme.start = attribute(parser, "start");
        programme.stop = attribute(parser, "stop");
        while (next(parser) != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            if (parser.getName().equals("title") && programme.title.isEmpty()) programme.title = text(parser);
            else skip(parser);
        }
        return programme;
    }

    private static String attribute(XmlPullParser parser, String name) {
        String value = parser.getAttributeValue(null, name);
        return value == null ? "" : value;
    }

    private static String text(XmlPullParser parser) throws Exception {
        StringBuilder text = new StringBuilder();
        int event;
        while ((event = next(parser)) != XmlPullParser.END_TAG) {
            if (event == XmlPullParser.TEXT) text.append(parser.getText());
            else if (event == XmlPullParser.START_TAG) throw new XmlPullParserException("Nested text element");
        }
        return text.toString().trim();
    }

    private static void skip(XmlPullParser parser) throws Exception {
        int depth = 1;
        while (depth > 0) {
            int event = next(parser);
            if (event == XmlPullParser.START_TAG) depth++;
            else if (event == XmlPullParser.END_TAG) depth--;
        }
    }

    private static int next(XmlPullParser parser) throws Exception {
        HttpCallScope.throwIfCancelled();
        int event = parser.next();
        HttpCallScope.throwIfCancelled();
        if (event == XmlPullParser.END_DOCUMENT && parser.getDepth() > 0) throw new XmlPullParserException("Truncated XMLTV document");
        return event;
    }
}
