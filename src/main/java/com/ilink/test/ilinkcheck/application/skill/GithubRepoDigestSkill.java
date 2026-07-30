package com.ilink.test.ilinkcheck.application.skill;

import com.ilink.test.ilinkcheck.application.agent.feature.github.GitHubRepoDigestService;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.AgentLoopService;
import org.springframework.stereotype.Component;

/**
 * GitHub 仓库速读 Skill。
 * 当用户说"帮我看看这个仓库""分析这个项目""这个 repo 是做什么的"时被触发。
 * 内部委托给 AgentLoopService 走完整的工作流（提取仓库 → 获取信息 → 生成摘要）。
 */
@Component
public class GithubRepoDigestSkill implements Skill {

    private final AgentLoopService agentLoopService;

    public GithubRepoDigestSkill(AgentLoopService agentLoopService) {
        this.agentLoopService = agentLoopService;
    }

    @Override
    public String id() {
        return "github-repo-digest";
    }

    @Override
    public String name() {
        return "GitHub 仓库速读";
    }

    @Override
    public String description() {
        return "分析 GitHub 仓库的 README、Star、Fork、技术栈等信息，生成简洁的速读摘要。用户说“帮我看看这个仓库/分析这个项目/这个 repo 是做什么的”时使用。";
    }

    @Override
    public String execute(String input) {
        return agentLoopService.tryHandle("skill:" + id(), input)
                .orElse("请提供 GitHub 仓库链接或 owner/repo 格式的仓库名。");
    }
}
