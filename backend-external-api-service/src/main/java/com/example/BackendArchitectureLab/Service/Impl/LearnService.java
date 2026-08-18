package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.AudioRecognizeVo;
import com.example.BackendArchitectureLab.Vo.SttResponseVo;
import com.example.BackendArchitectureLab.Service.ILearnService;
import com.example.BackendArchitectureLab.Service.ISttService;
import com.example.BackendArchitectureLab.Service.Nlp.PhoneticConvertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class LearnService implements ILearnService {

    private final ISttService sttService;
    private final PhoneticConvertService phoneticConvertService;

    @Override
    public AudioRecognizeVo processAudio(MultipartFile file, String lang, String mode) {
        return processAudio(file, lang, mode, null);
    }

    @Override
    public AudioRecognizeVo processAudio(MultipartFile file, String lang, String mode, String provider) {
        AudioRecognizeVo vo = new AudioRecognizeVo();

        try {
            // 1. 呼叫 Python STT 服務進行語音辨識（可指定 provider）
            SttResponseVo sttResult = sttService.recognize(file.getBytes(), lang, provider);
            String text = (sttResult != null && sttResult.getText() != null) ? sttResult.getText() : "";
            vo.setText(text);
            if (sttResult != null) {
                vo.setDurationSec(sttResult.getDurationSec());
                vo.setAudioUrl(sttResult.getAudioUrl());
            }

            // 2. 轉換拼音/注音/羅馬音
            String phonetic = phoneticConvertService.convert(text, mode, lang);
            vo.setPhonetic(phonetic);

        } catch (Exception e) {
            e.printStackTrace();
            vo.setText("音訊處理失敗: " + e.getMessage());
        }

        return vo;
    }
}
