package com.example.BackendArchitectureLab.Service;

public interface ILineDiaryService {
    void handleText(String replyToken, String text);
    void handleAudio(String replyToken, String messageId);
}
