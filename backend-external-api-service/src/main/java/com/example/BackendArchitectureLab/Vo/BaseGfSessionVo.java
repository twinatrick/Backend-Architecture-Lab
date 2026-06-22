package com.example.BackendArchitectureLab.Vo;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class BaseGfSessionVo {
    protected String gfName;
    protected String prompt;
    protected Boolean active;
    protected Boolean voiceEnabled;
    protected String voiceSampleKey;
    protected String voiceSampleText;
    protected String conversationHistory;
    protected String language;

    /**
     * 動態拼接系統提示詞與 4 句話長度限制規則
     */
    public List<Map<String, String>> buildSystemMessage() {
        List<Map<String, String>> messages = new ArrayList<>();
        String nameRule = "";
        if (this.gfName != null && !this.gfName.isBlank()) {
            nameRule = "你的名字是「" + this.gfName + "」。你自稱「" + this.gfName + "」。\n";
        }
        String activePrompt = (this.prompt != null && !this.prompt.isBlank()) 
                ? this.prompt 
                : "你是一個可愛的女朋友，用溫柔關心的語氣回覆";
        String lengthLimit = "\n【絕對鐵律】：你的回覆必須極度簡短，字數絕對不能超過 35 個字，且長度必須控制在 2 句話（句號/問號/驚嘆號）以內。回覆風格要像真人在手機即時通訊軟體（如 LINE）上打字一樣，非常口語、簡短、溫馨，千萬不要像 AI 或寫文章一樣長篇大論，除非使用者在當前對話中明確要求你詳細說明。";
        
        messages.add(Map.of("role", "system", "content", nameRule + activePrompt + lengthLimit));
        return messages;
    }
}
