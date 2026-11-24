package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Config.MealProperties;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Service.MealLocalOcrEngine;
import com.example.BackendArchitectureLab.Vo.MealReceiptOcrCandidateVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class TesseractProcessOcrEngine implements MealLocalOcrEngine {
    private static final int MAX_OUTPUT_BYTES = 65536;
    private static final Pattern LINE_ITEM_PATTERN = Pattern.compile(
            "^\\s*([^0-9$NT￥¥]{1,60}?)\\s+(?:NT\\$|\\$|NTD)?\\s*(\\d{1,6}(?:\\.\\d{1,2})?)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> EXCLUDED_TOTAL_KEYWORDS = List.of(
            "總計", "合計", "小計", "總額", "應付", "實收", "找零", "服務費", "折扣", "稅額", "統編", "發票",
            "total", "subtotal", "tax", "service", "discount", "change", "cash");

    private final MealProperties properties;
    private final Semaphore workerSemaphore = new Semaphore(1);
    private final AtomicInteger waitingQueue = new AtomicInteger(0);

    @Override
    public OcrExecutionResult recognizeReceipt(List<Path> imageFiles) {
        if (!properties.isOcrEnabled()) {
            return new OcrExecutionResult(false, "", List.of(), "本機 OCR 未啟用，請手動輸入單品金額並填寫金額說明");
        }
        if (imageFiles == null || imageFiles.isEmpty()) {
            return new OcrExecutionResult(false, "", List.of(), "尚未上傳消費明細圖片，請上傳圖片或手動輸入金額與說明");
        }

        int queueCap = Math.max(1, properties.getOcrMaxQueueSize());
        int currentWaiting = waitingQueue.incrementAndGet();
        try {
            if (currentWaiting > queueCap + 1) {
                throw new AppException("OCR_QUEUE_FULL", "本機 OCR 佇列已滿，請稍後重試或改為手動輸入金額與說明", 429);
            }
            int timeoutSec = Math.max(1, properties.getOcrTimeoutSeconds());
            boolean acquired = workerSemaphore.tryAcquire(timeoutSec, TimeUnit.SECONDS);
            if (!acquired) {
                throw new AppException("OCR_QUEUE_TIMEOUT", "等待本機 OCR 工作超時，請稍後重試或手動輸入", 429);
            }
            try {
                StringBuilder combinedRaw = new StringBuilder();
                for (Path imagePath : imageFiles) {
                    String pageText = runSingleProcess(imagePath, timeoutSec);
                    if (!pageText.isBlank()) {
                        if (combinedRaw.length() > 0) combinedRaw.append('\n');
                        combinedRaw.append(pageText.trim());
                    }
                }
                String rawText = combinedRaw.toString();
                List<MealReceiptOcrCandidateVo> candidates = parseCandidates(rawText);
                if (candidates.isEmpty()) {
                    return new OcrExecutionResult(
                            false,
                            rawText,
                            List.of(),
                            "本機 OCR 未能辨識出有效的單品金額項目，請手動輸入金額並填寫金額說明");
                }
                return new OcrExecutionResult(true, rawText, candidates, null);
            } finally {
                workerSemaphore.release();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new OcrExecutionResult(false, "", List.of(), "本機 OCR 執行中斷，請手動輸入金額與說明");
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Local OCR process failed: {}", ex.getMessage());
            return new OcrExecutionResult(false, "", List.of(), "本機 OCR 執行失敗（" + ex.getMessage() + "），請手動輸入金額與說明");
        } finally {
            waitingQueue.decrementAndGet();
        }
    }

    private String runSingleProcess(Path imagePath, int timeoutSec) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                properties.getOcrCommand(),
                imagePath.toAbsolutePath().toString(),
                "stdout",
                "-l",
                properties.getOcrLanguage());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        byte[] outputBytes;
        try (InputStream in = process.getInputStream()) {
            outputBytes = in.readNBytes(MAX_OUTPUT_BYTES);
        }
        boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("OCR 子行程執行超時");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("OCR 子行程非預期結束碼: " + process.exitValue());
        }
        return new String(outputBytes, StandardCharsets.UTF_8);
    }

    public static List<MealReceiptOcrCandidateVo> parseCandidates(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        List<MealReceiptOcrCandidateVo> results = new ArrayList<>();
        int index = 0;
        for (String line : rawText.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            Matcher matcher = LINE_ITEM_PATTERN.matcher(trimmed);
            if (!matcher.matches()) continue;
            String rawName = matcher.group(1).trim();
            String rawPrice = matcher.group(2).trim();
            if (isExcludedSummaryLine(rawName)) continue;
            try {
                BigDecimal amount = new BigDecimal(rawPrice);
                if (amount.compareTo(BigDecimal.ZERO) >= 0 && amount.compareTo(new BigDecimal("999999.99")) <= 0
                        && amount.scale() <= 2) {
                    results.add(new MealReceiptOcrCandidateVo(index++, rawName, amount));
                }
            } catch (NumberFormatException ignored) {
                // 忽略非合法金額行
            }
        }
        return results;
    }

    private static boolean isExcludedSummaryLine(String itemName) {
        String lower = itemName.toLowerCase(Locale.ROOT);
        for (String keyword : EXCLUDED_TOTAL_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
