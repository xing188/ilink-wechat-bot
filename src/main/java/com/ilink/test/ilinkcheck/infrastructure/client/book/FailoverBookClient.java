package com.ilink.test.ilinkcheck.infrastructure.client.book;

import com.ilink.test.ilinkcheck.application.tool.categories.book.BookInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * 带自动降级的图书 API 客户端。
 *
 * 查询顺序：豆瓣（国内、中文数据全）→ OpenLibrary（国际、备选）。
 * 豆瓣配置了 API Key 时才启用，未配置时直接走 OpenLibrary。
 */
@Component
public class FailoverBookClient implements BookApiClient {

    private static final Logger log = LoggerFactory.getLogger(FailoverBookClient.class);

    private final DoubanBookClient doubanClient;
    private final OpenLibraryClient openLibraryClient;
    private final boolean doubanEnabled;

    public FailoverBookClient(RestClient.Builder restClientBuilder,
                              @Value("${app.book.douban-api-key:}") String doubanApiKey) {
        this.openLibraryClient = new OpenLibraryClient(restClientBuilder);
        if (StringUtils.hasText(doubanApiKey)) {
            this.doubanClient = new DoubanBookClient(restClientBuilder, doubanApiKey);
            this.doubanEnabled = true;
            log.info("图书搜索：豆瓣 API 已启用（配置了 API Key），OpenLibrary 作为降级");
        } else {
            this.doubanClient = null;
            this.doubanEnabled = false;
            log.info("图书搜索：豆瓣 API 未配置（无 API Key），仅使用 OpenLibrary");
        }
    }

    @Override
    public List<BookInfo> search(String query, int limit) {
        if (doubanEnabled) {
            try {
                List<BookInfo> results = doubanClient.search(query, limit);
                if (!results.isEmpty()) {
                    return results;
                }
                log.warn("[降级] 豆瓣搜索无结果，切换到 OpenLibrary。query={}", query);
            }
            catch (Exception e) {
                log.warn("[降级] 豆瓣搜索失败，切换到 OpenLibrary。query={}, error={}", query, e.getMessage());
            }
        }

        try {
            return openLibraryClient.search(query, limit);
        }
        catch (Exception e) {
            log.error("[失败] OpenLibrary 搜索也失败。query={}, error={}", query, e.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<BookInfo> findByIsbn(String isbn) {
        if (doubanEnabled) {
            try {
                Optional<BookInfo> result = doubanClient.findByIsbn(isbn);
                if (result.isPresent()) {
                    return result;
                }
                log.warn("[降级] 豆瓣 ISBN 查询无结果，切换到 OpenLibrary。isbn={}", isbn);
            }
            catch (Exception e) {
                log.warn("[降级] 豆瓣 ISBN 查询失败，切换到 OpenLibrary。isbn={}, error={}", isbn, e.getMessage());
            }
        }

        try {
            return openLibraryClient.findByIsbn(isbn);
        }
        catch (Exception e) {
            log.error("[失败] OpenLibrary ISBN 查询也失败。isbn={}, error={}", isbn, e.getMessage());
            return Optional.empty();
        }
    }
}