package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.MealReceiptOcrCandidateVo;

import java.nio.file.Path;
import java.util.List;

public interface MealLocalOcrEngine {
    record OcrExecutionResult(boolean succeeded, String rawText, List<MealReceiptOcrCandidateVo> candidates, String errorMessage) {
    }

    OcrExecutionResult recognizeReceipt(List<Path> imageFiles);
}
