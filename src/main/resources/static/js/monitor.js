(function () {
    "use strict";

    const MAX_CONVS = 40;
    const MAX_LOG_LINES = 10;

    const convList = document.getElementById("convList");
    const convCountEl = document.getElementById("convCount");
    const eventCountEl = document.getElementById("eventCount");
    const runCountEl = document.getElementById("runCount");
    const waitCountEl = document.getElementById("waitCount");
    const doneCountEl = document.getElementById("doneCount");
    const connBox = document.getElementById("connBox");
    const connText = document.getElementById("connText");
    const clearBtn = document.getElementById("clearBtn");

    const TASK_LABEL = {
        GITHUB_REPO_DIGEST: "仓库速读",
        TECH_ROADMAP: "学习路线",
        DOC_REVIEW: "文档审稿",
        DAILY_DIGEST_SUBSCRIBE: "晨报订阅"
    };

    const convs = new Map(); // conversationId -> {events: []}
    let totalEvents = 0;

    function esc(text) {
        return String(text == null ? "" : text)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }

    function shortId(id) {
        if (!id) return "-";
        return id.length > 26 ? id.slice(0, 26) + "…" : id;
    }

    function timeStr(iso) {
        if (!iso) return "--:--:--";
        const d = new Date(iso);
        const p = (n) => String(n).padStart(2, "0");
        return p(d.getHours()) + ":" + p(d.getMinutes()) + ":" + p(d.getSeconds());
    }

    function elapsedText(conv) {
        const first = conv.firstTs ? new Date(conv.firstTs).getTime() : Date.now();
        const last = conv.lastTs ? new Date(conv.lastTs).getTime() : Date.now();
        const sec = Math.max(0, Math.round((last - first) / 1000));
        if (sec < 60) return sec + "s";
        const m = Math.floor(sec / 60);
        return m + "m" + (sec % 60) + "s";
    }

    function statusOf(conv) {
        const last = conv.events[conv.events.length - 1];
        if (!last) return ["run", "运行中"];
        switch (last.event) {
            case "finish": return ["done", "✅ 完成"];
            case "cancel": return ["cancel", "✖ 已取消"];
            case "fail": return ["fail", "⚠ 失败"];
            case "wait-user-input": return ["wait", "⏸ 等待输入"];
            default: return ["run", "🟢 运行中"];
        }
    }

    function taskClass(taskType) {
        return taskType && TASK_LABEL[taskType] ? taskType : "null";
    }

    function eventClass(name) {
        if (["translate", "restart", "expand-directory", "route-jump"].includes(name)) return "ev-translate";
        if (name === "start") return "ev-start";
        if (name === "phase") return "ev-phase";
        if (name === "wait-user-input") return "ev-wait-user-input";
        if (name === "resume") return "ev-resume";
        if (name === "finish") return "ev-finish";
        if (name === "cancel") return "ev-cancel";
        if (name === "fail") return "ev-fail";
        return "ev-phase";
    }

    function eventLabel(name) {
        return {
            start: "start",
            phase: "phase",
            "wait-user-input": "wait",
            resume: "resume",
            finish: "finish",
            cancel: "cancel",
            fail: "fail",
            translate: "translate",
            restart: "restart",
            "expand-directory": "dir",
            "route-jump": "jump"
        }[name] || name;
    }

    function buildContextChips(conv) {
        const last = conv.events[conv.events.length - 1];
        if (!last) return "";
        const chips = [];
        if (last.topic) chips.push(`<span class="ctx-chip"><b>主题</b>${esc(last.topic)}</span>`);
        if (last.repository) chips.push(`<span class="ctx-chip"><b>仓库</b>${esc(last.repository)}</span>`);
        if (last.draft) chips.push(`<span class="ctx-chip"><b>草稿</b>${esc(last.draft)}</span>`);
        if (last.scheduleText) chips.push(`<span class="ctx-chip"><b>计划</b>${esc(last.scheduleText)}</span>`);
        if (!last.userMessage && chips.length === 0) return "";
        return `<div class="conv-ctx">${chips.join("")}</div>`;
    }

    function buildLog(conv) {
        const lines = conv.events.slice(-MAX_LOG_LINES).map((ev) => {
            const phase = ev.phase ? `<span class="phase-tag">${esc(ev.phase)}</span>` : "";
            let msg = "";
            if (ev.event === "start" || ev.event === "resume" || ev.event === "restart"
                || ev.event === "translate" || ev.event === "expand-directory") {
                if (ev.userMessage) msg = `<span class="log-msg user">用户：${esc(ev.userMessage)}</span>`;
            } else if (ev.event === "phase") {
                if (ev.reply) msg = `<span class="log-msg reply">${esc(ev.reply)}</span>`;
            } else if (ev.event === "wait-user-input") {
                if (ev.reply) msg = `<span class="log-msg reply">${esc(ev.reply)}</span>`;
            } else if (ev.event === "finish" || ev.event === "cancel" || ev.event === "fail") {
                if (ev.reply) msg = `<span class="log-msg reply">${esc(ev.reply)}</span>`;
            }
            return `<div class="log-line">
                <span class="log-time">${timeStr(ev.timestamp)}</span>
                <span class="event-badge ${eventClass(ev.event)}">${esc(eventLabel(ev.event))}</span>
                ${phase}
                ${msg || ""}
            </div>`;
        }).join("");
        return `<div class="conv-log">${lines}</div>`;
    }

    function upsertCard(conv) {
        const [statusCls, statusLabel] = statusOf(conv);
        let card = document.getElementById("card-" + conv.id);
        const isNew = !card;
        if (isNew) {
            card = document.createElement("div");
            card.id = "card-" + conv.id;
            card.className = "conv-card";
            convList.prepend(card);
        }

        const last = conv.events[conv.events.length - 1];
        const taskType = last.taskType;
        card.innerHTML = `
            <div class="conv-head">
                <span class="conv-id">${esc(shortId(conv.id))}</span>
                <span class="task-badge task-${taskClass(taskType)}">${esc(TASK_LABEL[taskType] || taskType || "未知")}</span>
                <span class="status-pill status-${statusCls}">${statusLabel}</span>
            </div>
            ${buildContextChips(conv)}
            ${buildLog(conv)}
            <div class="conv-foot">
                <span>事件 ${conv.events.length}</span>
                <span class="elapsed">⏱ ${elapsedText(conv)}</span>
            </div>`;

        if (isNew) {
            while (convs.size > MAX_CONVS) {
                const oldest = convs.values().next().value;
                const oldCard = document.getElementById("card-" + oldest.id);
                if (oldCard) oldCard.remove();
                convs.delete(oldest.id);
            }
        }
    }

    function updateStats() {
        let run = 0, wait = 0, done = 0;
        for (const c of convs.values()) {
            const [cls] = statusOf(c);
            if (cls === "run") run++;
            else if (cls === "wait") wait++;
            else done++;
        }
        convCountEl.textContent = convs.size;
        eventCountEl.textContent = totalEvents;
        runCountEl.textContent = run;
        waitCountEl.textContent = wait;
        doneCountEl.textContent = done;
    }

    function onAgentEvent(ev) {
        totalEvents++;
        let conv = convs.get(ev.conversationId);
        if (!conv) {
            conv = { id: ev.conversationId, events: [], firstTs: ev.timestamp };
            convs.set(ev.conversationId, conv);
        }
        conv.events.push(ev);
        conv.lastTs = ev.timestamp;
        upsertCard(conv);
        updateStats();
    }

    function setConn(state) {
        connBox.classList.remove("online", "offline");
        if (state === "online") {
            connBox.classList.add("online");
            connText.textContent = "已连接 · 实时";
        } else if (state === "offline") {
            connBox.classList.add("offline");
            connText.textContent = "连接断开，重连中…";
        } else {
            connText.textContent = "连接中…";
        }
    }

    let es = null;
    function connect() {
        if (es) es.close();
        setConn("connecting");
        es = new EventSource("/api/agent/events");
        es.addEventListener("agent", (e) => {
            try {
                onAgentEvent(JSON.parse(e.data));
            } catch (err) {
                console.error("parse event failed", err);
            }
        });
        es.onopen = () => setConn("online");
        es.onerror = () => {
            setConn("offline");
            setTimeout(connect, 2000);
        };
    }

    clearBtn.addEventListener("click", () => {
        convs.clear();
        totalEvents = 0;
        convList.innerHTML = "";
        updateStats();
    });

    connect();
})();
