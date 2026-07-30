package com.ilink.test.ilinkcheck.infrastructure.client.github;

import com.ilink.test.ilinkcheck.application.agent.dto.github.RepoInfo;
import com.ilink.test.ilinkcheck.application.agent.dto.github.TrendingRepo;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.GitHubRepoProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * GitHubRepoProvider Mock 实现。
 * 在人1的正式实现就绪前，返回模拟数据供人3测试。
 * 激活方式：application.yml 中设置 spring.profiles.active=dev 或在启动参数加 --spring.profiles.active=mock
 */
@Component
@Profile("mock")
public class MockGitHubRepoProvider implements GitHubRepoProvider {

    @Override
    public RepoInfo fetchRepo(String owner, String repo) {
        if ("lodash".equals(owner) && "lodash".equals(repo)) {
            return new RepoInfo(
                    "lodash", "lodash",
                    "A modern JavaScript utility library delivering modularity, performance & extras.",
                    "JavaScript", 60000, 7000,
                    """
                    Lodash makes JavaScript easier by taking the hassle out of working with arrays, numbers, objects, strings, etc.
                    \n## Architecture\nLodash 采用模块化设计，每个函数独立放在 modules/ 目录下，通过组合函数实现复杂功能。核心模块包括：Array、Collection、Function、Lang、Math、Number、Object、Seq、String、Util。
                    \n## Usage\n`_.chunk(array, size)` — 将数组拆分成多个 size 长度的块\n`_.debounce(func, wait)` — 防抖函数\n`_.deepClone(value)` — 深拷贝""",
                    "JavaScript, Node.js"
            );
        }
        if ("vuejs".equals(owner) && "core".equals(repo)) {
            return new RepoInfo(
                    "vuejs", "core",
                    "Vue.js 是用于构建用户界面的渐进式 JavaScript 框架。",
                    "TypeScript", 45000, 8000,
                    """
                    Vue.js 是一个用于构建用户界面的渐进式框架。
                    \n## Architecture\nVue 3 采用 Monorepo 管理，核心包包括：\n- `reactivity` — 独立于框架的响应式系统\n- `runtime-core` — 运行时核心（虚拟 DOM、组件、生命周期）\n- `compiler-core` — 编译器核心（模板编译优化）\n- `vue` — 完整构建入口
                    \n## Data Flow\n组件树 → 响应式状态 → 虚拟 DOM Diff → 高效 DOM 更新""",
                    "TypeScript, Monorepo, Vite"
            );
        }
        // 通用 mock
        return new RepoInfo(
                owner, repo,
                "A modern open-source project built with best practices. (" + owner + "/" + repo + ")",
                "TypeScript", 1000 + (owner.hashCode() % 5000),
                100 + (repo.hashCode() % 1000),
                "该项目致力于提供高效、可靠的解决方案。采用模块化架构设计，核心模块职责清晰，通过插件化机制支持功能扩展。\\n\\n## Architecture\\n整体架构分为三层：核心层（提供基础能力）、扩展层（插件机制）、应用层（用户接口）。数据流采用事件驱动模式，各模块之间通过消息队列解耦。",
                "TypeScript, React, Node.js"
        );
    }

    @Override
    public List<TrendingRepo> fetchTrending(String language, String since) {
        return List.of(
                new TrendingRepo("microsoft", "vscode", "Visual Studio Code - Open Source IDE", "TypeScript", 250, "https://github.com/microsoft/vscode"),
                new TrendingRepo("facebook", "react", "A declarative, efficient, and flexible JavaScript library for building user interfaces.", "JavaScript", 180, "https://github.com/facebook/react"),
                new TrendingRepo("torvalds", "linux", "Linux kernel source tree", "C", 150, "https://github.com/torvalds/linux"),
                new TrendingRepo("tailwindlabs", "tailwindcss", "A utility-first CSS framework for rapid UI development.", "JavaScript", 120, "https://github.com/tailwindlabs/tailwindcss"),
                new TrendingRepo("rust-lang", "rust", "Empowering everyone to build reliable and efficient software.", "Rust", 100, "https://github.com/rust-lang/rust")
        );
    }
}
