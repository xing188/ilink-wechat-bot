package com.ilink.test.ilinkcheck.application.voice;

public interface TextToSpeechService {
    /**
     * 文字合成语音mp3
     * @param text 文本
     * @param voice 音色（可选）
     * @return mp3二进制数组
     */
    byte[] synthesizeMp3(String text, String voice);

    default byte[] synthesizeMp3(String text) {
        return synthesizeMp3(text, null);
    }
}