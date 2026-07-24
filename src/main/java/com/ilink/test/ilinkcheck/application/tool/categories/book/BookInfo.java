package com.ilink.test.ilinkcheck.application.tool.categories.book;

import java.util.List;

/**
 * 图书信息模型。
 *
 * @param id          唯一标识
 * @param title       书名
 * @param authors     作者列表
 * @param publishYear 首次出版年份
 * @param isbns       ISBN 列表
 * @param subjects    主题/分类标签
 * @param coverUrl    封面图片 URL
 * @param rating      评分（如 9.3），无评分时为 null
 * @param ratingsCount 评分人数
 */
public record BookInfo(
        String id,
        String title,
        List<String> authors,
        Integer publishYear,
        List<String> isbns,
        List<String> subjects,
        String coverUrl,
        Double rating,
        int ratingsCount
) {

    public String toSummary() {
        StringBuilder sb = new StringBuilder(title != null ? title : "未知书名");
        if (authors != null && !authors.isEmpty()) {
            sb.append(" / ").append(String.join(", ", authors));
        }
        if (publishYear != null) {
            sb.append(" (").append(publishYear).append(")");
        }
        if (rating != null) {
            sb.append(" ⭐").append(rating);
        }
        return sb.toString();
    }
}