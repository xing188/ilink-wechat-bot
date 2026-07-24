package com.ilink.test.ilinkcheck.application.tool.book;

import com.ilink.test.ilinkcheck.infrastructure.client.book.BookApiClient;
import com.ilink.test.ilinkcheck.infrastructure.client.book.BookClientException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.DecimalFormat;
import java.util.List;

@Service
public class BookSearchService {

    private static final String MISSING_QUERY_MESSAGE = """
            搜索图书需要提供书名或关键词。
            请告诉我想搜索什么书，例如：三体、百年孤独、Python编程。
            """;

    private static final DecimalFormat RATING_FMT = new DecimalFormat("0.#");

    private final BookApiClient bookApiClient;

    public BookSearchService(BookApiClient bookApiClient) {
        this.bookApiClient = bookApiClient;
    }

    public String searchBooks(String query, Integer limit) {
        String normalized = normalize(query);
        if (!StringUtils.hasText(normalized)) {
            return MISSING_QUERY_MESSAGE.trim();
        }

        int capped = limit != null ? Math.max(1, Math.min(limit, 10)) : 5;

        try {
            List<BookInfo> books = bookApiClient.search(normalized, capped);

            if (books.isEmpty()) {
                return "没有找到与 \"" + normalized + "\" 相关的图书，请尝试其他关键词。";
            }

            return formatSearchResults(normalized, books);
        }
        catch (BookClientException e) {
            return "图书搜索服务暂时不可用，请稍后再试。\n" + e.getMessage();
        }
        catch (Exception e) {
            return "搜索出错了，请稍后再试。";
        }
    }

    public String recommendBooks(String subject, Integer limit) {
        String normalizedSubject = normalize(subject);
        String query = StringUtils.hasText(normalizedSubject)
                ? normalizedSubject + " 图书"
                : "热门图书 推荐";

        int capped = limit != null ? Math.max(1, Math.min(limit, 10)) : 5;

        try {
            List<BookInfo> books = bookApiClient.search(query, capped);

            if (books.isEmpty()) {
                String topic = StringUtils.hasText(normalizedSubject) ? normalizedSubject : "热门";
                return "暂时没有找到 \"" + topic + "\" 分类下的推荐图书。";
            }

            return formatRecommendations(books, normalizedSubject);
        }
        catch (BookClientException e) {
            return "图书推荐服务暂时不可用，请稍后再试。";
        }
        catch (Exception e) {
            return "推荐出错了，请稍后再试。";
        }
    }

    public String lookupByIsbn(String isbn) {
        String cleanIsbn = normalize(isbn);
        if (!StringUtils.hasText(cleanIsbn)) {
            return "请提供 ISBN 号码。";
        }

        cleanIsbn = cleanIsbn.replaceAll("[\\s-]", "");

        try {
            var opt = bookApiClient.findByIsbn(cleanIsbn);
            if (opt.isEmpty()) {
                return "没有找到 ISBN 为 \"" + cleanIsbn + "\" 的图书。请确认 ISBN 是否正确。";
            }

            return formatBookDetail(opt.get());
        }
        catch (BookClientException e) {
            return "图书查询服务暂时不可用，请稍后再试。";
        }
    }

    private String formatSearchResults(String query, List<BookInfo> books) {
        StringBuilder sb = new StringBuilder();
        sb.append("📚 搜索 \"").append(query).append("\" 找到以下图书：\n\n");

        for (int i = 0; i < books.size(); i++) {
            BookInfo book = books.get(i);
            sb.append(i + 1).append(". ").append(formatBrief(book)).append("\n");
        }

        sb.append("\n回复 ISBN 可查看图书详情。");
        return sb.toString();
    }

    private String formatRecommendations(List<BookInfo> books, String subject) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(subject)) {
            sb.append("📚 推荐 \"").append(subject).append("\" 相关图书：\n\n");
        } else {
            sb.append("📚 热门图书推荐：\n\n");
        }

        for (int i = 0; i < books.size(); i++) {
            BookInfo book = books.get(i);
            sb.append(i + 1).append(". ").append(formatBrief(book)).append("\n");
        }

        sb.append("\n回复书名或 ISBN 可查看详情。");
        return sb.toString();
    }

    private String formatBrief(BookInfo book) {
        StringBuilder sb = new StringBuilder();
        sb.append(book.title() != null ? book.title() : "未知书名");
        if (book.authors() != null && !book.authors().isEmpty()) {
            sb.append(" | ").append(String.join(", ", book.authors()));
        }
        if (book.publishYear() != null) {
            sb.append(" (").append(book.publishYear()).append(")");
        }
        if (book.rating() != null) {
            sb.append(" ⭐").append(RATING_FMT.format(book.rating()));
        }
        return sb.toString();
    }

    private String formatBookDetail(BookInfo book) {
        StringBuilder sb = new StringBuilder();
        sb.append("━━━━━━━━━━━━━━━━━━━\n");
        sb.append("  ").append(book.title() != null ? book.title() : "未知书名").append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━\n");

        if (book.authors() != null && !book.authors().isEmpty()) {
            sb.append("✍️ 作者：").append(String.join(", ", book.authors())).append("\n");
        }
        if (book.publishYear() != null) {
            sb.append("📅 出版年份：").append(book.publishYear()).append("\n");
        }
        if (book.rating() != null) {
            sb.append("⭐ 评分：").append(RATING_FMT.format(book.rating()));
            if (book.ratingsCount() > 0) {
                sb.append(" (").append(book.ratingsCount()).append("人评价)");
            }
            sb.append("\n");
        }
        if (book.isbns() != null && !book.isbns().isEmpty()) {
            sb.append("🔖 ISBN：").append(String.join(" / ", book.isbns())).append("\n");
        }
        if (book.subjects() != null && !book.subjects().isEmpty()) {
            sb.append("🏷️ 标签：").append(String.join("、", book.subjects())).append("\n");
        }

        return sb.toString();
    }

    private String normalize(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }
}