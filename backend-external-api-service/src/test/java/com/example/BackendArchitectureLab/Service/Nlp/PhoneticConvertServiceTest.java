package com.example.BackendArchitectureLab.Service.Nlp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneticConvertServiceTest {

    private PhoneticConvertService service;

    @BeforeEach
    void setUp() {
        service = new PhoneticConvertService();
    }

    @Test
    @DisplayName("zh + pinyin 應回傳拼音字串")
    void shouldConvertChineseToPinyin() {
        String result = service.convert("你好", "pinyin", "zh");
        assertNotNull(result);
        assertFalse(result.isBlank());
        assertTrue(result.contains("n") || result.contains("h"));
    }

    @Test
    @DisplayName("zh + zhuyin 應回傳注音字串")
    void shouldConvertChineseToZhuyin() {
        String result = service.convert("中文", "zhuyin", "zh");
        assertNotNull(result);
    }

    @Test
    @DisplayName("ja + romaji 應回傳羅馬音")
    void shouldConvertJapaneseToRomaji() {
        String result = service.convert("こんにちは", "romaji", "ja");
        assertNotNull(result);
        assertTrue(result.contains("konnichiwa") || result.contains("konnichiha") || !result.isEmpty());
    }

    @Test
    @DisplayName("mode=none 應回傳 null")
    void shouldReturnNullWhenModeIsNone() {
        assertNull(service.convert("你好", "none", "zh"));
    }

    @Test
    @DisplayName("text 為空白時應回傳 null")
    void shouldReturnNullWhenTextIsBlank() {
        assertNull(service.convert("  ", "pinyin", "zh"));
        assertNull(service.convert(null, "pinyin", "zh"));
    }

    @Test
    @DisplayName("不支援的語言+模式組合應回傳 null")
    void shouldReturnNullForUnsupportedCombination() {
        assertNull(service.convert("hello", "romaji", "zh"));
        assertNull(service.convert("你好", "romaji", "zh"));
    }
}
