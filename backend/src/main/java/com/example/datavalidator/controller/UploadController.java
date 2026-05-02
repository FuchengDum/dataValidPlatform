package com.example.datavalidator.controller;

import com.example.datavalidator.service.ExcelImportService;
import com.example.datavalidator.web.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class UploadController {
    private final ExcelImportService excelImportService;

    public UploadController(ExcelImportService excelImportService) {
        this.excelImportService = excelImportService;
    }

    @PostMapping("/upload")
    public ApiResponse<ExcelImportService.ImportResult> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(excelImportService.importWorkbook(file));
    }
}
