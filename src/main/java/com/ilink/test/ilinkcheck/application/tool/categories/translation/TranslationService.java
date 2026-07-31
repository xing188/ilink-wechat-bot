package com.ilink.test.ilinkcheck.application.tool.categories.translation;

import java.util.List;

public interface TranslationService {
    String translate(String text, String sourceLang, String targetLang);

    /**
     * 批量翻译：将多段文本合并为一次 API 调用，按顺序返回翻译结果。
     * 返回列表长度与输入一致；某段翻译失败时对应位置为原文。
     */
    List<String> batchTranslate(List<String> texts, String sourceLang, String targetLang);
}
