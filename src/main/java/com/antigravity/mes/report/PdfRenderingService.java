package com.antigravity.mes.report;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;

/** Renders an HTML string (produced by Claude) to a PDF byte array. */
@Service
public class PdfRenderingService {

    public byte[] renderToPdf(String html) throws Exception {
        // Flying Saucer requires strictly well-formed XML, but model-generated HTML routinely
        // has minor violations (unclosed tags, bare '&', named entities like &nbsp;). jsoup
        // parses it leniently like a browser would, then re-serializes as clean XHTML.
        Document doc = Jsoup.parse(html);
        doc.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .charset("UTF-8");
        String cleanXhtml = doc.html();

        ITextRenderer renderer = new ITextRenderer();
        renderer.setDocumentFromString(cleanXhtml);
        renderer.layout();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            renderer.createPDF(out);
            return out.toByteArray();
        }
    }
}
