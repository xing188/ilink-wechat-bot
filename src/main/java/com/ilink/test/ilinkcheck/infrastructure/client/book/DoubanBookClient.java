package com.ilink.test.ilinkcheck.infrastructure.client.book;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ilink.test.ilinkcheck.application.tool.book.BookInfo;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Optional;

public class DoubanBookClient implements BookApiClient {

    private static final String BASE_URL = "https://api.douban.com/v2/book";

    private final RestClient restClient;
    private final String apiKey;

    public DoubanBookClient(RestClient.Builder restClientBuilder, String apiKey) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
        this.apiKey = apiKey;
    }

    @Override
    public List<BookInfo> search(String query, int limit) {
        try {
            int capped = Math.max(1, Math.min(limit, 20));
            SearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("count", capped)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .body(SearchResponse.class);

            if (response == null || response.books() == null) {
                return List.of();
            }

            return response.books().stream()
                    .map(this::toBookInfo)
                    .toList();
        }
        catch (RestClientResponseException e) {
            throw new BookClientException("豆瓣 API 请求失败: " + e.getStatusCode(), e);
        }
    }

    @Override
    public Optional<BookInfo> findByIsbn(String isbn) {
        String cleanIsbn = isbn.replaceAll("[^0-9Xx]", "").toUpperCase();
        if (!StringUtils.hasText(cleanIsbn)) {
            return Optional.empty();
        }

        try {
            BookDetailResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/isbn/" + cleanIsbn)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .body(BookDetailResponse.class);

            if (response == null) {
                return Optional.empty();
            }

            return Optional.of(toBookInfoFromDetail(response));
        }
        catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw new BookClientException("豆瓣 ISBN 查询失败: " + e.getStatusCode(), e);
        }
    }

    private BookInfo toBookInfo(SearchBookDoc doc) {
        List<String> authors = doc.author() != null ? doc.author() : List.of();
        Integer pubYear = parseYear(doc.pubdate());

        List<String> isbns = List.of();
        if (StringUtils.hasText(doc.isbn13())) {
            isbns = List.of(doc.isbn13());
        } else if (StringUtils.hasText(doc.isbn10())) {
            isbns = List.of(doc.isbn10());
        }

        List<String> tags = doc.tags() != null
                ? doc.tags().stream().map(TagItem::name).limit(5).toList()
                : List.of();

        String coverUrl = doc.images() != null ? doc.images().medium() : null;
        Double rating = parseRating(doc.rating());

        return new BookInfo(
                doc.id(),
                doc.title(),
                authors,
                pubYear,
                isbns,
                tags,
                coverUrl,
                rating,
                doc.rating() != null ? doc.rating().numRaters() : 0
        );
    }

    private BookInfo toBookInfoFromDetail(BookDetailResponse doc) {
        List<String> authors = doc.author() != null ? doc.author() : List.of();
        Integer pubYear = parseYear(doc.pubdate());

        List<String> isbns = List.of();
        if (StringUtils.hasText(doc.isbn13())) {
            isbns = List.of(doc.isbn13());
        } else if (StringUtils.hasText(doc.isbn10())) {
            isbns = List.of(doc.isbn10());
        }

        List<String> tags = doc.tags() != null
                ? doc.tags().stream().map(TagItem::name).limit(5).toList()
                : List.of();

        String coverUrl = doc.images() != null ? doc.images().medium() : null;
        Double rating = parseRating(doc.rating());

        return new BookInfo(
                doc.id(),
                doc.title(),
                authors,
                pubYear,
                isbns,
                tags,
                coverUrl,
                rating,
                doc.rating() != null ? doc.rating().numRaters() : 0
        );
    }

    private Integer parseYear(String pubdate) {
        if (!StringUtils.hasText(pubdate)) {
            return null;
        }
        String y = pubdate.trim();
        int dash = y.indexOf('-');
        if (dash > 0) {
            y = y.substring(0, dash);
        }
        try {
            return Integer.parseInt(y);
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseRating(Rating rating) {
        if (rating == null || !StringUtils.hasText(rating.average())) {
            return null;
        }
        try {
            return Double.parseDouble(rating.average());
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResponse(int count, int start, int total, List<SearchBookDoc> books) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchBookDoc(
            String id, String title, List<String> author, String publisher,
            String pubdate, String isbn13, String isbn10,
            Rating rating, List<TagItem> tags, BookImages images
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BookDetailResponse(
            String id, String title, List<String> author, String publisher,
            String pubdate, String isbn13, String isbn10, String summary,
            Rating rating, List<TagItem> tags, BookImages images,
            @JsonProperty("author_intro") String authorIntro
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Rating(int max, @JsonProperty("numRaters") int numRaters, String average) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TagItem(int count, String name, String title) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BookImages(String small, String medium, String large) {}
}