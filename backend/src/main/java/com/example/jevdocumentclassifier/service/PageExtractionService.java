package com.example.jevdocumentclassifier.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class PageExtractionService {

    public List<ExtractedPage> extract(List<MultipartFile> files) throws IOException {
        List<ExtractedPage> pages = new ArrayList<>();
        int globalPageIndex = 1;

        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
            String contentType = file.getContentType() == null ? "" : file.getContentType();

            if (contentType.equals("application/pdf") || fileName.toLowerCase().endsWith(".pdf")) {
                try (PDDocument document = Loader.loadPDF(file.getBytes())) {
                    PDFTextStripper stripper = new PDFTextStripper();

                    for (int page = 1; page <= document.getNumberOfPages(); page++) {
                        stripper.setStartPage(page);
                        stripper.setEndPage(page);
                        String text = stripper.getText(document);
                        pages.add(new ExtractedPage(globalPageIndex++, fileName, page, text));
                    }
                }
            } else {
                // Image OCR will be added next. Keeping the page in the pipeline makes
                // the upload/UI flow usable before Tesseract is installed.
                pages.add(new ExtractedPage(globalPageIndex++, fileName, 1,
                        "[OCR_REQUIRED] " + fileName));
            }
        }

        return pages;
    }

    public record ExtractedPage(
            int globalPageIndex,
            String sourceName,
            int sourcePageNumber,
            String text
    ) {}
}
