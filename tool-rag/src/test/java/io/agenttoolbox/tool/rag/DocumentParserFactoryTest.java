package io.agenttoolbox.tool.rag;

import dev.langchain4j.data.document.DocumentParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentParserFactoryTest {

    @Test
    void returnsPdfParserForPdfExtension() {
        assertThat(DocumentParserFactory.forFile("report.pdf").getClass().getSimpleName())
                .contains("Pdf");
    }

    @Test
    void returnsPdfParserForUppercaseExtension() {
        assertThat(DocumentParserFactory.forFile("REPORT.PDF").getClass().getSimpleName())
                .contains("Pdf");
    }

    @Test
    void returnsPoiParserForDocxExtension() {
        assertThat(DocumentParserFactory.forFile("notes.docx").getClass().getSimpleName())
                .contains("Poi");
    }

    @Test
    void returnsPoiParserForDocExtension() {
        assertThat(DocumentParserFactory.forFile("old.doc").getClass().getSimpleName())
                .contains("Poi");
    }

    @Test
    void returnsPoiParserForXlsxExtension() {
        assertThat(DocumentParserFactory.forFile("data.xlsx").getClass().getSimpleName())
                .contains("Poi");
    }

    @Test
    void returnsPoiParserForPptxExtension() {
        assertThat(DocumentParserFactory.forFile("slides.pptx").getClass().getSimpleName())
                .contains("Poi");
    }

    @Test
    void returnsTextParserForTxtExtension() {
        assertThat(DocumentParserFactory.forFile("readme.txt").getClass().getSimpleName())
                .contains("Text");
    }

    @Test
    void returnsTextParserForMdExtension() {
        assertThat(DocumentParserFactory.forFile("docs.md").getClass().getSimpleName())
                .contains("Text");
    }

    @Test
    void returnsTextParserForUnknownExtension() {
        assertThat(DocumentParserFactory.forFile("config.yaml").getClass().getSimpleName())
                .contains("Text");
    }
}
