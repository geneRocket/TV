package com.fongmi.quickjs.bean;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class Cache {

    public String pdfhHtml;
    public String pdfaHtml;
    public Document pdfhDoc;
    public Document pdfaDoc;

    public Document getPdfh(String html) {
        updatePdfh(html);
        return pdfhDoc;
    }

    public Document getPdfa(String html) {
        updatePdfa(html);
        return pdfaDoc;
    }

    private void updatePdfh(String html) {
        if (html.equals(pdfhHtml)) return;
        // Both parser paths only select nodes; exclusions clone before removing.
        pdfhDoc = html.equals(pdfaHtml) ? pdfaDoc : Jsoup.parse(html);
        pdfhHtml = html;
    }

    private void updatePdfa(String html) {
        if (html.equals(pdfaHtml)) return;
        pdfaDoc = html.equals(pdfhHtml) ? pdfhDoc : Jsoup.parse(html);
        pdfaHtml = html;
    }
}
