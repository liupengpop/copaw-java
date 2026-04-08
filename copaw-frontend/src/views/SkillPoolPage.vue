<template>
  <div class="skillpool-page">
    <div class="pool-toolbar">
      <el-button icon="el-icon-refresh" size="small" :loading="loading" @click="loadPool">刷新</el-button>
      <el-button size="small" @click="broadcast">广播到智能体</el-button>
      <el-button size="small" @click="updateBuiltin">更新内置技能</el-button>
      <el-upload :show-file-list="false" :http-request="uploadZip" accept=".zip" action="#">
        <el-button size="small" icon="el-icon-upload2">通过zip上传</el-button>
      </el-upload>
      <el-button size="small">从Skills Hub导入技能</el-button>
      <el-button size="small" type="primary">批量操作</el-button>
      <el-button size="small" type="primary" icon="el-icon-plus" @click="createSkill">创建技能</el-button>
    </div>

    <div class="pool-search">
      <el-input v-model="keyword" placeholder="按名称筛选" size="small" clearable prefix-icon="el-icon-search" style="max-width:320px" />
    </div>

    <div v-loading="loading" class="pool-list">
      <div v-for="skill in filteredSkills" :key="skill.dirName" class="pool-item">
        <div class="pool-item__icon">
          <div class="skill-icon">{{ (skill.name || '?').charAt(0).toUpperCase() }}</div>
        </div>
        <div class="pool-item__info">
          <div class="pool-item__head">
            <span class="pool-item__name">{{ skill.name }}</span>
            <el-tag v-if="skill.source" size="mini" effect="plain">{{ skill.source }}</el-tag>
            <el-tag size="mini" effect="plain">{{ skill.scope || 'all' }}</el-tag>
            <span class="pool-item__time">更新时间 {{ skill.updatedLabel }}</span>
          </div>
          <div class="pool-item__desc">{{ skill.description }}</div>
        </div>
        <div class="pool-item__actions">
          <el-button size="mini" type="primary" plain @click="broadcastSkill(skill)">广播到智能体</el-button>
          <el-button size="mini" type="danger" plain @click="removeSkill(skill)">删除</el-button>
        </div>
      </div>
    </div>
    <div v-if="!loading && !filteredSkills.length" class="pool-empty">
      <el-empty description="暂无技能" />
    </div>
  </div>
</template>

<script>
import axios from "axios";
import { API_BASE } from "@/services/api";

const http = axios.create({ baseURL: API_BASE, timeout: 30000 });
http.interceptors.response.use(function r(res) { return res.data; });

function timeAgo(ts) {
  if (!ts) { return ""; }
  try {
    const diff = Date.now() - new Date(ts).getTime();
    const d = Math.floor(diff / 86400000);
    if (d > 0) { return d + " 天前"; }
    const h = Math.floor(diff / 3600000);
    if (h > 0) { return h + " 小时前"; }
    return Math.floor(diff / 60000) + " 分钟前";
  } catch (e) { return ts; }
}

export default {
  name: "SkillPoolPage",
  data: function data() {
    return {
      loading: false,
      skills: [],
      keyword: ""
    };
  },
  computed: {
    filteredSkills: function filteredSkills() {
      const kw = this.keyword.trim().toLowerCase();
      if (!kw) { return this.skills; }
      return this.skills.filter(function f(s) {
        return (s.name || "").toLowerCase().includes(kw) || (s.description || "").toLowerCase().includes(kw);
      });
    }
  },
  mounted: function mounted() {
    this.loadPool();
  },
  methods: {
    async loadPool() {
      this.loading = true;
      try {
        // 全局技能池不需要 agentId，使用第一个 agent 拉取
        const agentId = this.$store.state.selectedAgentId;
        if (!agentId) { return; }
        const res = await http.get("/skills/pool", { params: { agentId: agentId } });
        const list = res.skills || res || [];
        this.skills = list.map(function mapSkill(s) {
          return Object.assign({ updatedLabel: timeAgo(s.updatedAt || s.updated_at) }, s);
        });
      } catch (e) {
        this.$message.error("加载技能池失败：" + e.message);
      } finally {
        this.loading = false;
      }
    },
    broadcast: function broadcast() { this.$message.info("广播功能开发中"); },
    updateBuiltin: function updateBuiltin() { this.$message.info("更新内置技能开发中"); },
    createSkill: function createSkill() { this.$message.info("创建技能开发中"); },
    broadcastSkill: function broadcastSkill() { this.$message.info("开发中"); },
    removeSkill: function removeSkill() { this.$message.info("开发中"); },
    uploadZip: function uploadZip() { this.$message.info("上传技能开发中"); }
  }
};
</script>

<style scoped>
.skillpool-page { display: flex; flex-direction: column; gap: 14px; }
.pool-toolbar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.pool-search {}
.pool-list { display: flex; flex-direction: column; gap: 2px; }
.pool-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 8px;
  border-radius: 8px;
  transition: background 0.15s;
}
.pool-item:hover { background: var(--surface-strong); }
.skill-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--surface-highlight);
  color: var(--color-primary);
  font-weight: 700;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.pool-item__info { flex: 1; min-width: 0; }
.pool-item__head { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; margin-bottom: 4px; }
.pool-item__name { font-size: 14px; font-weight: 600; color: var(--text); }
.pool-item__time { font-size: 11px; color: var(--text-soft); margin-left: 4px; }
.pool-item__desc { font-size: 12px; color: var(--text-muted); line-height: 1.6; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.pool-item__actions { display: flex; gap: 6px; flex-shrink: 0; align-items: flex-start; padding-top: 2px; }
.pool-empty { padding: 40px; text-align: center; }
</style>
