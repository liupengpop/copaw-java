<template>
  <div class="app-shell" :data-theme="resolvedTheme">
    <aside class="app-sidebar">
      <div class="sidebar-brand">
        <div class="brand-mark">Co</div>
        <span class="brand-title">CoPaw</span>
      </div>

      <div class="agent-switcher">
        <div class="sidebar-label">当前智能体 ({{ agentCount }})</div>
        <div class="agent-switcher__row">
          <el-select
            :value="selectedAgentId"
            size="small"
            filterable
            placeholder="选择智能体"
            style="flex: 1; min-width: 0"
            @change="onSelectAgent"
          >
            <el-option
              v-for="agent in agents"
              :key="agent.id"
              :label="agent.name"
              :value="agent.id"
            />
          </el-select>
          <el-button size="small" icon="el-icon-refresh" :loading="agentLoading" circle @click="loadAgents" />
        </div>
      </div>

      <nav class="sidebar-nav">
        <router-link to="/chat" class="nav-item" :class="{ 'is-active': isActive('/chat') }">
          <i class="el-icon-chat-dot-round"></i>
          <span>聊天</span>
        </router-link>

        <div class="nav-group-label">控制</div>
        <router-link to="/channels" class="nav-item" :class="{ 'is-active': isActive('/channels') }">
          <i class="el-icon-connection"></i>
          <span>频道</span>
        </router-link>
        <router-link to="/sessions" class="nav-item" :class="{ 'is-active': isActive('/sessions') }">
          <i class="el-icon-document"></i>
          <span>会话</span>
        </router-link>
        <router-link to="/cron" class="nav-item" :class="{ 'is-active': isActive('/cron') }">
          <i class="el-icon-alarm-clock"></i>
          <span>定时任务</span>
        </router-link>
        <router-link to="/heartbeat" class="nav-item" :class="{ 'is-active': isActive('/heartbeat') }">
          <i class="el-icon-odometer"></i>
          <span>心跳</span>
        </router-link>

        <div class="nav-group-label">工作区</div>
        <router-link to="/files" class="nav-item" :class="{ 'is-active': isActive('/files') }">
          <i class="el-icon-folder"></i>
          <span>文件</span>
        </router-link>
        <router-link to="/skills" class="nav-item" :class="{ 'is-active': isActive('/skills') }">
          <i class="el-icon-magic-stick"></i>
          <span>技能</span>
        </router-link>
        <router-link to="/tools" class="nav-item" :class="{ 'is-active': isActive('/tools') }">
          <i class="el-icon-cpu"></i>
          <span>工具</span>
        </router-link>
        <router-link to="/mcp" class="nav-item" :class="{ 'is-active': isActive('/mcp') }">
          <i class="el-icon-s-operation"></i>
          <span>MCP</span>
        </router-link>
        <router-link to="/runtime" class="nav-item" :class="{ 'is-active': isActive('/runtime') }">
          <i class="el-icon-setting"></i>
          <span>运行配置</span>
        </router-link>

        <div class="nav-group-label">设置</div>
        <router-link to="/agents" class="nav-item" :class="{ 'is-active': isActive('/agents') }">
          <i class="el-icon-user"></i>
          <span>智能体管理</span>
        </router-link>
        <router-link to="/models" class="nav-item" :class="{ 'is-active': isActive('/models') }">
          <i class="el-icon-monitor"></i>
          <span>模型</span>
        </router-link>
        <router-link to="/skillpool" class="nav-item" :class="{ 'is-active': isActive('/skillpool') }">
          <i class="el-icon-s-grid"></i>
          <span>技能池</span>
        </router-link>
        <router-link to="/env" class="nav-item" :class="{ 'is-active': isActive('/env') }">
          <i class="el-icon-key"></i>
          <span>环境变量</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <button class="theme-btn" :class="{ 'is-active': themePreference === 'light' }" @click="setTheme('light')">亮</button>
        <button class="theme-btn" :class="{ 'is-active': themePreference === 'dark' }" @click="setTheme('dark')">暗</button>
        <button class="theme-btn" :class="{ 'is-active': themePreference === 'system' }" @click="setTheme('system')">系统</button>
      </div>
    </aside>

    <div class="app-content">
      <div class="app-breadcrumb">
        <span class="breadcrumb-section">{{ currentSection }}</span>
        <span v-if="currentTitle" class="breadcrumb-sep">/</span>
        <span v-if="currentTitle" class="breadcrumb-page">{{ currentTitle }}</span>
      </div>
      <main class="app-main">
        <div class="main-scroll" :class="{ 'is-fullheight': isFullheightRoute }">
          <router-view />
        </div>
      </main>
    </div>
  </div>
</template>

<script>
import { agentsApi } from "@/services/api";
import { normalizeAgent } from "@/utils/format";

const THEME_KEY = "copaw-theme";

const SECTION_MAP = {
  "/chat": ["聊天", ""],
  "/channels": ["控制", "频道"],
  "/sessions": ["控制", "会话"],
  "/cron": ["控制", "定时任务"],
  "/heartbeat": ["控制", "心跳"],
  "/files": ["工作区", "文件"],
  "/skills": ["工作区", "技能"],
  "/tools": ["工作区", "工具"],
  "/mcp": ["工作区", "MCP"],
  "/runtime": ["工作区", "运行配置"],
  "/agents": ["设置", "智能体管理"],
  "/models": ["设置", "模型"],
  "/skillpool": ["设置", "技能池"],
  "/env": ["设置", "环境变量"]
};

export default {
  name: "App",
  data: function data() {
    return {
      themePreference: "system",
      systemPrefersDark: false,
      mediaQuery: null,
      agentLoading: false,
      agents: []
    };
  },
  computed: {
    resolvedTheme: function resolvedTheme() {
      if (this.themePreference === "light" || this.themePreference === "dark") {
        return this.themePreference;
      }
      return this.systemPrefersDark ? "dark" : "light";
    },
    selectedAgentId: function selectedAgentId() {
      return this.$store.state.selectedAgentId;
    },
    agentCount: function agentCount() {
      return this.agents.length;
    },
    currentSection: function currentSection() {
      const entry = SECTION_MAP[this.$route.path];
      return entry ? entry[0] : "CoPaw";
    },
    currentTitle: function currentTitle() {
      const entry = SECTION_MAP[this.$route.path];
      return entry ? entry[1] : (this.$route.meta && this.$route.meta.title) || "";
    },
    isFullheightRoute: function isFullheightRoute() {
      return this.$route.path === "/chat" || this.$route.path === "/sessions";
    }
  },
  watch: {
    resolvedTheme: function watchTheme() {
      this.applyTheme();
    }
  },
  created: function created() {
    try {
      this.themePreference = window.localStorage.getItem(THEME_KEY) || "system";
    } catch (e) {
      this.themePreference = "system";
    }
  },
  mounted: function mounted() {
    this.applyTheme();
    this.setupSystemTheme();
    this.loadAgents();
  },
  beforeDestroy: function beforeDestroy() {
    if (this.mediaQuery) {
      if (typeof this.mediaQuery.removeEventListener === "function") {
        this.mediaQuery.removeEventListener("change", this.onSystemThemeChange);
      } else if (typeof this.mediaQuery.removeListener === "function") {
        this.mediaQuery.removeListener(this.onSystemThemeChange);
      }
    }
  },
  methods: {
    isActive: function isActive(path) {
      return this.$route.path === path;
    },
    setTheme: function setTheme(theme) {
      this.themePreference = theme;
      try {
        window.localStorage.setItem(THEME_KEY, theme);
      } catch (e) { /* ignore */ }
    },
    applyTheme: function applyTheme() {
      document.documentElement.setAttribute("data-theme", this.resolvedTheme);
    },
    setupSystemTheme: function setupSystemTheme() {
      if (typeof window.matchMedia !== "function") { return; }
      this.mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
      this.systemPrefersDark = this.mediaQuery.matches;
      if (typeof this.mediaQuery.addEventListener === "function") {
        this.mediaQuery.addEventListener("change", this.onSystemThemeChange);
      } else if (typeof this.mediaQuery.addListener === "function") {
        this.mediaQuery.addListener(this.onSystemThemeChange);
      }
    },
    onSystemThemeChange: function onSystemThemeChange(e) {
      this.systemPrefersDark = Boolean(e && e.matches);
    },
    onSelectAgent: function onSelectAgent(id) {
      this.$store.commit("setSelectedAgentId", id || "");
    },
    async loadAgents() {
      this.agentLoading = true;
      try {
        const response = await agentsApi.list();
        this.agents = (response.agents || []).map(normalizeAgent);
        this.$store.commit("setAgents", this.agents);
        if (!this.selectedAgentId && this.agents.length) {
          this.$store.commit("setSelectedAgentId", this.agents[0].id);
        }
      } catch (e) {
        this.$message.error("加载智能体列表失败：" + e.message);
      } finally {
        this.agentLoading = false;
      }
    }
  }
};
</script>

<style scoped>
.app-shell {
  display: flex;
  height: 100vh;       /* 固定到视口高度 */
  overflow: hidden;    /* 禁止 shell 自身滚动 */
  background: var(--page-background);
}

/* ── Sidebar ── */
.app-sidebar {
  width: 200px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border-right: 1px solid var(--border-soft);
  padding: 0 0 12px 0;
  height: 100%;        /* 撑满父高度 */
  overflow-y: auto;    /* 导航项多时侧边栏独立滚动 */
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px 14px;
}

.brand-mark {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: var(--color-primary);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.brand-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--text);
}

/* Agent Switcher */
.agent-switcher {
  padding: 8px 10px 12px;
  border-bottom: 1px solid var(--border-soft);
}

.sidebar-label {
  font-size: 11px;
  color: var(--text-soft);
  margin-bottom: 6px;
  padding: 0 4px;
}

.agent-switcher__row {
  display: flex;
  gap: 6px;
  align-items: center;
}

/* Nav */
.sidebar-nav {
  flex: 1;
  padding: 8px 0;
  display: flex;
  flex-direction: column;
}

.nav-group-label {
  font-size: 11px;
  color: var(--text-soft);
  padding: 10px 16px 4px;
  letter-spacing: 0.04em;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  font-size: 14px;
  color: var(--text-muted);
  text-decoration: none;
  border-radius: 0;
  transition: background 0.15s, color 0.15s;
  cursor: pointer;
}

.nav-item i {
  font-size: 15px;
  width: 18px;
  text-align: center;
  flex-shrink: 0;
}

.nav-item:hover {
  background: var(--surface-strong);
  color: var(--text);
}

.nav-item.is-active {
  background: var(--surface-highlight);
  color: var(--color-primary);
  font-weight: 600;
}

/* Footer theme switcher */
.sidebar-footer {
  display: flex;
  gap: 4px;
  padding: 8px 12px 0;
  border-top: 1px solid var(--border-soft);
  margin-top: 8px;
}

.theme-btn {
  flex: 1;
  padding: 5px 0;
  border: 1px solid var(--border-soft);
  border-radius: 6px;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.theme-btn:hover,
.theme-btn.is-active {
  background: var(--surface-highlight);
  color: var(--color-primary);
  border-color: var(--color-primary);
}

/* ── Content ── */
.app-content {
  flex: 1;
  min-width: 0;
  height: 100%;         /* 与父等高，不超出 */
  display: flex;
  flex-direction: column;
  overflow: hidden;     /* 让 app-main 自己控制滚动 */
  background: var(--surface-subtle);
}

.app-breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 14px 28px 0;
  font-size: 13px;
  color: var(--text-soft);
  flex-shrink: 0;       /* breadcrumb 固定不压缩 */
}

.breadcrumb-sep {
  color: var(--text-soft);
}

.breadcrumb-page {
  color: var(--text-muted);
  font-weight: 500;
}

.app-main {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: hidden;    /* app-main 自身不滚动，由 main-scroll 负责 */
  display: flex;
  flex-direction: column;
}

/* 普通页面：main-scroll 负责垂直滚动 */
.main-scroll {
  flex: 1;
  min-height: 0;
  padding: 16px 28px 28px;
  overflow-y: auto;
}

/* 全高页面（聊天、会话）：不 padding / 不 scroll，子组件自管 */
.main-scroll.is-fullheight {
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.main-scroll.is-fullheight > * {
  flex: 1;
  min-height: 0;
}
</style>
