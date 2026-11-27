package com.example.BackendArchitectureLab.Config;

public interface MealLineClient {
    boolean isConfigured();
    void replyText(String replyToken, String text);
    void pushText(String lineUserId, String text);
}
