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
  modelPicker:             document.getElementById("modelPicker"),
  modelPickerList:         document.getElementById("modelPickerList"),
  stopBtn:                 document.getElementById("stopBtn"),
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
  return [list.length, last.id || "", last.status || "", last.createdAt || "", last.content || ""].join("|");
}

function scrollChatToBottom() {
  const applyScroll = () => { el.chatLog.scrollTop = el.chatLog.scrollHeight; };
  applyScroll();
  window.requestAnimationFrame(() => { applyScroll(); window.requestAnimationFrame(applyScroll); });
}

function resolveActiveModelName() {
  if (state.runtime?.activeModelName) return state.runtime.activeModelName;
  const configs = state.config?.apiConfigs || [];
  if (!configs.length) return "未配置模型";
  const index = Math.min(Math.max(state.config?.preferredApiIndex || 0, 0), configs.length - 1);
  return configs[index]?.name?.trim() || `接口 ${index + 1}`;
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
  el.systemPromptInput.value        = state.config.systemPrompt        || "";
  el.screenshotDirectoryInput.value  = state.config.screenshotDirectory || "";
  el.scrollUpHotkeyInput.value      = state.config.scrollUpHotkey      || "ALT+UP";
  el.scrollDownHotkeyInput.value    = state.config.scrollDownHotkey    || "ALT+DOWN";
  el.modelSwitchHotkeyInput.value   = state.config.modelSwitchHotkey   || "ALT+M";
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
    fallbackIndexes:     state.config?.fallbackIndexes   || [],
  };
}

/* ---------- 运行时状态 ---------- */
function applyRuntime(runtime) {
  if (!runtime) return;
  state.runtime = runtime;

  const lampState = runtime.lampState || "OFF";
  el.statusLamp.className = `lamp lamp-${lampState === "GREEN" ? "green" : lampState === "YELLOW" ? "yellow" : "off"}`;
  el.statusMessage.textContent = runtime.statusMessage || "";

  const busy = !!runtime.aiBusy;
  el.pendingLabel.textContent = busy ? "AI 处理中…" : "";
  el.pendingLabel.classList.toggle("is-busy", busy);
  el.activeModelDisplay.textContent = resolveActiveModelName();

  // 停止按钮：只在 AI 回答中才可点
  el.stopBtn.disabled = !busy;

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
const AI_LOADING_HTML = `<span class="ai-loading"><span></span><span></span><span></span></span>`;

function renderMessageContent(message) {
  if (message.role === "user") {
    return `<p>${escapeHtml(message.content || "")}</p>`;
  }
  if (message.status === "pending" && !message.content) {
    const hint = message.streamingHint
      ? `<span class="streaming-hint">${escapeHtml(message.streamingHint)}</span>`
      : "";
    return `${AI_LOADING_HTML}${hint}`;
  }
  return renderMarkdown(message.content || "");
}

function resolveRoleLabel(message) {
  if (message.role === "user") return "你";
  // AI 气泡：优先显示实际回答的模型名
  return escapeHtml(message.modelName || "AI");
}

function renderHistory() {
  const list = state.history || [];
  const nextTailKey = buildHistoryTailKey(list);
  const shouldForceScroll = nextTailKey !== state.lastHistoryTailKey;

  if (!list.length) {
    el.chatLog.innerHTML = `
      <div class="empty-state">
        <div class="empty-state-graphic" aria-hidden="true"></div>
        <span class="empty-state-title">还没有对话记录</span>
        <span class="empty-state-subtitle">截图后将自动在此显示问答内容</span>
      </div>`;
    state.lastHistoryTailKey = "";
    return;
  }

  el.chatLog.innerHTML = list.map((message) => {
    const statusClass = message.status === "error" ? " is-error" : "";
    const screenshot  = message.screenshotPath
      ? `<div class="chat-screenshot">${escapeHtml(message.screenshotPath)}</div>`
      : "";
    return `
      <article class="chat-item role-${message.role}${statusClass}" data-id="${escapeHtml(message.id || "")}">
        <div class="chat-meta">
          <span class="chat-role">${resolveRoleLabel(message)}</span>
          <span>${formatTime(message.createdAt)}</span>
        </div>
        <div class="chat-content">${renderMessageContent(message)}</div>
        ${screenshot}
      </article>`;
  }).join("");

  state.lastHistoryTailKey = nextTailKey;
  if (shouldForceScroll) scrollChatToBottom();
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
      const payload = typeof data.payload === "string" ? JSON.parse(data.payload) : data.payload;
      applyRuntime(payload);
    } catch (e) {
      console.warn("[SSE] state 解析失败", e, event.data);
    }
  });

  source.addEventListener("history", (event) => {
    try {
      const data    = JSON.parse(event.data);
      const payload = typeof data.payload === "string" ? JSON.parse(data.payload) : data.payload;

      const prev = state.history;
      state.history = payload;

      // 流式：最后一条是 pending assistant，只更新该节点内容
      if (prev.length === payload.length && payload.length > 0) {
        const lastNew = payload[payload.length - 1];
        if (lastNew.role === "assistant" && lastNew.status === "pending") {
          const lastEl    = el.chatLog.querySelector(`[data-id="${lastNew.id}"]`);
          const contentEl = lastEl?.querySelector(".chat-content");
          if (contentEl) {
            if (lastNew.content) {
              contentEl.innerHTML = renderMarkdown(lastNew.content);
            } else {
              const hint = lastNew.streamingHint
                ? `<span class="streaming-hint">${escapeHtml(lastNew.streamingHint)}</span>`
                : "";
              contentEl.innerHTML = `${AI_LOADING_HTML}${hint}`;
            }
            scrollChatToBottom();
            return;
          }
        }
      }

      renderHistory();
    } catch (e) {
      console.warn("[SSE] history 解析失败", e, event.data);
    }
  });

  source.onerror = () => { source.close(); window.setTimeout(connectEvents, 2000); };
}

/* ---------- 轮询兜底 ---------- */
let pollTimer = null;
function startPolling() {
  if (pollTimer) return;
  pollTimer = window.setInterval(async () => {
    try { applyRuntime(await api("/api/state")); } catch (_) {}
  }, 1000);
}

/* ---------- 模型选择面板（含降级勾选） ---------- */
const PICK_CHECK_SVG = `<svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M2 7l4 4 6-7" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>`;

function buildModelPicker() {
  const configs      = state.config?.apiConfigs || [];
  const currentIndex = state.config?.preferredApiIndex ?? 0;
  const fallbackIdxs = new Set(state.config?.fallbackIndexes || []);

  el.modelPickerList.innerHTML = "";
  configs.forEach((endpoint, i) => {
    const hasContent  = endpoint.name?.trim() || endpoint.model?.trim();
    const displayName = endpoint.name?.trim()  || `接口 ${i + 1}`;
    const displayModel= endpoint.model?.trim() || "未配置";
    const isActive    = i === currentIndex;
    const isFallback  = fallbackIdxs.has(i);

    const row = document.createElement("div");
    row.className = "picker-row" + (isActive ? " is-active" : "") + (!hasContent ? " is-disabled" : "");

    // 左侧：主选按钮
    const selectBtn = document.createElement("button");
    selectBtn.type = "button";
    selectBtn.className = "picker-select-btn";
    selectBtn.disabled = !hasContent;
    selectBtn.innerHTML = `
      <span class="picker-check">${isActive ? PICK_CHECK_SVG : ""}</span>
      <span class="picker-info">
        <span class="picker-name">${escapeHtml(displayName)}</span>
        <span class="picker-model">${escapeHtml(displayModel)}</span>
      </span>`;
    selectBtn.addEventListener("click", async () => {
      closePicker();
      try {
        state.config = await api("/api/model/select", {
          method: "POST",
          body: JSON.stringify({ index: i }),
        });
        applyRuntime(await api("/api/state"));
        renderSettings();
      } catch (err) {
        window.alert(`选择模型失败：${err.message}`);
      }
    });

    // 右侧：降级勾选框
    const fallbackLabel = document.createElement("label");
    fallbackLabel.className = "picker-fallback-label";
    fallbackLabel.title = "加入降级列表";
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.className = "picker-fallback-cb";
    checkbox.checked = isFallback;
    checkbox.disabled = !hasContent;
    checkbox.addEventListener("change", async () => {
      const current = new Set(state.config?.fallbackIndexes || []);
      if (checkbox.checked) current.add(i); else current.delete(i);
      // 保持原顺序：按接口索引升序
      const sorted = [...current].sort((a, b) => a - b);
      try {
        state.config = await api("/api/model/fallback", {
          method: "POST",
          body: JSON.stringify({ fallbackIndexes: sorted }),
        });
      } catch (err) {
        checkbox.checked = !checkbox.checked;
        window.alert(`更新降级配置失败：${err.message}`);
      }
    });
    fallbackLabel.appendChild(checkbox);
    fallbackLabel.insertAdjacentHTML("beforeend", `<span class="picker-fallback-text">降级</span>`);

    row.appendChild(selectBtn);
    row.appendChild(fallbackLabel);
    el.modelPickerList.appendChild(row);
  });
}

function openPicker() {
  buildModelPicker();
  el.modelPicker.hidden = false;
  el.switchModelBtn.setAttribute("aria-expanded", "true");
}

function closePicker() {
  el.modelPicker.hidden = true;
  el.switchModelBtn.removeAttribute("aria-expanded");
}

/* ---------- 事件绑定 ---------- */
el.openSettingsBtn.addEventListener("click", async () => {
  try {
    const [config, runtime] = await Promise.all([api("/api/config"), api("/api/state")]);
    state.config = config;
    applyRuntime(runtime);
    renderSettings();
    el.settingsDialog.showModal();
  } catch (err) {
    window.alert(`读取设置失败：${err.message}`);
  }
});

el.clearHistoryBtn?.addEventListener("click", async () => {
  if (!window.confirm("确定要清空当前所有对话记录吗？")) return;
  try {
    await api("/api/history/clear", { method: "POST" });
    state.history = [];
    renderHistory();
    el.statusMessage.textContent = "对话记录已清空";
  } catch (err) {
    window.alert(`清空失败：${err.message}`);
  }
});

el.stopBtn?.addEventListener("click", async () => {
  try {
    await api("/api/stop", { method: "POST" });
  } catch (err) {
    console.warn("停止失败", err);
  }
});

el.switchModelBtn?.addEventListener("click", (e) => {
  e.stopPropagation();
  el.modelPicker.hidden ? openPicker() : closePicker();
});

document.addEventListener("click", (e) => {
  if (el.modelPicker && !el.modelPicker.hidden) {
    if (!el.switchModelBtn.contains(e.target) && !el.modelPicker.contains(e.target)) {
      closePicker();
    }
  }
});

el.closeSettingsBtn.addEventListener("click", () => el.settingsDialog.close());

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
  } catch (err) {
    window.alert(`保存设置失败：${err.message}`);
  }
});

el.settingsForm.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && event.target?.tagName === "INPUT") event.preventDefault();
});

/* ---------- 启动 ---------- */
refreshAll()
  .then(() => { connectEvents(); startPolling(); })
  .catch((err) => {
    el.statusMessage.textContent = `初始化失败：${err.message}`;
    connectEvents();
    startPolling();
  });
