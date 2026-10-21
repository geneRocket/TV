package com.fongmi.android.tv.player;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.StringReader;

import javax.xml.parsers.SAXParserFactory;

/** Reads the existing DIDL item id without depending on Cling's process-wide SAX driver setup. */
public final class CastMediaTitle {
    private CastMediaTitle() {}

    public static String read(String uri, String metadata) {
        if (metadata == null || metadata.isEmpty()) return uri;
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XMLReader reader = factory.newSAXParser().getXMLReader();
            reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
            reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            reader.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            String[] id = new String[1];
            reader.setContentHandler(new DefaultHandler() {
                @Override public void startElement(String namespace, String localName, String qName, Attributes attributes) {
                    if (id[0] == null && "item".equals(localName)) {
                        String value = attributes.getValue("id");
                        id[0] = value == null ? "" : value;
                    }
                }
            });
            reader.parse(new InputSource(new StringReader(metadata)));
            return id[0] == null ? uri : id[0];
        } catch (Exception ignored) {
            return uri;
        }
    }
}
