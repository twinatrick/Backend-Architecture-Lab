package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Vo.AudioRecognizeVo;
import com.example.BackendArchitectureLab.Vo.SttResponseVo;
import com.example.BackendArchitectureLab.Service.ILearnService;
import com.example.BackendArchitectureLab.Service.ISttService;
import com.example.BackendArchitectureLab.Service.Nlp.PhoneticConvertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LearnService implements ILearnService {

    @Autowired
    private ISttService sttService;
    @Autowired
    private PhoneticConvertService phoneticConvertService;

    @Override
    public AudioRecognizeVo processAudio(MultipartFile file, String lang, String mode) {
        AudioRecognizeVo vo = new AudioRecognizeVo();
        
        try {
            // 1. 呼叫 Python STT 服務進行語音辨識
            SttResponseVo sttResult = sttService.recognize(file.getBytes(), lang);
            String text = (sttResult != null && sttResult.getText() != null) ? sttResult.getText() : "";
            vo.setText(text);

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
