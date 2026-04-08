<template>
  <div class="sessions-page">
    <!-- 左侧会话列表 -->
    <div class="sessions-sidebar">
      <div class="sessions-sidebar__header">
        <span class="sessions-sidebar__title">会话列表</span>
        <el-button size="mini" icon="el-icon-refresh" :loading="listLoading" circle @click="loadSessions" />
      </div>

      <div class="sessions-filter">
        <el-input
          v-model="filterText"
          placeholder="搜索会话..."
          size="small"
          prefix-icon="el-icon-search"
          clearable
        />
      </div>

      <div v-if="listLoading" class="sessions-loading">
        <el-skeleton :rows="4" animated />
      </div>

      <div v-else-if="!filteredSessions.length" class="sessions-empty">
        <el-empty description="暂无会话记录" :image-size="60" />
      </div>

      <div v-else class="sessions-list">
        <button
          v-for="s in filteredSessions"
          :key="s.id"
          type="button"
          class="session-item"
          :class="{ 'is-active': selectedSessionId === s.id }"
          @click="selectSession(s)"
        >
          <div class="session-item__channel">
            <el-tag size="mini" :type="channelTagType(s.channel)">{{ s.channel || 'console' }}</el-tag>
          </div>
          <div class="session-item__name">{{ s.name || s.sessionId || s.id }}</div>
          <div class="session-item__meta">{{ s.userId || '-' }} · {{ formatTime(s.updatedAt || s.createdAt) }}</div>
          <div class="session-item__preview">{{ s.preview || '' }}</div>
        </button>
      </div>

      <!-- 分页 -->
      <div v-if="total > pageSize" class="sessions-pagination">
        <el-pagination
          small
          layout="prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="currentPage"
          @current-change="onPageChange"
        />
      </div>
    </div>

    <!-- 右侧消息详情 -->
    <div class="sessions-detail">
      <div v-if="!selectedSessionId" class="sessions-detail__empty">
        <el-empty description="选择一个会话查看消息记录" :image-size="80" />
      </div>

      <template v-else>
        <div class="sessions-detail__header">
          <div class="sessions-detail__title">
            <el-tag :type="channelTagType(selectedSession && selectedSession.channel)">
              {{ (selectedSession && selectedSession.channel) || 'console' }}
            </el-tag>
            <span class="detail-title-text">{{ (selectedSession && (selectedSession.name || selectedSession.sessionId)) || '当前会话' }}</span>
          </div>
          <div class="sessions-detail__actions">
            <el-button size="mini" icon="el-icon-refresh" :loading="msgLoading" @click="loadMessages">刷新</el-button>
          </div>
        </div>

        <div v-if="msgLoading" class="sessions-detail__loading">
          <el-skeleton :rows="6" animated />
        </div>

        <div v-else-if="!detailMessages.length" class="sessions-detail__empty">
          <el-empty description="暂无消息记录" :image-size="60" />
        </div>

        <div v-else class="sessions-messages" ref="msgList">
          <div
            v-for="(msg, idx) in detailMessages"
            :key="idx"
            class="session-msg"
            :class="'session-msg--' + msg.role"
          >
            <div class="session-msg__bubble">
              <pre class="session-msg__content">{{ msg.content }}</pre>
            </div>
            <div class="session-msg__meta">{{ msg.role }}</div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script>
import { sessionsApi } from "@/services/api";

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

const CHANNEL_TAG = {
  console: "primary",
  wechat: "success",
  dingtalk: "warning",
  feishu: "info",
  slack: ""
};

export default {
  name: "SessionsPage",
  data: function data() {
    return {
      listLoading: false,
      msgLoading: false,
      sessions: [],
      total: 0,
      currentPage: 1,
      pageSize: 30,
      filterText: "",
      filterChannel: "",
      channels: ["console", "wechat", "dingtalk", "feishu", "slack"],
      selectedSessionId: "",
      selectedSession: null,
      detailMessages: []
    };
  },
  computed: {
    selectedAgentId: function selectedAgentId() {
      return this.$store.state.selectedAgentId;
    },
    filteredSessions: function filteredSessions() {
      if (!this.filterText) { return this.sessions; }
      const kw = this.filterText.toLowerCase();
      return this.sessions.filter(function (s) {
        return (s.name || "").toLowerCase().includes(kw)
          || (s.sessionId || "").toLowerCase().includes(kw)
          || (s.userId || "").toLowerCase().includes(kw);
      });
    }
  },
  watch: {
    selectedAgentId: function watchAgent() {
      this.currentPage = 1;
      this.sessions = [];
      this.selectedSessionId = "";
      this.selectedSession = null;
      this.detailMessages = [];
      this.loadSessions();
    }
  },
  mounted: function mounted() {
    this.loadSessions();
  },
  methods: {
    formatTime: formatTime,
    channelTagType: function channelTagType(channel) {
      return CHANNEL_TAG[channel] || "";
    },
    async loadSessions() {
      if (!this.selectedAgentId) { this.sessions = []; return; }
      this.listLoading = true;
      try {
        const params = {
          agentId: this.selectedAgentId,
          page: this.currentPage - 1,
          size: this.pageSize
        };
        if (this.filterChannel) { params.channel = this.filterChannel; }
        const res = await sessionsApi.list(this.selectedAgentId, params);
        const list = res.sessions || res.content || res.items || [];

        this.sessions = list;
        this.total = res.total || res.totalElements || list.length;

        // 自动选中第一条
        if (this.sessions.length && !this.selectedSessionId) {
          this.selectSession(this.sessions[0]);
        }
      } catch (e) {
        this.$message.error("加载会话列表失败：" + e.message);
      } finally {
        this.listLoading = false;
      }
    },
    async selectSession(session) {
      this.selectedSessionId = session.id;
      this.selectedSession = session;
      this.detailMessages = [];
      if (session && session.sessionId) {
        this.$store.commit("setSessionId", session.sessionId);
      }
      await this.loadMessages();
    },
    async loadMessages() {
      if (!this.selectedAgentId || !this.selectedSessionId) { return; }
      this.msgLoading = true;
      try {
        const res = await sessionsApi.getMessages(this.selectedAgentId, this.selectedSessionId);
        this.detailMessages = res.messages || [];
        this.$nextTick(function () {
          const el = this.$refs.msgList;
          if (el) { el.scrollTop = el.scrollHeight; }
        }.bind(this));
      } catch (e) {
        this.$message.error("加载消息失败：" + e.message);
      } finally {
        this.msgLoading = false;
      }
    },
    onPageChange: function onPageChange(page) {
      this.currentPage = page;
      this.loadSessions();
    }
  }
};
</script>

<style scoped>
.sessions-page {
  display: flex;
  gap: 0;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

/* ── Left sidebar ── */
.sessions-sidebar {
  width: 280px;
  flex-shrink: 0;
  border-right: 1px solid var(--border-soft);
  background: var(--surface);
  border-radius: 10px 0 0 10px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sessions-sidebar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 14px 8px;
  flex-shrink: 0;
}

.sessions-sidebar__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-muted);
}

.sessions-filter {
  padding: 0 10px 10px;
  flex-shrink: 0;
}

.sessions-loading {
  padding: 12px;
}

.sessions-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sessions-list {
  flex: 1;
  overflow-y: auto;
}

.session-item {
  width: 100%;
  background: none;
  border: none;
  border-bottom: 1px solid var(--border-soft);
  padding: 10px 14px;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s;
}

.session-item:hover { background: var(--surface-strong); }
.session-item.is-active { background: var(--surface-highlight); }

.session-item__channel { margin-bottom: 4px; }

.session-item__name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-item__meta {
  font-size: 11px;
  color: var(--text-soft);
  margin-top: 2px;
}

.session-item__preview {
  font-size: 12px;
  color: var(--text-soft);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sessions-pagination {
  padding: 8px 10px;
  flex-shrink: 0;
  border-top: 1px solid var(--border-soft);
}

/* ── Right detail ── */
.sessions-detail {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--surface-subtle);
  border-radius: 0 10px 10px 0;
  overflow: hidden;
}

.sessions-detail__empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sessions-detail__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px 10px;
  border-bottom: 1px solid var(--border-soft);
  background: var(--surface);
  flex-shrink: 0;
}

.sessions-detail__title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-title-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.sessions-detail__actions {
  display: flex;
  gap: 8px;
}

.sessions-detail__loading {
  padding: 16px;
}

.sessions-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.session-msg {
  display: flex;
  flex-direction: column;
  max-width: 80%;
}

.session-msg--user {
  align-self: flex-end;
  align-items: flex-end;
}

.session-msg--assistant,
.session-msg--system,
.session-msg--tool {
  align-self: flex-start;
  align-items: flex-start;
}

.session-msg__bubble {
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 13px;
}

.session-msg--user .session-msg__bubble {
  background: var(--color-primary);
  color: #fff;
}

.session-msg--assistant .session-msg__bubble {
  background: var(--surface);
  border: 1px solid var(--border-soft);
  color: var(--text);
}

.session-msg--system .session-msg__bubble,
.session-msg--tool .session-msg__bubble {
  background: var(--surface-strong);
  color: var(--text-muted);
  font-size: 12px;
}

.session-msg__content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: inherit;
}

.session-msg__meta {
  font-size: 11px;
  color: var(--text-soft);
  margin-top: 3px;
  padding: 0 2px;
}
</style>
