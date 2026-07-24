package com.ilink.test.ilinkcheck.infrastructure.client.book;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ilink.test.ilinkcheck.application.tool.book.BookInfo;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Optional;

public class OpenLibraryClient implements BookApiClient {

    private static final String BASE_URL = "https://openlibrary.org";

    private final RestClient restClient;

    public OpenLibraryClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
    }

    @Override
    public List<BookInfo> search(String query, int limit) {
        try {
            int capped = Math.max(1, Math.min(limit, 50));
            SearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search.json")
                            .queryParam("q", query)
                            .queryParam("limit", capped)
                            .queryParam("fields", "key,title,author_name,first_publish_year,isbn,subject,cover_i,ratings_count")
                            .build())
                    .retrieve()
                    .body(SearchResponse.class);

            if (response == null || response.docs() == null) {
                return List.of();
            }

            return response.docs().stream()
                    .map(this::toBookInfo)
                    .toList();
        }
        catch (RestClientResponseException e) {
            throw new BookClientException("OpenLibrary 搜索失败: " + e.getStatusCode(), e);
        }
    }

    @Override
    public Optional<BookInfo> findByIsbn(String isbn) {
        String cleanIsbn = isbn.replaceAll("[^0-9Xx]", "").toUpperCase();
        if (!StringUtils.hasText(cleanIsbn)) {
            return Optional.empty();
        }

        try {
            List<BookInfo> results = search("isbn:" + cleanIsbn, 1);
            if (!results.isEmpty()) {
                return Optional.of(results.getFirst());
            }
        }
        catch (Exception ignored) {
        }

        try {
            IsbnResponse isbnResp = restClient.get()
                    .uri("/isbn/" + cleanIsbn + ".json")
                    .retrieve()
                    .body(IsbnResponse.class);

            if (isbnResp != null && StringUtils.hasText(isbnResp.title())) {
                List<String> authors = isbnResp.authors() != null
                        ? isbnResp.authors().stream()
                                .filter(a -> StringUtils.hasText(a.name()))
                                .map(IsbnAuthor::name)
                                .toList()
                        : List.of();

                String coverUrl = isbnResp.covers() != null && !isbnResp.covers().isEmpty()
                        ? "https://covers.openlibrary.org/b/id/" + isbnResp.covers().getFirst() + "-M.jpg"
                        : null;

                return Optional.of(new BookInfo(
                        cleanIsbn,
                        isbnResp.title(),
                        authors,
                        null,
                        List.of(cleanIsbn),
                        isbnResp.subjects() != null ? isbnResp.subjects().stream().limit(5).toList() : List.of(),
                        coverUrl,
                        null,
                        0
                ));
            }
        }
        catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 404) {
                throw new BookClientException("OpenLibrary ISBN 查询失败: " + e.getStatusCode(), e);
            }
        }

        return Optional.empty();
    }

    private BookInfo toBookInfo(SearchDoc doc) {
        List<String> isbns = doc.isbn() != null
                ? doc.isbn().stream().limit(3).toList()
                : List.of();

        String coverUrl = doc.coverI() != null
                ? "https://covers.openlibrary.org/b/id/" + doc.coverI() + "-M.jpg"
                : null;

        return new BookInfo(
                doc.key() != null ? doc.key().replace("/works/", "") : null,
                doc.title(),
                doc.authorName() != null ? doc.authorName() : List.of(),
                doc.firstPublishYear(),
                isbns,
                doc.subject() != null ? doc.subject().stream().limit(5).toList() : List.of(),
                coverUrl,
                null,
                doc.ratingsCount() != null ? doc.ratingsCount() : 0
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResponse(long numFound, List<SearchDoc> docs) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchDoc(
            String key, String title,
            @JsonProperty("author_name") List<String> authorName,
            @JsonProperty("first_publish_year") Integer firstPublishYear,
            List<String> isbn, List<String> subject,
            @JsonProperty("cover_i") Integer coverI,
            @JsonProperty("ratings_count") Integer ratingsCount
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IsbnResponse(
            String title,
            List<IsbnAuthor> authors,
            List<Integer> covers,
            List<String> subjects
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IsbnAuthor(String key, String name) {}
}