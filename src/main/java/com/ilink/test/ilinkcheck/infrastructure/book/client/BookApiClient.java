package com.ilink.test.ilinkcheck.infrastructure.book.client;

import com.ilink.test.ilinkcheck.application.tool.book.BookInfo;
import java.util.List;
import java.util.Optional;

/**
 * 图书 API 客户端接口。
 * 不同的图书数据源实现此接口，可切换。
 */
public interface BookApiClient {

    /**
     * 搜索图书。
     *
     * @param query 搜索关键词（书名、作者等）
     * @param limit 返回条数上限
     * @return 匹配的图书列表
     */
    List<BookInfo> search(String query, int limit);

    /**
     * 根据 ISBN 查询图书。
     *
     * @param isbn 10位或13位 ISBN
     * @return 图书信息
     */
    Optional<BookInfo> findByIsbn(String isbn);
}