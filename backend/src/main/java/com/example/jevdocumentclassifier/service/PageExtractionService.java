package com.example.jevdocumentclassifier.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class PageExtractionService {
    private static final int MIN_USEFUL_TEXT_LENGTH = 40;
    private final OcrService ocrService;

    public PageExtractionService(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    public List<ExtractedPage> extract(List<MultipartFile> files) throws IOException {
        List<ExtractedPage> pages = new ArrayList<>();
        int globalPageIndex = 1;

        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
            String contentType = file.getContentType() == null ? "" : file.getContentType();

            if (contentType.equals("application/pdf") || fileName.toLowerCase().endsWith(".pdf")) {
                try (PDDocument document = Loader.loadPDF(file.getBytes())) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    PDFRenderer renderer = new PDFRenderer(document);

                    for (int page = 1; page <= document.getNumberOfPages(); page++) {
                        stripper.setStartPage(page);
                        stripper.setEndPage(page);
                        String text = stripper.getText(document).strip();
                        boolean usedOcr = false;

                        if (text.length() < MIN_USEFUL_TEXT_LENGTH) {
                            BufferedImage image = renderer.renderImageWithDPI(page - 1, 220, ImageType.RGB);
                            text = ocrService.extract(image);
                            usedOcr = true;
                        }

                        pages.add(new ExtractedPage(globalPageIndex++, fileName, page, text, usedOcr));
                    }
                }
            } else {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
                String text = image == null ? "[UNSUPPORTED_IMAGE] " + fileName : ocrService.extract(image);
                pages.add(new ExtractedPage(globalPageIndex++, fileName, 1, text, true));
            }
        }
        return pages;
    }

    public record ExtractedPage(
            int globalPageIndex,
            String sourceName,
            int sourcePageNumber,
            String text,
            boolean ocrUsed
    ) {}
}
