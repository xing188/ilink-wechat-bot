(function () {
    "use strict";

    const $ = (id) => document.getElementById(id);
    const draftInput = $("draftInput");
    const reviewBtn = $("reviewBtn");
    const sampleBtn = $("sampleBtn");
    const clearBtn = $("clearBtn");
    const charCount = $("charCount");
    const resultBody = $("resultBody");
    const tabs = Array.from(document.querySelectorAll(".tab"));
    const copyFixBtn = $("copyFixBtn");
    const exportBtn = $("exportBtn");

    const TYPE_LABEL = {
        "spacing": "排版间距",
        "code-block": "代码块",
        "terminology": "术语大小写",
        "other": "其它"
    };

    const SAMPLE_DRAFT = [
        "Redis延迟排查指南",
        "",
        "这篇文章主要讨论 redis延迟 的排查路径，以及怎么验证瓶颈。",
        "",
        "运行以下命令：",
        "```",
        "INFO commandstats",
        "```",
        "",
        "使用redis-cli可以连接服务器，检查macos系统上的redis配置，调用api接口测试。"
    ].join("\n");

    let latestReport = null;
    let latestFixed = null;

    // ── 输入区 ──
    draftInput.addEventListener("input", () => {
        charCount.textContent = draftInput.value.length;
    });

    sampleBtn.addEventListener("click", () => {
        draftInput.value = SAMPLE_DRAFT;
        charCount.textContent = draftInput.value.length;
        draftInput.focus();
    });

    clearBtn.addEventListener("click", () => {
        draftInput.value = "";
        charCount.textContent = "0";
        resetResult();
    });

    reviewBtn.addEventListener("click", doReview);

    tabs.forEach((tab) => {
        tab.addEventListener("click", () => switchTab(tab.dataset.tab));
    });

    copyFixBtn.addEventListener("click", () => {
        if (latestFixed == null) return;
        navigator.clipboard.writeText(latestFixed).then(() => {
            flash(copyFixBtn, "已复制 ✓");
        }).catch(() => {
            window.prompt("复制失败，请手动复制：", latestFixed);
        });
    });

    exportBtn.addEventListener("click", exportReport);

    // 支持 Ctrl/Cmd + Enter 快速审稿
    draftInput.addEventListener("keydown", (e) => {
        if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
            e.preventDefault();
            doReview();
        }
    });

    // ── 审稿 ──
    async function doReview() {
        const draft = draftInput.value.trim();
        if (!draft) {
            toast("请先粘贴草稿内容");
            return;
        }
        setBusy(true);
        try {
            const [reportRes, fixRes] = await Promise.all([
                postJson("/api/review", { draftText: draft }),
                postJson("/api/review/fix", { draftText: draft })
            ]);
            latestReport = reportRes;
            latestFixed = fixRes.fixedText;
            copyFixBtn.classList.remove("hidden");
            exportBtn.classList.remove("hidden");
            enableTab("diff", latestFixed != null && latestFixed.trim() !== "");
            switchTab("report");
            renderReport(reportRes);
        } catch (err) {
            toast("审稿失败：" + (err.message || "网络异常"));
        } finally {
            setBusy(false);
        }
    }

    function renderReport(report) {
        const suggestions = report.suggestions || [];
        const banner = report.passed
            ? `<div class="summary-banner pass">✅ ${esc(report.summary)}</div>`
            : `<div class="summary-banner fail">⚠️ ${esc(report.summary)}</div>`;

        const stats = statChips(suggestions);
        const cards = suggestions.map(renderSuggestion).join("") ||
            '<div class="empty-state" style="justify-content:flex-start"><p>未发现具体问题，可切到「修改稿 Diff」查看自动修复结果。</p></div>';

        resultBody.innerHTML = banner + stats + cards;
    }

    function statChips(suggestions) {
        const count = (type) => suggestions.filter((s) => s.type === type).length;
        const chips = [
            ["全部", suggestions.length],
            ["排版间距", count("spacing")],
            ["代码块", count("code-block")],
            ["术语大小写", count("terminology")]
        ];
        return `<div class="stat-row">` + chips
            .map(([label, n]) => `<span class="stat-chip">${label} <b>${n}</b></span>`)
            .join("") + `</div>`;
    }

    function renderSuggestion(s) {
        const type = s.type || "other";
        const badge = `<span class="type-badge type-${cssClass(type)}">${esc(TYPE_LABEL[type] || type)}</span>`;
        const location = s.location && s.location.trim()
            ? `<span class="loc" style="color:var(--text-3);font-size:12px">@ ${esc(s.location)}</span>`
            : "";
        const suggestion = s.suggestion && s.suggestion.trim()
            ? `<div class="suggestion-sug">💡 ${esc(s.suggestion)}</div>`
            : "";
        return `<div class="suggestion-card">
            <div class="suggestion-top">${badge}${location}</div>
            <div class="suggestion-msg">${esc(s.message)}</div>
            ${suggestion}
        </div>`;
    }

    // ── Diff 渲染 ──
    function renderDiff(original, fixed) {
        const ops = diffLines(original, fixed);
        const total = ops.length;
        const added = ops.filter((o) => o.type === "add").length;
        const removed = ops.filter((o) => o.type === "del").length;

        let html = `<div class="diff-wrap">
            <div class="diff-toolbar">
                <span>修改 ${added} 行 / 删除 ${removed} 行 · 共 ${total} 行</span>
            </div>
            <table class="diff-table">`;

        let aLine = 0, bLine = 0;
        for (const op of ops) {
            if (op.type === "same") {
                aLine++; bLine++;
                html += `<tr class="diff-same"><td class="ln">${aLine}</td><td class="ln">${bLine}</td><td>${esc(op.text)}</td></tr>`;
            } else if (op.type === "del") {
                aLine++;
                html += `<tr class="diff-del"><td class="ln">${aLine}</td><td class="ln">·</td><td>${esc(op.text)}</td></tr>`;
            } else {
                bLine++;
                html += `<tr class="diff-add"><td class="ln">·</td><td class="ln">${bLine}</td><td>${esc(op.text)}</td></tr>`;
            }
        }
        html += `</table></div>`;

        return `<div class="fixed-note">✅ 已按规范自动修复下方草稿，绿色=新增/修改，红色=删除。可直接复制使用。</div>` + html;
    }

    // 基于 LCS 的行级 diff（简单统一 diff）
    function diffLines(aText, bText) {
        const a = aText.split("\n");
        const b = bText.split("\n");
        const n = a.length, m = b.length;
        const dp = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0));
        for (let i = n - 1; i >= 0; i--) {
            for (let j = m - 1; j >= 0; j--) {
                dp[i][j] = a[i] === b[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }
        const ops = [];
        let i = 0, j = 0;
        while (i < n && j < m) {
            if (a[i] === b[j]) { ops.push({ type: "same", text: a[i] }); i++; j++; }
            else if (dp[i + 1][j] >= dp[i][j + 1]) { ops.push({ type: "del", text: a[i] }); i++; }
            else { ops.push({ type: "add", text: b[j] }); j++; }
        }
        while (i < n) { ops.push({ type: "del", text: a[i] }); i++; }
        while (j < m) { ops.push({ type: "add", text: b[j] }); j++; }
        return ops;
    }

    // ── 导出 docx ──
    async function exportReport() {
        if (!latestReport) return;
        const title = prompt("导出文件名：", "技术文档审稿报告");
        if (title == null) return;
        setBusy(true);
        try {
            const resp = await fetch("/api/review/export", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ title: title, reportText: latestReport.formattedReport })
            });
            if (!resp.ok) throw new Error("HTTP " + resp.status);
            const blob = await resp.blob();
            const url = URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = title + ".docx";
            a.click();
            URL.revokeObjectURL(url);
        } catch (err) {
            toast("导出失败：" + (err.message || "网络异常"));
        } finally {
            setBusy(false);
        }
    }

    // ── 工具 ──
    function switchTab(name) {
        tabs.forEach((t) => t.classList.toggle("active", t.dataset.tab === name));
        if (name === "report" && latestReport) {
            renderReport(latestReport);
        } else if (name === "diff") {
            if (latestFixed != null) {
                const original = draftInput.value;
                resultBody.innerHTML = renderDiff(original, latestFixed);
            } else {
                resultBody.innerHTML = '<div class="empty-state"><p>请先点击「开始审稿」生成修改稿。</p></div>';
            }
        }
    }

    function enableTab(name, enabled) {
        const tab = tabs.find((t) => t.dataset.tab === name);
        if (tab) tab.disabled = !enabled;
    }

    function resetResult() {
        latestReport = null;
        latestFixed = null;
        copyFixBtn.classList.add("hidden");
        exportBtn.classList.add("hidden");
        enableTab("diff", false);
        switchTab("report");
        resultBody.innerHTML = `<div class="empty-state">
            <div class="empty-icon"><svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.4">
                <path d="M14 3v4a1 1 0 0 0 1 1h4"/><path d="M17 21H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7l5 5v11a2 2 0 0 1-2 2z"/><path d="M12 11v6"/><path d="M9 14h6"/></svg></div>
            <p>在左侧粘贴草稿，点击「开始审稿」查看规范问题与修改建议</p>
        </div>`;
    }

    function setBusy(busy) {
        reviewBtn.disabled = busy;
        const label = reviewBtn.querySelector(".btn-label");
        if (busy) label.textContent = "审稿中…";
        else label.textContent = "开始审稿";
    }

    function flash(btn, text) {
        const old = btn.textContent;
        btn.textContent = text;
        setTimeout(() => { btn.textContent = old; }, 1200);
    }

    let toastTimer = null;
    function toast(msg) {
        let el = document.querySelector(".toast");
        if (!el) {
            el = document.createElement("div");
            el.className = "toast";
            el.style.cssText = "position:fixed;bottom:28px;left:50%;transform:translateX(-50%);background:rgba(17,24,39,.92);border:1px solid var(--panel-border);color:var(--text-1);padding:10px 18px;border-radius:10px;font-size:13px;z-index:99;box-shadow:0 8px 30px rgba(0,0,0,.4);";
            document.body.appendChild(el);
        }
        el.textContent = msg;
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => { el.style.display = "none"; }, 2200);
        el.style.display = "block";
    }

    async function postJson(url, body) {
        const resp = await fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body)
        });
        if (!resp.ok) throw new Error("HTTP " + resp.status);
        return resp.json();
    }

    function esc(text) {
        return String(text == null ? "" : text)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }

    function cssClass(type) {
        return ["spacing", "code-block", "terminology"].includes(type) ? type : "other";
    }
})();
