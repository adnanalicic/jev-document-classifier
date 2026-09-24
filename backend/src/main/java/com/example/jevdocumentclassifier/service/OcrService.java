package com.example.jevdocumentclassifier.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
public class OcrService {
    private final String command;
    private final String languages;

    public OcrService(
            @Value("\${ocr.tesseract-command:tesseract}") String command,
            @Value("\${ocr.languages:eng+deu}") String languages
    ) {
        this.command = command;
        this.languages = languages;
    }

    public String extract(BufferedImage image) {
        Path tempImage = null;
        try {
            tempImage = Files.createTempFile("jev-ocr-", ".png");
            ImageIO.write(image, "png", tempImage.toFile());

            Process process = new ProcessBuilder(
                    command, tempImage.toAbsolutePath().toString(), "stdout",
                    "-l", languages, "--psm", "6")
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "[OCR_TIMEOUT]";
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return "[OCR_UNAVAILABLE] " + output.strip();
            }
            return output.strip();
        } catch (IOException e) {
            return "[OCR_UNAVAILABLE] Install Tesseract or configure OCR_TESSERACT_COMMAND";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[OCR_INTERRUPTED]";
        } finally {
            if (tempImage != null) {
                try { Files.deleteIfExists(tempImage); } catch (IOException ignored) {}
            }
        }
    }
}
