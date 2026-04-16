/* ===================================================
 *  SnapMuse — 前端逻辑
 * =================================================== */

const state = {
  config: null,
  runtime: null,
  history: [],
  lastHotkeyAt: 0,
  lastHistoryTailKey: "",
};

/* ---------- DOM 引用 ---------- */
const el = {
  statusLamp:              document.getElementById("statusLamp"),
  statusMessage:           document.getElementById("statusMessage"),
  pendingLabel:            document.getElementById("pendingLabel"),
  activeModelDisplay:      document.getElementById("activeModelDisplay"),
  switchModelBtn:          document.getElementById("switchModelBtn"),
  fallbackToggle:          document.getElementById("fallbackToggle"),
  chatLog:                 document.getElementById("chatLog"),
  clearHistoryBtn:         document.getElementById("clearHistoryBtn"),
  openSettingsBtn:         document.getElementById("openSettingsBtn"),
  closeSettingsBtn:        document.getElementById("closeSettingsBtn"),
  settingsDialog:          document.getElementById("settingsDialog"),
  settingsForm:            document.getElementById("settingsForm"),
  apiConfigList:           document.getElementById("apiConfigList"),
  systemPromptInput:       document.getElementById("systemPromptInput"),
  screenshotDirectoryInput:document.getElementById("screenshotDirectoryInput"),
  scrollUpHotkeyInput:     document.getElementById("scrollUpHotkeyInput"),
  scrollDownHotkeyInput:   document.getElementById("scrollDownHotkeyInput"),
  modelSwitchHotkeyInput:  document.getElementById("modelSwitchHotkeyInput"),
};

/* ---------- 工具函数 ---------- */
function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function formatTime(value) {
  if (!value) return "";
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}

function renderMarkdown(text) {
  if (!text) return "";
  try {
    return marked.parse(text, { breaks: true, gfm: true });
  } catch (_) {
    return escapeHtml(text);
  }
}

function buildHistoryTailKey(list) {
  if (!list?.length) return "";
  const last = list[list.length - 1];
  return [
    list.length,
    last.id || "",
    last.status || "",
    last.createdAt || "",
    last.content || "",
  ].join("|");
}

function scrollChatToBottom() {
  const applyScroll = () => {
    el.chatLog.scrollTop = el.chatLog.scrollHeight;
  };
  applyScroll();
  window.requestAnimationFrame(() => {
    applyScroll();
    window.requestAnimationFrame(applyScroll);
  });
}

function resolveActiveModelDisplay() {
  if (state.runtime?.activeModelDisplay) {
    return state.runtime.activeModelDisplay;
  }
  const configs = state.config?.apiConfigs || [];
  if (!configs.length) {
    return "未配置模型";
  }
  const index = Math.min(Math.max(state.config?.preferredApiIndex || 0, 0), configs.length - 1);
  const endpoint = configs[index] || {};
  const name = endpoint.name?.trim() || `模型 ${index + 1}`;
  const model = endpoint.model?.trim() || "未配置模型";
  return `${name} · ${model}`;
}

/* ---------- API ---------- */
async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  if (response.status === 204) return null;
  return response.json();
}

/* ---------- 设置面板渲染 ---------- */
function buildApiCard(index, endpoint = {}) {
  const wrapper = document.createElement("div");
  wrapper.className = "api-card";
  wrapper.innerHTML = `
    <label>
      <span>接口名称 ${index + 1}</span>
      <input type="text" name="api-name-${index}" value="${escapeHtml(endpoint.name || "")}">
    </label>
    <div class="api-card-grid">
      <label>
        <span>Base URL</span>
        <input type="text" name="api-base-${index}" value="${escapeHtml(endpoint.baseUrl || "")}">
      </label>
      <label>
        <span>API Key</span>
        <input type="password" name="api-key-${index}" value="${escapeHtml(endpoint.apiKey || "")}">
      </label>
      <label>
        <span>模型名</span>
        <input type="text" name="api-model-${index}" value="${escapeHtml(endpoint.model || "")}">
      </label>
    </div>
  `;
  return wrapper;
}

function renderSettings() {
  if (!state.config) return;
  el.apiConfigList.innerHTML = "";
  (state.config.apiConfigs || []).forEach((endpoint, index) => {
    el.apiConfigList.appendChild(buildApiCard(index, endpoint));
  });
  el.systemPromptInput.value       = state.config.systemPrompt        || "";
  el.screenshotDirectoryInput.value = state.config.screenshotDirectory || "";
  el.scrollUpHotkeyInput.value     = state.config.scrollUpHotkey      || "ALT+UP";
  el.scrollDownHotkeyInput.value   = state.config.scrollDownHotkey    || "ALT+DOWN";
  el.modelSwitchHotkeyInput.value  = state.config.modelSwitchHotkey   || "ALT+M";
}

function collectSettingsForm() {
  const valueOf = (name) =>
    el.settingsForm.querySelector(`[name="${name}"]`)?.value?.trim() || "";
  const slotCount = Math.max(state.config?.apiConfigs?.length || 0, 5);
  const apiConfigs = Array.from({ length: slotCount }).map((_, index) => ({
    name:    valueOf(`api-name-${index}`),
    baseUrl: valueOf(`api-base-${index}`),
    apiKey:  valueOf(`api-key-${index}`),
    model:   valueOf(`api-model-${index}`),
  }));
  return {
    apiConfigs,
    systemPrompt:        el.systemPromptInput.value.trim(),
    screenshotDirectory: el.screenshotDirectoryInput.value.trim(),
    scrollUpHotkey:      el.scrollUpHotkeyInput.value.trim(),
    scrollDownHotkey:    el.scrollDownHotkeyInput.value.trim(),
    modelSwitchHotkey:   el.modelSwitchHotkeyInput.value.trim(),
    preferredApiIndex:   state.config?.preferredApiIndex || 0,
    fallbackEnabled:     state.runtime?.fallbackEnabled ?? state.config?.fallbackEnabled ?? true,
  };
}

/* ---------- 运行时状态 ---------- */
function applyRuntime(runtime) {
  if (!runtime) return;
  state.runtime = runtime;

  // 指示灯
  const lampState = runtime.lampState || "OFF";
  const lampClass = lampState === "GREEN"
    ? "lamp-green"
    : lampState === "YELLOW"
      ? "lamp-yellow"
      : "lamp-off";
  el.statusLamp.className = `lamp ${lampClass}`;
  el.statusMessage.textContent = runtime.statusMessage || "";

  // AI 处理中标签
  const busy = !!runtime.aiBusy;
  el.pendingLabel.textContent = busy ? "AI 处理中…" : "";
  el.pendingLabel.classList.toggle("is-busy", busy);
  el.activeModelDisplay.textContent = resolveActiveModelDisplay();
  el.fallbackToggle.checked = !!runtime.fallbackEnabled;

  // 热键触发的滚动
  if (runtime.lastHotkeyAt && runtime.lastHotkeyAt !== state.lastHotkeyAt) {
    state.lastHotkeyAt = runtime.lastHotkeyAt;
    if (runtime.lastHotkeyDirection === "UP") {
      el.chatLog.scrollBy({ top: -220, behavior: "smooth" });
    } else if (runtime.lastHotkeyDirection === "DOWN") {
      el.chatLog.scrollBy({ top: 220, behavior: "smooth" });
    }
  }
}

/* ---------- 对话历史渲染 ---------- */
function renderHistory() {
  const list = state.history || [];
  const nextTailKey = buildHistoryTailKey(list);
  const shouldForceScroll = nextTailKey !== state.lastHistoryTailKey;

  if (!list.length) {
    el.chatLog.innerHTML = `
      <div class="empty-state">
        <span class="empty-state-icon">📸</span>
        <span>还没有对话记录</span>
        <span>截图后将自动在此显示问答内容</span>
      </div>`;
    state.lastHistoryTailKey = "";
    return;
  }

  el.chatLog.innerHTML = list.map((message) => {
    const isUser = message.role === "user";
    const roleLabel = isUser ? "你" : "AI";
    const statusClass = message.status === "error" ? " is-error" : "";
    const screenshot = message.screenshotPath
      ? `<div class="chat-screenshot">📷 ${escapeHtml(message.screenshotPath)}</div>`
      : "";
    // 用户消息保持纯文本，AI 消息渲染 Markdown
    const contentHtml = isUser
      ? `<p>${escapeHtml(message.content || "")}</p>`
      : renderMarkdown(message.content || "");

    return `
      <article class="chat-item role-${message.role}${statusClass}">
        <div class="chat-meta">
          <span class="chat-role">${roleLabel}</span>
          <span>${formatTime(message.createdAt)}</span>
        </div>
        <div class="chat-content">${contentHtml}</div>
        ${screenshot}
      </article>`;
  }).join("");

  state.lastHistoryTailKey = nextTailKey;

  if (shouldForceScroll) {
    scrollChatToBottom();
  }
}

/* ---------- 初始化加载 ---------- */
async function refreshAll() {
  const [config, runtime, history] = await Promise.all([
    api("/api/config"),
    api("/api/state"),
    api("/api/history"),
  ]);
  state.config  = config;
  state.history = history;
  renderSettings();
  applyRuntime(runtime);
  renderHistory();
}

/* ---------- SSE 实时推送 ---------- */
function connectEvents() {
  const source = new EventSource("/api/events");

  source.addEventListener("state", (event) => {
    try {
      const data    = JSON.parse(event.data);
      const payload = typeof data.payload === "string"
        ? JSON.parse(data.payload) : data.payload;
      applyRuntime(payload);
    } catch (e) {
      console.warn("[SSE] state 解析失败", e, event.data);
    }
  });

  source.addEventListener("history", (event) => {
    try {
      const data    = JSON.parse(event.data);
      const payload = typeof data.payload === "string"
        ? JSON.parse(data.payload) : data.payload;
      state.history = payload;
      renderHistory();
    } catch (e) {
      console.warn("[SSE] history 解析失败", e, event.data);
    }
  });

  source.onerror = () => {
    source.close();
    window.setTimeout(connectEvents, 2000);
  };
}

/* ---------- 轮询兜底 ---------- */
let pollTimer = null;
function startPolling() {
  if (pollTimer) return;
  pollTimer = window.setInterval(async () => {
    try {
      const runtime = await api("/api/state");
      applyRuntime(runtime);
    } catch (_) { /* 忽略 */ }
  }, 1000);
}

/* ---------- 事件绑定 ---------- */
el.openSettingsBtn.addEventListener("click", async () => {
  try {
    const [config, runtime] = await Promise.all([
      api("/api/config"),
      api("/api/state"),
    ]);
    state.config = config;
    applyRuntime(runtime);
    renderSettings();
    el.settingsDialog.showModal();
  } catch (error) {
    window.alert(`读取设置失败：${error.message}`);
  }
});

async function clearChatHistory() {
  if (!window.confirm("确定要清空当前所有对话记录吗？")) {
    return;
  }
  try {
    await api("/api/history/clear", { method: "POST" });
    state.history = [];
    renderHistory();
    el.statusMessage.textContent = "对话记录已清空";
  } catch (error) {
    window.alert(`清空失败：${error.message}`);
  }
}

if (el.clearHistoryBtn) {
  el.clearHistoryBtn.addEventListener("click", clearChatHistory);
}

if (el.switchModelBtn) {
  el.switchModelBtn.addEventListener("click", async () => {
    try {
      state.config = await api("/api/model/next", { method: "POST" });
      applyRuntime(await api("/api/state"));
      renderSettings();
    } catch (error) {
      window.alert(`切换模型失败：${error.message}`);
    }
  });
}

if (el.fallbackToggle) {
  el.fallbackToggle.addEventListener("change", async () => {
    const nextValue = el.fallbackToggle.checked;
    try {
      state.config = await api("/api/model/fallback", {
        method: "POST",
        body: JSON.stringify({ enabled: nextValue }),
      });
      applyRuntime(await api("/api/state"));
    } catch (error) {
      el.fallbackToggle.checked = !nextValue;
      window.alert(`切换降级开关失败：${error.message}`);
    }
  });
}

el.closeSettingsBtn.addEventListener("click", () => {
  el.settingsDialog.close();
});

// 点击 backdrop 关闭
el.settingsDialog.addEventListener("click", (e) => {
  if (e.target === el.settingsDialog) el.settingsDialog.close();
});

el.settingsForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    state.config = await api("/api/config", {
      method: "POST",
      body: JSON.stringify(collectSettingsForm()),
    });
    renderSettings();
    applyRuntime(await api("/api/state"));
    el.settingsDialog.close();
  } catch (error) {
    window.alert(`保存设置失败：${error.message}`);
  }
});

el.settingsForm.addEventListener("keydown", (event) => {
  const tagName = event.target?.tagName;
  if (event.key === "Enter" && tagName === "INPUT") {
    event.preventDefault();
  }
});

/* ---------- 启动 ---------- */
refreshAll()
  .then(() => {
    connectEvents();
    startPolling();
  })
  .catch((error) => {
    el.statusMessage.textContent = `初始化失败：${error.message}`;
    connectEvents();
    startPolling();
  });
