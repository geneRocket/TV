package com.fongmi.android.tv.bean;

import org.junit.Test;
import org.w3c.dom.Document;
import static org.junit.Assert.*;

public class DanmuXmlTest {
    @Test public void ordinaryCommentXmlRetainsAttributesAndUnicode() throws Exception {
        Document document = Danmu.parseXmlDocument("<comments><comment time='1.5'>中文弹幕 &amp; text</comment></comments>");
        assertEquals("1.5", document.getElementsByTagName("comment").item(0).getAttributes().getNamedItem("time").getNodeValue());
        assertEquals("中文弹幕 & text", document.getElementsByTagName("comment").item(0).getTextContent());
    }

    @Test public void nestedCommentFieldsArePreserved() throws Exception {
        Document document = Danmu.parseXmlDocument("<root><item><timeOffset>2500</timeOffset><text><![CDATA[Hello <world>]]></text></item></root>");
        assertEquals("2500", document.getElementsByTagName("timeOffset").item(0).getTextContent());
        assertEquals("Hello <world>", document.getElementsByTagName("text").item(0).getTextContent());
    }

    @Test public void internalAndExternalDtdsAreRejectedBeforeParsing() throws Exception {
        for (String xml : new String[]{
                "<!DOCTYPE root [<!ENTITY test 'expanded'>]><root>&test;</root>",
                "<!DOCTYPE root SYSTEM 'file:///does-not-exist'><root/>"}) {
            try { Danmu.parseXmlDocument(xml); fail("DTD accepted"); }
            catch (IllegalArgumentException expected) { assertEquals("Unsupported XML document type", expected.getMessage()); }
        }
    }
}
