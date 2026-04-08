import Vue from "vue";
import VueRouter from "vue-router";

import ChatPage from "@/views/ChatPage.vue";
import ChannelsPage from "@/views/ChannelsPage.vue";
import SessionsPage from "@/views/SessionsPage.vue";
import CronPage from "@/views/CronPage.vue";
import HeartbeatPage from "@/views/HeartbeatPage.vue";
import FilesPage from "@/views/FilesPage.vue";
import SkillsPage from "@/views/SkillsPage.vue";
import ToolsPage from "@/views/ToolsPage.vue";
import McpPage from "@/views/McpPage.vue";
import RuntimeConfigPage from "@/views/RuntimeConfigPage.vue";
import AgentsPage from "@/views/AgentsPage.vue";
import ModelConfigPage from "@/views/ModelConfigPage.vue";
import SkillPoolPage from "@/views/SkillPoolPage.vue";
import EnvPage from "@/views/EnvPage.vue";

Vue.use(VueRouter);

const routes = [
  { path: "/", redirect: "/chat" },

  // 聊天
  { path: "/chat", name: "chat", component: ChatPage },

  // 控制 - 当前 agent
  { path: "/channels", name: "channels", component: ChannelsPage },
  { path: "/sessions", name: "sessions", component: SessionsPage },
  { path: "/cron", name: "cron", component: CronPage },
  { path: "/heartbeat", name: "heartbeat", component: HeartbeatPage },

  // 工作区 - 当前 agent
  { path: "/files", name: "files", component: FilesPage },
  { path: "/skills", name: "skills", component: SkillsPage },
  { path: "/tools", name: "tools", component: ToolsPage },
  { path: "/mcp", name: "mcp", component: McpPage },
  { path: "/runtime", name: "runtime", component: RuntimeConfigPage },

  // 设置 - 全局
  { path: "/agents", name: "agents", component: AgentsPage },
  { path: "/models", name: "models", component: ModelConfigPage },
  { path: "/skillpool", name: "skillpool", component: SkillPoolPage },
  { path: "/env", name: "env", component: EnvPage }
];

const router = new VueRouter({
  mode: "hash",
  routes
});

export default router;
