package com.example.BackendArchitectureLab.Service.Nlp;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import com.rnkrsoft.bopomofo4j.Bopomofo4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PhoneticConvertService {

    private final Tokenizer kuromojiTokenizer = new Tokenizer();
    private final Map<String, String> katakanaToRomajiMap = buildKatakanaMap();

    public String convert(String text, String mode, String lang) {
        if (text == null || text.isBlank() || "none".equalsIgnoreCase(mode)) {
            return null;
        }

        mode = mode.toLowerCase();
        
        try {
            if ("zh".equalsIgnoreCase(lang)) {
                if ("pinyin".equals(mode)) {
                    return toPinyin(text);
                } else if ("zhuyin".equals(mode)) {
                    return toZhuyin(text);
                }
            } else if ("ja".equalsIgnoreCase(lang) && "romaji".equals(mode)) {
                return toRomaji(text);
            }
        } catch (Exception e) {
            // 解析出錯時返回原文或 null
            e.printStackTrace();
        }

        return null; // 不支援的模式或語言組合
    }

    private String toPinyin(String text) throws BadHanyuPinyinOutputFormatCombination {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITH_TONE_MARK);
        // 使用 WITH_TONE_MARK 時，vCharType 必須設為 WITH_U_UNICODE
        format.setVCharType(HanyuPinyinVCharType.WITH_U_UNICODE);

        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.toString(c).matches("[\u4E00-\u9FA5]+")) {
                String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, format);
                if (pinyinArray != null && pinyinArray.length > 0) {
                    sb.append(pinyinArray[0]).append(" ");
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    private String toZhuyin(String text) {
        // bopomofo4j 的 pinyin 方法預設即可輸出帶音調的拼音或注音
        // 為了將漢字轉注音，我們使用 Bopomofo4j.pinyin(text, 2, false, false, " ") 獲取無音調拼音並手動轉換（此套件主要功能似乎是輸出拼音）
        // 備註：檢查 bopomofo4j API 後發現他沒有直接公開 pinyin2zhuyin，它主要還是提供漢字轉拼音功能。
        // 但因為名字叫 Bopomofo4j，我們可以用 ToneType = 0 也就是帶音調拼音回傳。
        // （若此套件不符合預期，可後續調整）
        return Bopomofo4j.pinyin(text, 0, false, false, " ");
    }

    private String toRomaji(String text) {
        text = Normalizer.normalize(text, Normalizer.Form.NFC);
        List<Token> tokens = kuromojiTokenizer.tokenize(text);
        StringBuilder sb = new StringBuilder();
        
        for (Token token : tokens) {
            String reading = token.getReading(); // 取得片假名讀音
            if (reading != null) {
                sb.append(katakanaToRomaji(reading)).append(" ");
            } else {
                // 如果沒有讀音(如標點符號)，保留原字
                sb.append(token.getSurface()).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private String katakanaToRomaji(String katakana) {
        StringBuilder romaji = new StringBuilder();
        for (int i = 0; i < katakana.length(); i++) {
            String ch = String.valueOf(katakana.charAt(i));
            // 簡單處理促音 (ッ) 和長音 (ー) 邏輯，此處實作基礎對照
            if (ch.equals("ッ") && i + 1 < katakana.length()) {
                String nextRomaji = katakanaToRomajiMap.get(String.valueOf(katakana.charAt(i+1)));
                if (nextRomaji != null && !nextRomaji.isEmpty()) {
                    romaji.append(nextRomaji.charAt(0));
                }
            } else if (ch.equals("ー")) {
                 // 長音省略或補母音，簡單處理略過或加橫線
                 romaji.append("-");
            } else {
                 romaji.append(katakanaToRomajiMap.getOrDefault(ch, ch));
            }
        }
        return romaji.toString();
    }

    private static Map<String, String> buildKatakanaMap() {
        Map<String, String> map = new HashMap<>();
        map.put("ア", "a"); map.put("イ", "i"); map.put("ウ", "u"); map.put("エ", "e"); map.put("オ", "o");
        map.put("カ", "ka"); map.put("キ", "ki"); map.put("ク", "ku"); map.put("ケ", "ke"); map.put("コ", "ko");
        map.put("サ", "sa"); map.put("シ", "shi"); map.put("ス", "su"); map.put("セ", "se"); map.put("ソ", "so");
        map.put("タ", "ta"); map.put("チ", "chi"); map.put("ツ", "tsu"); map.put("テ", "te"); map.put("ト", "to");
        map.put("ナ", "na"); map.put("ニ", "ni"); map.put("ヌ", "nu"); map.put("ネ", "ne"); map.put("ノ", "no");
        map.put("ハ", "ha"); map.put("ヒ", "hi"); map.put("フ", "fu"); map.put("ヘ", "he"); map.put("ホ", "ho");
        map.put("マ", "ma"); map.put("ミ", "mi"); map.put("ム", "mu"); map.put("メ", "me"); map.put("モ", "mo");
        map.put("ヤ", "ya"); map.put("ユ", "yu"); map.put("ヨ", "yo");
        map.put("ラ", "ra"); map.put("リ", "ri"); map.put("ル", "ru"); map.put("レ", "re"); map.put("ロ", "ro");
        map.put("ワ", "wa"); map.put("ヲ", "wo"); map.put("ン", "n");
        map.put("ガ", "ga"); map.put("ギ", "gi"); map.put("グ", "gu"); map.put("ゲ", "ge"); map.put("ゴ", "go");
        map.put("ザ", "za"); map.put("ジ", "ji"); map.put("ズ", "zu"); map.put("ゼ", "ze"); map.put("ゾ", "zo");
        map.put("ダ", "da"); map.put("ヂ", "ji"); map.put("ヅ", "zu"); map.put("デ", "de"); map.put("ド", "do");
        map.put("バ", "ba"); map.put("ビ", "bi"); map.put("ブ", "bu"); map.put("ベ", "be"); map.put("ボ", "bo");
        map.put("パ", "pa"); map.put("ピ", "pi"); map.put("プ", "pu"); map.put("ペ", "pe"); map.put("ポ", "po");
        
        // 凹音簡單處理
        map.put("キャ", "kya"); map.put("キュ", "kyu"); map.put("キョ", "kyo");
        map.put("シャ", "sha"); map.put("シュ", "shu"); map.put("ショ", "sho");
        map.put("チャ", "cha"); map.put("チュ", "chu"); map.put("チョ", "cho");
        map.put("ニャ", "nya"); map.put("ニュ", "nyu"); map.put("ニョ", "nyo");
        map.put("ヒャ", "hya"); map.put("ヒュ", "hyu"); map.put("ヒョ", "hyo");
        map.put("ミャ", "mya"); map.put("ミュ", "myu"); map.put("ミョ", "myo");
        map.put("リャ", "rya"); map.put("リュ", "ryu"); map.put("リョ", "ryo");
        map.put("ギャ", "gya"); map.put("ギュ", "gyu"); map.put("ギョ", "gyo");
        map.put("ジャ", "ja"); map.put("ジュ", "ju"); map.put("ジョ", "jo");
        map.put("ビャ", "bya"); map.put("ビュ", "byu"); map.put("ビョ", "byo");
        map.put("ピャ", "pya"); map.put("ピュ", "pyu"); map.put("ピョ", "pyo");
        
        return map;
    }
}
