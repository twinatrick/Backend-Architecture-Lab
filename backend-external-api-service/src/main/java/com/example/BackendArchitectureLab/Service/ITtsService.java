package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.TtsResponseVo;

public interface ITtsService {
    TtsResponseVo synthesize(String text, String language);

    /**
     * 下載語音音檔位元組陣列，支援 MinIO 認證下載與 HTTP 回退機制
     */
    byte[] downloadAudio(String audioUrl) throws Exception;

    /**
     * 為 MinIO 內部私有連結生成臨時的公開預簽名 URL，若失敗或非 MinIO 連結則傳回原 URL
     */
    String getPresignedUrl(String audioUrl);

    /**
     * 從內部的 MinIO 儲存桶下載 AI 合成的 TTS 語音暫存檔
     */
    byte[] downloadTtsFile(String fileName) throws Exception;
}
