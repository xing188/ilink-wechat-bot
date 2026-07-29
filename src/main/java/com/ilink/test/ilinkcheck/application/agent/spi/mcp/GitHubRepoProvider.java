package com.ilink.test.ilinkcheck.application.agent.spi.mcp;

import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;

import java.util.List;

public interface GitHubRepoProvider {

    RepoInfo fetchRepo(String owner, String repo);

    List<TrendingRepo> fetchTrending(String language, String since);
}
