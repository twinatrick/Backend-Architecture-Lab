package com.example.BackendArchitectureLab.Service.Onnx;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import io.github.givimad.whisperjni.WhisperJNI;
import io.github.givimad.whisperjni.WhisperContext;
import io.github.givimad.whisperjni.WhisperFullParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class WhisperOnnxService {

    @Value("${ai.whisper.model-path:models/ggml-large-v3-turbo.bin}")
    private String modelPath;

    private WhisperJNI whisper;
    private WhisperContext context;
    private boolean isReady = false;

    @PostConstruct
    public void init() {
        try {
            WhisperJNI.loadLibrary();
            this.whisper = new WhisperJNI();

            Path modelFile = Paths.get(modelPath);
            if (!modelFile.toFile().exists()) {
                System.out.println("Whisper 模型不存在: " + modelFile.toAbsolutePath() + "，使用模擬文字回退");
                return;
            }

            this.context = whisper.init(modelFile);
            this.isReady = true;
            System.out.println("Whisper 模型載入成功: " + modelFile.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("Whisper 初始化失敗: " + e.getMessage());
        }
    }

    public String transcribe(float[] audioData, String lang) {
        if (!isReady || context == null) {
            System.out.println("Whisper 模型未就緒，回傳模擬文字");
            if ("ja".equalsIgnoreCase(lang)) {
                return "こんにちは、これはテストです。";
            }
            return "這是一段模擬的語音辨識結果。";
        }

        try {
            WhisperFullParams params = new WhisperFullParams();
            if ("ja".equalsIgnoreCase(lang)) {
                params.language = "ja";
            } else {
                params.language = "zh";
            }
            params.nThreads = 4;
            params.suppressNonSpeechTokens = true;
            params.logprobThold = 0.0f;
            params.noSpeechThold = 0.1f;
            params.temperatureInc = 0.0f;

            int result = whisper.full(context, params, audioData, audioData.length);
            if (result != 0) {
                return "語音辨識失敗，錯誤碼: " + result;
            }

            StringBuilder sb = new StringBuilder();
            int segments = whisper.fullNSegments(context);
            for (int i = 0; i < segments; i++) {
                sb.append(whisper.fullGetSegmentText(context, i));
            }
            String rawText = sb.toString().trim();
            if ("zh".equalsIgnoreCase(lang)) {
                return ZhConverterUtil.toTraditional(rawText);
            }
            return rawText;
        } catch (Exception e) {
            e.printStackTrace();
            return "語音辨識發生錯誤: " + e.getMessage();
        }
    }

    @PreDestroy
    public void close() {
        if (context != null) {
            try {
                context.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
