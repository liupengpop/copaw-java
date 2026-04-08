<template>
  <div class="tools-page">
    <div class="tools-header">
      <el-button size="small" icon="el-icon-refresh" :loading="loading" @click="loadTools">刷新</el-button>
    </div>
    <div v-loading="loading" class="tools-grid">
      <div v-for="tool in tools" :key="tool.name" class="tool-card">
        <div class="tool-card__head">
          <div class="tool-name">
            {{ tool.name }}
            <el-tag v-if="tool.source === 'mcp'" size="mini" type="warning" effect="plain">MCP</el-tag>
            <el-tag v-else size="mini" type="success" effect="plain">内置</el-tag>
          </div>
          <span class="tool-desc">{{ tool.description }}</span>
        </div>
      </div>
    </div>
    <div v-if="!loading && !tools.length" class="tools-empty">
      <el-empty description="暂无工具数据" />
    </div>
  </div>
</template>

<script>
import axios from "axios";
import { API_BASE } from "@/services/api";

const http = axios.create({ baseURL: API_BASE, timeout: 15000 });
http.interceptors.response.use(function r(res) { return res.data; });

export default {
  name: "ToolsPage",
  data: function data() {
    return {
      loading: false,
      tools: []
    };
  },
  computed: {
    selectedAgentId: function selectedAgentId() {
      return this.$store.state.selectedAgentId;
    }
  },
  watch: {
    selectedAgentId: function watchAgent() {
      this.loadTools();
    }
  },
  mounted: function mounted() {
    this.loadTools();
  },
  methods: {
    async loadTools() {
      if (!this.selectedAgentId) { this.tools = []; return; }
      this.loading = true;
      try {
        const res = await http.get("/tools", { params: { agentId: this.selectedAgentId } });
        this.tools = res.tools || res || [];
      } catch (e) {
        this.$message.error("加载工具失败：" + e.message);
      } finally {
        this.loading = false;
      }
    }
  }
};
</script>

<style scoped>
.tools-page { display: flex; flex-direction: column; gap: 16px; }
.tools-header { display: flex; }
.tools-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}
.tool-card {
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  padding: 14px 16px;
  background: var(--surface);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.tool-card__head { display: flex; flex-direction: column; gap: 6px; }
.tool-name { font-weight: 600; font-size: 14px; display: flex; align-items: center; gap: 8px; color: var(--text); }
.tool-desc { font-size: 12px; color: var(--text-muted); line-height: 1.6; }
.tool-card__foot { display: flex; justify-content: flex-end; }
.tools-empty { padding: 40px; text-align: center; }
</style>
