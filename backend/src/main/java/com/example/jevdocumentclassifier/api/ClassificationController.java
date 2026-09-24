package com.example.jevdocumentclassifier.api;

import com.example.jevdocumentclassifier.model.ClassificationResponse;
import com.example.jevdocumentclassifier.service.ClassificationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/classifications")
public class ClassificationController {

    private final ClassificationService classificationService;

    public ClassificationController(ClassificationService classificationService) {
        this.classificationService = classificationService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ClassificationResponse classify(@RequestPart("files") List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one file is required");
        }

        return classificationService.classify(files);
    }
}
