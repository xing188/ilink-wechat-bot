package com.ilink.test.ilinkcheck.model;

import lombok.Getter;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Getter
public enum VoiceOption {
    OPTION_1(1, "龙安洋", "longanyang", "阳光大男孩 (20~30岁)"),
    OPTION_2(2, "龙安欢（V3）", "longanhuan_v3", "欢脱元气女 (多方言支持)"),
    OPTION_3(3, "龙呼呼", "longhuhu_v3", "天真烂漫女童 (6~10岁)"),
    OPTION_4(4, "龙老伯", "longlaobo_v3", "沧桑岁月爷 (60岁以上)"),
    OPTION_5(5, "龙猴哥", "longhouge_v3", "经典猴哥 (20~25岁)");

    private final int index;
    private final String name;
    private final String voiceParam;
    private final String feature;

    VoiceOption(int index, String name, String voiceParam, String feature) {
        this.index = index;
        this.name = name;
        this.voiceParam = voiceParam;
        this.feature = feature;
    }

    public static VoiceOption match(String input) {
        if (!StringUtils.hasText(input)) {
            return null;
        }

        String normalized = normalize(input);
        for (VoiceOption option : values()) {
            if (option.matchesExactSelection(normalized)) {
                return option;
            }
        }
        return null;
    }

    public static VoiceOption findMentioned(String input) {
        if (!StringUtils.hasText(input)) {
            return null;
        }

        String normalized = normalize(input);
        for (VoiceOption option : values()) {
            if (normalized.contains(normalize(option.name))
                    || normalized.contains(normalize(option.voiceParam))) {
                return option;
            }
        }
        return null;
    }

    public static VoiceOption fromVoiceParam(String voiceParam) {
        if (!StringUtils.hasText(voiceParam)) {
            return null;
        }

        String normalized = normalize(voiceParam);
        for (VoiceOption option : values()) {
            if (normalize(option.voiceParam).equals(normalized)) {
                return option;
            }
        }
        return null;
    }

    public String displayLine(boolean current) {
        return String.format("%d. %s [%s]%s", index, name, feature, current ? " √" : "");
    }

    private boolean matchesExactSelection(String normalizedInput) {
        if (!StringUtils.hasText(normalizedInput)) {
            return false;
        }

        Integer indexValue = parseIndex(normalizedInput);
        if (indexValue != null && indexValue == index) {
            return true;
        }

        return normalize(name).equals(normalizedInput)
                || normalize(voiceParam).equals(normalizedInput);
    }

    private static Integer parseIndex(String normalizedInput) {
        return switch (normalizedInput) {
            case "1", "一", "一号", "第一个", "第1个", "第一种", "第1种", "第1号", "第一位" -> 1;
            case "2", "二", "二号", "第二个", "第2个", "第二种", "第2种", "第2号", "第二位" -> 2;
            case "3", "三", "三号", "第三个", "第3个", "第三种", "第3种", "第3号", "第三位" -> 3;
            case "4", "四", "四号", "第四个", "第4个", "第四种", "第4种", "第4号", "第四位" -> 4;
            case "5", "五", "五号", "第五个", "第5个", "第五种", "第5种", "第5号", "第五位" -> 5;
            default -> null;
        };
    }

    private static String normalize(String input) {
        return input == null ? "" : input.trim()
                .toLowerCase(Locale.ROOT)
                .replace("（", "(")
                .replace("）", ")")
                .replace("【", "[")
                .replace("】", "]")
                .replaceAll("[\\s\\p{Punct}，。、“”‘’：；？！·]", "");
    }
}
