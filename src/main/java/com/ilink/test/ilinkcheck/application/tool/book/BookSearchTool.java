package com.ilink.test.ilinkcheck.application.tool.book;

import com.ilink.test.ilinkcheck.application.tool.ChatFunctionTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class BookSearchTool implements ChatFunctionTool {

    private final BookSearchService bookSearchService;

    public BookSearchTool(BookSearchService bookSearchService) {
        this.bookSearchService = bookSearchService;
    }

    @Tool(
            name = "searchBooks",
            description = """
                    搜索图书，根据书名或关键词返回匹配的图书列表。
                    当用户想找某本书、查询某位作者的作品，或搜索某个主题的图书时使用。
                    支持中文和英文搜索。
                    如果搜索结果为空或服务不可用，你可以根据自己的知识回答用户。
                    """
    )
    public String searchBooks(
            @ToolParam(required = true, description = "搜索关键词，支持书名、作者名或任意关键词，例如：三体、百年孤独、刘慈欣、机器学习")
            String query,
            @ToolParam(required = false, description = "返回结果数量，1-10 之间，默认为 5")
            Integer limit
    ) {
        return bookSearchService.searchBooks(query, limit);
    }

    @Tool(
            name = "recommendBooks",
            description = """
                    推荐图书，支持按主题分类推荐。
                    当用户说"推荐几本书"、"有什么好书"、"推荐科幻小说"时使用。
                    可按主题推荐，如：科幻、小说、历史、哲学、编程、育儿等。
                    如果推荐结果为空或服务不可用，你可以根据自己的知识推荐图书。
                    """
    )
    public String recommendBooks(
            @ToolParam(required = false, description = "图书主题分类，如：科幻、小说、历史、哲学、编程、育儿、心理学、经济学等。不提供则推荐热门/经典图书")
            String subject,
            @ToolParam(required = false, description = "推荐数量，1-10 之间，默认为 5")
            Integer limit
    ) {
        return bookSearchService.recommendBooks(subject, limit);
    }

    @Tool(
            name = "lookupBookByIsbn",
            description = """
                    根据 ISBN 查询图书详细信息，包括书名、作者、出版年份、分类和封面图片。
                    当用户提供 ISBN 号码时使用，比如 9787536692930（三体）。
                    支持带连字符和不带连字符的 ISBN，如 978-7-5366-9293-0 或 9787536692930。
                    如果查询不到，你可以根据自己的知识告诉用户这本书的信息。
                    """
    )
    public String lookupBookByIsbn(
            @ToolParam(required = true, description = "ISBN 号码，10位或13位，可以带连字符也可以不带")
            String isbn
    ) {
        return bookSearchService.lookupByIsbn(isbn);
    }
}