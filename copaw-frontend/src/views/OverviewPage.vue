<template>
  <div class="console-page overview-page">
    <section class="page-card overview-hero">
      <div class="overview-hero__copy">
        <div class="overview-kicker">CoPaw Console</div>
        <h2 class="overview-title">把参考站点的产品感，搬进 Java 运行时控制台</h2>
        <p class="overview-subtitle">
          这版前端继续复用现有 Vue 2 + Element UI 的稳定接口接线，但整体信息架构和视觉语言改成更接近
          <code>copaw-python/website</code>
          的方式：先给一个清晰入口，再把聊天、模型、Agent、自动任务、MCP 和技能收进统一壳层。
        </p>
        <div class="overview-actions">
          <el-button type="primary" @click="go('/chat')">开始聊天</el-button>
          <el-button @click="go('/models')">配置模型</el-button>
          <el-button plain @click="go('/agents')">管理 Agent</el-button>
        </div>
      </div>

      <div class="overview-hero__panel">
        <div class="overview-hero__panel-label">Current runtime snapshot</div>
        <div class="overview-hero__panel-main">{{ selectedAgent ? selectedAgent.name : "还未绑定 Agent" }}</div>
        <div class="overview-hero__panel-meta">{{ selectedAgentId || "请选择一个 Agent" }}</div>

        <div class="overview-hero__panel-grid">
          <div>
            <span>Session</span>
            <strong>{{ sessionId }}</strong>
          </div>
          <div>
            <span>User</span>
            <strong>{{ userId }}</strong>
          </div>
          <div>
            <span>Theme</span>
            <strong>{{ themeLabel }}</strong>
          </div>
          <div>
            <span>Modules</span>
            <strong>{{ modules.length }}</strong>
          </div>
        </div>
      </div>
    </section>

    <section class="overview-metrics">
      <article class="page-card overview-metric">
        <span class="overview-metric__label">当前 Agent</span>
        <strong>{{ selectedAgentId || "未选择" }}</strong>
        <p>{{ selectedAgent ? "已接通 workspace 配置与 API 调用。" : "右上角先选 Agent，下面所有模块都会跟着切。" }}</p>
      </article>
      <article class="page-card overview-metric">
        <span class="overview-metric__label">会话上下文</span>
        <strong>{{ sessionId }}</strong>
        <p>聊天流、push message 和工具审批都围绕这个 session 聚合。</p>
      </article>
      <article class="page-card overview-metric">
        <span class="overview-metric__label">运行时入口</span>
        <strong>/api</strong>
        <p>开发环境默认代理到 <code>http://localhost:8080</code>，适合本地联调。</p>
      </article>
      <article class="page-card overview-metric">
        <span class="overview-metric__label">前端状态</span>
        <strong>参考官网重构</strong>
        <p>不再只是测试页，而是一个可导航、可继续打磨的正式控制台骨架。</p>
      </article>
    </section>

    <section class="overview-grid">
      <el-card shadow="never" class="page-card">
        <div slot="header" class="flex-between">
          <span>快速开始</span>
          <el-tag effect="plain" type="success">参考 Home / QuickStart</el-tag>
        </div>
        <ol class="overview-steps">
          <li>先在右上角选择目标 Agent；如果是新环境，先去 Agent 页面创建或切换到目标 workspace。</li>
          <li>到模型配置页确认 <code>active_model</code> 和 <code>running.max_iters</code>，保存后会自动触发热重载。</li>
          <li>打开聊天页直接走 SSE 对话；如果触发工具审批，右侧会同步出现待处理卡片和 push message 时间线。</li>
          <li>需要运行时配套时，再去 Cron、MCP 和技能中心补齐自动化、外部工具和能力扩展。</li>
        </ol>
      </el-card>

      <el-card shadow="never" class="page-card">
        <div slot="header" class="flex-between">
          <span>当前状态</span>
          <el-tag effect="plain" :type="selectedAgentId ? 'success' : 'warning'">{{ selectedAgentId ? '可继续联调' : '等待初始化' }}</el-tag>
        </div>

        <el-alert
          v-if="!selectedAgentId"
          title="还没有选中 Agent。先在右上角绑定一个运行时，再进入聊天、模型或自动任务页面。"
          type="warning"
          :closable="false"
          show-icon
        />

        <div v-else class="overview-status-list">
          <div class="overview-status-item">
            <span>Agent 名称</span>
            <strong>{{ selectedAgent.name || selectedAgent.id }}</strong>
          </div>
          <div class="overview-status-item">
            <span>Agent ID</span>
            <strong>{{ selectedAgent.id }}</strong>
          </div>
          <div class="overview-status-item">
            <span>Workspace</span>
            <strong>{{ selectedAgent.workspaceDir || "使用后端默认目录" }}</strong>
          </div>
          <div class="overview-status-item">
            <span>状态</span>
            <el-tag size="small" :type="selectedAgent.loaded ? 'success' : 'info'">
              {{ selectedAgent.loaded ? '已加载' : '未加载 / 待刷新' }}
            </el-tag>
          </div>
        </div>
      </el-card>
    </section>

    <section>
      <div class="section-heading">
        <div>
          <div class="section-eyebrow">Core modules</div>
          <h3 class="section-title">把原来的“验证页”改成更像产品入口的模块导航</h3>
        </div>
        <el-button type="text" @click="openGitHub">查看 CoPaw 仓库</el-button>
      </div>

      <div class="overview-modules">
        <button
          v-for="item in modules"
          :key="item.path"
          type="button"
          class="page-card overview-module"
          @click="go(item.path)"
        >
          <div class="overview-module__icon"><i :class="item.icon"></i></div>
          <div class="overview-module__copy">
            <div class="overview-module__head">
              <strong>{{ item.title }}</strong>
              <el-tag size="mini" effect="plain">{{ item.tag }}</el-tag>
            </div>
            <p>{{ item.description }}</p>
          </div>
        </button>
      </div>
    </section>

    <section class="overview-grid overview-grid--bottom">
      <el-card shadow="never" class="page-card">
        <div slot="header" class="flex-between">
          <span>这一版前端的设计边界</span>
          <el-tag effect="plain">参考 website，而不是硬搬 React</el-tag>
        </div>
        <p class="overview-paragraph">
          这次没有把 <code>copaw-python/website</code> 的 React + Tailwind 架构生搬到 Java 前端，而是保留现有 Vue 2 + Element UI 的 API
          与页面逻辑，只迁移它最值钱的部分：暖色系视觉、清晰的首页入口、模块化分区、产品化文案和更干净的壳层结构。这样改动面可控，也不会把当前已经打通的 SSE、审批和配置链路再打散一次。
        </p>
      </el-card>

      <el-card shadow="never" class="page-card">
        <div slot="header" class="flex-between">
          <span>已接通能力</span>
          <el-tag effect="plain" type="success">P0 / P1 闭环</el-tag>
        </div>
        <div class="overview-capabilities">
          <span v-for="item in capabilities" :key="item" class="overview-capability-pill">{{ item }}</span>
        </div>
      </el-card>
    </section>
  </div>
</template>

<script>
const MODULES = [
  {
    path: "/chat",
    icon: "el-icon-chat-dot-round",
    title: "控制台聊天",
    description: "SSE 对话、push message 与工具审批在同一页闭环。",
    tag: "Live"
  },
  {
    path: "/models",
    icon: "el-icon-setting",
    title: "模型配置",
    description: "直接维护 agent.json 的 active_model 与 max_iters。",
    tag: "Config"
  },
  {
    path: "/agents",
    icon: "el-icon-user-solid",
    title: "Agent / Workspace",
    description: "创建、切换、热重载和管理运行时工作目录。",
    tag: "Runtime"
  },
  {
    path: "/cron",
    icon: "el-icon-time",
    title: "自动任务",
    description: "配置 Cron、立即执行和观察下一次运行时间。",
    tag: "Automation"
  },
  {
    path: "/mcp",
    icon: "el-icon-connection",
    title: "MCP 客户端",
    description: "管理 stdio / sse / http 外部工具接入。",
    tag: "Integration"
  },
  {
    path: "/skills",
    icon: "el-icon-magic-stick",
    title: "技能中心",
    description: "查看技能清单、导入 ZIP、切渠道和启停。",
    tag: "Extension"
  }
];

const CAPABILITIES = [
  "SSE Chat",
  "Push Messages",
  "Tool Approval",
  "Agent Reload",
  "active_model Config",
  "Cron Control",
  "MCP Registry",
  "Skill Import"
];

export default {
  name: "OverviewPage",
  data: function data() {
    return {
      modules: MODULES,
      capabilities: CAPABILITIES
    };
  },
  computed: {
    selectedAgentId: function selectedAgentId() {
      return this.$store.state.selectedAgentId;
    },
    selectedAgent: function selectedAgent() {
      return this.$store.getters.selectedAgent || {};
    },
    sessionId: function sessionId() {
      return this.$store.state.sessionId;
    },
    userId: function userId() {
      return this.$store.state.userId;
    },
    themeLabel: function themeLabel() {
      return document.documentElement.getAttribute("data-theme") === "dark" ? "Dark" : "Light";
    }
  },
  methods: {
    go(path) {
      if (this.$route.path !== path) {
        this.$router.push(path);
      }
    },
    openGitHub() {
      window.open("https://github.com/agentscope-ai/CoPaw", "_blank", "noopener");
    }
  }
};
</script>

<style scoped>
.overview-page {
  gap: 24px;
}

.overview-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(320px, 1fr);
  gap: 24px;
  padding: 28px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top left, rgba(255, 157, 77, 0.22), transparent 34%),
    radial-gradient(circle at bottom right, rgba(255, 214, 174, 0.45), transparent 32%),
    var(--surface);
}

.overview-kicker,
.section-eyebrow,
.overview-metric__label,
.overview-hero__panel-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-soft);
}

.overview-title,
.section-title {
  margin: 12px 0 0;
  font-family: var(--font-display);
  font-size: 36px;
  line-height: 1.08;
  color: var(--text);
}

.overview-subtitle,
.overview-paragraph {
  margin: 16px 0 0;
  font-size: 15px;
  line-height: 1.85;
  color: var(--text-muted);
}

.overview-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 22px;
}

.overview-hero__panel {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 18px;
  padding: 22px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.64);
  border: 1px solid rgba(234, 232, 231, 0.9);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.65);
}

.overview-hero__panel-main {
  font-size: 28px;
  font-weight: 700;
  color: var(--text);
}

.overview-hero__panel-meta {
  font-size: 14px;
  color: var(--text-muted);
}

.overview-hero__panel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.overview-hero__panel-grid div {
  padding: 12px 14px;
  border-radius: 18px;
  background: var(--surface-strong);
  border: 1px solid var(--border-soft);
}

.overview-hero__panel-grid span {
  display: block;
  font-size: 12px;
  color: var(--text-soft);
}

.overview-hero__panel-grid strong {
  display: block;
  margin-top: 6px;
  font-size: 14px;
  color: var(--text);
  word-break: break-word;
}

.overview-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.overview-metric {
  padding: 20px;
}

.overview-metric strong {
  display: block;
  margin-top: 10px;
  font-size: 20px;
  line-height: 1.25;
  color: var(--text);
}

.overview-metric p {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-muted);
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.overview-grid--bottom {
  align-items: stretch;
}

.overview-steps {
  margin: 0;
  padding-left: 20px;
  color: var(--text-muted);
}

.overview-steps li {
  margin-bottom: 12px;
  line-height: 1.75;
}

.overview-status-list {
  display: grid;
  gap: 12px;
}

.overview-status-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 18px;
  background: var(--surface-subtle);
  border: 1px solid var(--border-soft);
}

.overview-status-item span {
  color: var(--text-soft);
  font-size: 13px;
}

.overview-status-item strong {
  color: var(--text);
  font-size: 14px;
  text-align: right;
  word-break: break-word;
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.overview-modules {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.overview-module {
  width: 100%;
  border: 0;
  padding: 20px;
  text-align: left;
  cursor: pointer;
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;
}

.overview-module:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-hover);
  border-color: rgba(255, 157, 77, 0.5);
}

.overview-module__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: var(--surface-highlight);
  color: var(--accent);
  font-size: 18px;
}

.overview-module__copy {
  margin-top: 18px;
}

.overview-module__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.overview-module__head strong {
  font-size: 16px;
  color: var(--text);
}

.overview-module__copy p {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.75;
  color: var(--text-muted);
}

.overview-capabilities {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.overview-capability-pill {
  display: inline-flex;
  align-items: center;
  min-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  background: var(--surface-subtle);
  border: 1px solid var(--border-soft);
  color: var(--text);
  font-size: 13px;
  font-weight: 600;
}

@media (max-width: 1280px) {
  .overview-metrics,
  .overview-modules {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1024px) {
  .overview-hero,
  .overview-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .overview-title,
  .section-title {
    font-size: 28px;
  }

  .overview-metrics,
  .overview-modules,
  .overview-grid {
    grid-template-columns: 1fr;
  }

  .overview-hero {
    padding: 22px;
  }

  .overview-status-item,
  .section-heading,
  .overview-module__head {
    display: block;
  }

  .overview-status-item strong {
    display: block;
    margin-top: 8px;
    text-align: left;
  }
}
</style>
