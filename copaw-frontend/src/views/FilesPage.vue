<template>
  <div class="files-page">
    <div v-if="!selectedAgentId" class="files-empty">
      <el-empty description="请先选择一个智能体" :image-size="80" />
    </div>

    <template v-else>
      <div class="files-bar">
        <span class="files-workspace">工作区路径：{{ workspaceDir }}</span>
        <div class="files-actions">
          <el-button size="small" icon="el-icon-upload2">上传</el-button>
          <el-button size="small" icon="el-icon-download">下载</el-button>
          <el-button size="small" icon="el-icon-refresh" :loading="loading" @click="loadFiles">刷新</el-button>
        </div>
      </div>

      <el-row :gutter="16">
        <el-col :span="8">
          <div class="file-list-panel">
            <div class="file-list-panel__title">
              核心文件
              <span class="file-list-panel__hint">引导角色、身份和工具指南。</span>
            </div>
            <div v-if="loading" class="file-list-loading">
              <el-skeleton :rows="4" animated />
            </div>
            <template v-else>
              <button
                v-for="file in files"
                :key="file.path"
                type="button"
                class="file-item"
                :class="{ 'is-active': selectedPath === file.path }"
                @click="openFile(file)"
              >
                <div class="file-item__main">
                  <div class="file-item__drag-handle">⋮⋮</div>
                  <div class="file-item__info">
                    <div class="file-item__name">{{ file.filename }}</div>
                    <div class="file-item__meta">{{ file.sizeLabel }} · {{ file.modifiedLabel }}</div>
                  </div>
                  <el-switch
                    v-if="file.togglable"
                    :value="file.active"
                    size="small"
                    @change="toggleActive(file, $event)"
                    @click.native.stop
                  />
                </div>
              </button>
              <div v-if="!files.length" class="file-list-empty">
                <el-empty description="暂无核心文件" :image-size="60" />
              </div>
            </template>
          </div>
        </el-col>

        <el-col :span="16">
          <div class="file-editor-panel">
            <template v-if="selectedPath">
              <div class="file-editor-header">
                <span class="file-editor-title">{{ selectedPath }}</span>
                <div class="file-editor-actions">
                  <el-tag v-if="dirty" type="warning" size="small" effect="plain">未保存</el-tag>
                  <el-button size="small" type="primary" :loading="saving" :disabled="!dirty" @click="saveFile">保存</el-button>
                </div>
              </div>
              <el-input
                v-model="content"
                type="textarea"
                :rows="26"
                resize="none"
                class="file-textarea"
                @input="dirty = true"
              />
            </template>
            <div v-else class="file-editor-empty">
              <el-empty description="选择文件进行编辑" :image-size="80" />
            </div>
          </div>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script>
import { agentsApi } from "@/services/api";

function formatSize(size) {
  const n = typeof size === "number" ? size : 0;
  if (n < 1024) { return n + " B"; }
  if (n < 1024 * 1024) { return (n / 1024).toFixed(1) + " KB"; }
  return (n / (1024 * 1024)).toFixed(1) + " MB";
}

function formatTime(ts) {
  if (!ts) { return ""; }
  try {
    const d = new Date(ts);
    const now = new Date();
    const diff = now - d;
    if (diff < 60 * 60 * 1000) { return Math.floor(diff / 60000) + " 分钟前"; }
    if (diff < 24 * 60 * 60 * 1000) { return Math.floor(diff / 3600000) + " 小时前"; }
    return Math.floor(diff / 86400000) + " 天前";
  } catch (e) {
    return ts;
  }
}

// 有"启用/禁用"开关的核心文件（加载到系统提示词）
const TOGGLABLE_FILES = ["AGENTS.md", "SOUL.md", "PROFILE.md"];
// 所有核心文件的固定排序权重（数字越小越靠前）
const FILE_ORDER = {
  "AGENTS.md": 0,
  "SOUL.md": 1,
  "PROFILE.md": 2,
  "HEARTBEAT.md": 3,
  "MEMORY.md": 4
};

function fileWeight(filename) {
  const upper = String(filename || "").toUpperCase();
  // 精确匹配（大小写不敏感）
  for (const key of Object.keys(FILE_ORDER)) {
    if (upper === key.toUpperCase()) { return FILE_ORDER[key]; }
  }
  return 99;
}

export default {
  name: "FilesPage",
  data: function data() {
    return {
      loading: false,
      saving: false,
      files: [],
      selectedPath: "",
      content: "",
      originalContent: "",
      dirty: false
    };
  },
  computed: {
    selectedAgentId: function selectedAgentId() {
      return this.$store.state.selectedAgentId;
    },
    selectedAgent: function selectedAgent() {
      return this.$store.getters.selectedAgent || null;
    },
    workspaceDir: function workspaceDir() {
      return (this.selectedAgent && this.selectedAgent.workspaceDir) || "";
    }
  },
  watch: {
    selectedAgentId: function watchAgent() {
      this.reset();
      this.loadFiles();
    }
  },
  mounted: function mounted() {
    this.loadFiles();
  },
  methods: {
    reset: function reset() {
      this.files = [];
      this.selectedPath = "";
      this.content = "";
      this.originalContent = "";
      this.dirty = false;
    },
    async loadFiles() {
      if (!this.selectedAgentId) { return; }
      this.loading = true;
      try {
        const res = await agentsApi.listFiles(this.selectedAgentId, "");
        const rawFiles = res.files || res.entries || [];
        // 只显示 .md 文件
        this.files = rawFiles
          .filter(function filterMd(f) {
            return !f.isDirectory && String(f.filename || f.name || "").toLowerCase().endsWith(".md");
          })
          .map(function mapFile(f) {
            const filename = f.filename || f.name || f.path || "";
            const upper = String(filename).toUpperCase();
            const isTogglable = TOGGLABLE_FILES.some(function (t) {
              return t.toUpperCase() === upper;
            });
            return {
              filename: filename,
              path: f.path || filename,
              size: f.size || 0,
              modified: f.modified || f.updatedAt || "",
              active: Boolean(f.active !== false),
              togglable: isTogglable,
              sizeLabel: formatSize(f.size || 0),
              modifiedLabel: formatTime(f.modified || f.updatedAt || ""),
              _weight: fileWeight(filename)
            };
          })
          .sort(function (a, b) {
            if (a._weight !== b._weight) { return a._weight - b._weight; }
            return a.filename.localeCompare(b.filename);
          });
      } catch (e) {
        this.$message.error("加载文件列表失败：" + e.message);
      } finally {
        this.loading = false;
      }
    },
    async openFile(file) {
      if (this.dirty && this.selectedPath && this.selectedPath !== file.path) {
        const ok = await this.$confirm("当前文件有未保存改动，切换后将丢失，是否继续？", "提示", {
          confirmButtonText: "继续",
          cancelButtonText: "取消",
          type: "warning"
        }).then(function () { return true; }).catch(function () { return false; });
        if (!ok) { return; }
      }
      this.selectedPath = file.path;
      this.content = "";
      this.originalContent = "";
      this.dirty = false;
      try {
        const res = await agentsApi.getFile(this.selectedAgentId, file.path);
        const text = (res && (res.content || res.text || res)) || "";
        this.content = typeof text === "string" ? text : JSON.stringify(text, null, 2);
        this.originalContent = this.content;
      } catch (e) {
        this.$message.error("读取文件失败：" + e.message);
      }
    },
    async saveFile() {
      if (!this.selectedAgentId || !this.selectedPath) { return; }
      this.saving = true;
      try {
        await agentsApi.saveFile(this.selectedAgentId, this.selectedPath, this.content);
        this.originalContent = this.content;
        this.dirty = false;
        this.$message.success("已保存");
      } catch (e) {
        this.$message.error("保存失败：" + e.message);
      } finally {
        this.saving = false;
      }
    },
    toggleActive: function toggleActive(file, val) {
      file.active = val;
      // TODO: 调用后端启用/禁用接口
    }
  }
};
</script>

<style scoped>
.files-page { display: flex; flex-direction: column; gap: 14px; }

.files-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.files-workspace {
  font-size: 13px;
  color: var(--text-muted);
}

.files-actions { display: flex; gap: 8px; }

.files-empty { display: flex; align-items: center; justify-content: center; height: 300px; }

/* File list panel */
.file-list-panel {
  background: var(--surface);
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  padding: 14px 12px;
}

.file-list-panel__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-muted);
  margin-bottom: 4px;
}

.file-list-panel__hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-soft);
  margin-left: 6px;
}

.file-list-loading { padding: 8px 0; }

.file-item {
  width: 100%;
  background: none;
  border: none;
  border-radius: 8px;
  padding: 10px 8px;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s;
  display: block;
}

.file-item:hover { background: var(--surface-strong); }
.file-item.is-active { background: var(--surface-highlight); }

.file-item__main {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-item__drag-handle {
  color: var(--text-soft);
  font-size: 12px;
  cursor: grab;
  flex-shrink: 0;
}

.file-item__info { flex: 1; min-width: 0; }

.file-item__name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-item__meta { font-size: 11px; color: var(--text-soft); margin-top: 2px; }

.file-list-empty { padding: 16px; }

/* Editor panel */
.file-editor-panel {
  background: var(--surface);
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  padding: 14px 16px;
  min-height: 400px;
  display: flex;
  flex-direction: column;
}

.file-editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 8px;
}

.file-editor-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-muted);
}

.file-editor-actions { display: flex; align-items: center; gap: 8px; }

.file-textarea { flex: 1; }

.file-editor-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
