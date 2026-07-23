package com.ilink.test.ilinkcheck.application.tool.translation;

import com.ilink.test.ilinkcheck.application.tool.ChatFunctionTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class TranslationTool implements ChatFunctionTool {

    private final TranslationService translationService;

    public TranslationTool(TranslationService translationService) {
        this.translationService = translationService;
    }

    @Tool(name = "translate", description = "将文本从源语言翻译到目标语言，支持任意语言互译")
    public String translate(
            @ToolParam(required = true, description = "待翻译的文本内容")
            String text,
            @ToolParam(required = true, description = "源语言，如 Chinese、English、Japanese、French 等")
            String sourceLang,
            @ToolParam(required = true, description = "目标语言，如 Chinese、English、Japanese、French 等")
            String targetLang) {

        String result = translationService.translate(text, sourceLang, targetLang);
        if (result == null) {
            return "翻译失败，请稍后再试。";
        }
        return String.format("[翻译 %s→%s]\n%s", sourceLang, targetLang, result.trim());
    }
}
