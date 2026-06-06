package io.agenttoolbox.tool.rag;

import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;

/**
 * Selects the appropriate DocumentParser based on file extension.
 */
public final class DocumentParserFactory {

    private DocumentParserFactory() {}

    public static DocumentParser forFile(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return new ApachePdfBoxDocumentParser();
        }
        if (lower.endsWith(".docx") || lower.endsWith(".doc")) {
            return new ApachePoiDocumentParser();
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".pptx")) {
            return new ApachePoiDocumentParser();
        }
        return new TextDocumentParser();
    }
}
