package com.ilink.test.ilinkcheck.application.agent.spi.rag;

import com.ilink.test.ilinkcheck.application.agent.dto.review.ReviewSuggestion;

import java.util.List;

public interface StyleGuideRetriever {

    List<ReviewSuggestion> check(String draftText);
}
