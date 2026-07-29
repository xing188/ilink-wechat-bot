package com.ilink.test.ilinkcheck.application.agent.dto.digest;

import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;

import java.util.List;

public record DigestContent(
        List<TrendingRepo> trendingRepos,
        List<DevToArticle> hotArticles,
        String date
) {}
