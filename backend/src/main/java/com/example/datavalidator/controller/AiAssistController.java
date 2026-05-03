package com.example.datavalidator.controller;

import com.example.datavalidator.exception.BadRequestException;
import com.example.datavalidator.persistence.FindingEvidenceEntity;
import com.example.datavalidator.persistence.ValidationFindingEntity;
import com.example.datavalidator.repository.FindingEvidenceRepository;
import com.example.datavalidator.repository.ValidationFindingRepository;
import com.example.datavalidator.service.AiAssistService;
import com.example.datavalidator.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiAssistController {
    private final AiAssistService aiAssistService;
    private final ValidationFindingRepository findingRepository;
    private final FindingEvidenceRepository evidenceRepository;

    public AiAssistController(AiAssistService aiAssistService,
                              ValidationFindingRepository findingRepository,
                              FindingEvidenceRepository evidenceRepository) {
        this.aiAssistService = aiAssistService;
        this.findingRepository = findingRepository;
        this.evidenceRepository = evidenceRepository;
    }

    @GetMapping("/findings/{findingId}/analysis")
    public ApiResponse<AiAssistService.AnalysisResult> analyzeFinding(@PathVariable String findingId) {
        ValidationFindingEntity finding = findingRepository.findById(findingId)
                .orElseThrow(() -> new BadRequestException("异常不存在: " + findingId));
        List<FindingEvidenceEntity> evidences = evidenceRepository.findByFindingId(findingId);
        return ApiResponse.ok(aiAssistService.analyzeFinding(finding, evidences));
    }

    @PostMapping("/sql-drafts")
    public ApiResponse<AiAssistService.SqlDraftResult> draftSql(
            @RequestBody AiAssistService.SqlDraftRequest request) {
        return ApiResponse.ok(aiAssistService.draftValidationSql(request));
    }
}
