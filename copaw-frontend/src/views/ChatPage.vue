<template>
  <div class="chat-page">
    <div v-if="!selectedAgentId" class="chat-no-agent">
      <el-empty description="请先从左侧切换到一个智能体" />
    </div>

    <template v-else>
      <div class="chat-messages" ref="messageList">
        <div v-if="!messages.length" class="chat-empty">
          <el-empty description="还没有消息，发一条试试" :image-size="80" />
        </div>

        <div
          v-for="item in messages"
          :key="item.id"
          class="message-row"
          :class="'message-row--' + item.role"
        >
          <div class="message-bubble" :class="{ 'is-error': item.error, 'is-streaming': item.streaming }">
            <div class="message-content">{{ item.content || (item.streaming ? '…' : '') }}</div>
          </div>
          <div class="message-time">{{ item.timeLabel }}</div>
        </div>
      </div>

      <div class="chat-composer">
        <el-input
          v-model="composer"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="输入消息，Enter 发送，Shift+Enter 换行"
          :disabled="sending"
          @keydown.native="onKeydown"
        />
        <div class="composer-bar">
          <span class="composer-count">{{ composer.length }} / 10000</span>
          <div class="composer-actions">
            <el-button v-if="sending" size="small" @click="stopStream">停止</el-button>
            <el-button
              type="primary"
              size="small"
              :loading="sending"
              :disabled="!composer.trim()"
              @click="sendMessage"
            >
              <i class="el-icon-position"></i>
            </el-button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script>
import { buildApiUrl, chatApi, sessionsApi } from "@/services/api";
import { openJsonSse } from "@/services/sse";
import { generateId, stringifyContent } from "@/utils/format";

function timeLabel() {
  const now = new Date();
  const hh = String(now.getHours()).padStart(2, "0");
  const mm = String(now.getMinutes()).padStart(2, "0");
  return hh + ":" + mm;
}

function timeLabelFrom(ts) {
  if (!ts) { return ""; }
  try {
    const d = new Date(ts);
    const hh = String(d.getHours()).padStart(2, "0");
    const mm = String(d.getMinutes()).padStart(2, "0");
    return hh + ":" + mm;
  } catch (e) {
    return "";
  }
}

export default {
  name: "ChatPage",
  data: function data() {
    return {
      composer: "",
      sending: false,
      streamController: null,
      currentChatId: "",
      currentSessionRecordId: "",
      messages: []
    };
  },
  computed: {
    selectedAgentId: function selectedAgentId() {
      return this.$store.state.selectedAgentId;
    },
    sessionId: function sessionId() {
      return this.$store.state.sessionId;
    },
    userId: function userId() {
      return this.$store.state.userId;
    }
  },
  watch: {
    selectedAgentId: function watchAgent(newId) {
      this.messages = [];
      this.currentChatId = "";
      this.currentSessionRecordId = "";
      if (this.streamController) {
        this.streamController.abort();
        this.streamController = null;
        this.sending = false;
      }
      if (newId) {
        this.loadHistory();
      }
    }
  },
  mounted: function mounted() {
    if (this.selectedAgentId) {
      this.loadHistory();
    }
  },
  beforeDestroy: function beforeDestroy() {
    if (this.streamController) {
      this.streamController.abort();
      this.streamController = null;
    }
  },
  methods: {
    async resolveInitialSession() {
      const res = await sessionsApi.list(this.selectedAgentId, {
        page: 0,
        size: 200,
        channel: "console"
      });
      const sessions = res.sessions || [];
      if (!sessions.length) {
        this.currentSessionRecordId = "";
        this.messages = [];
        this.$store.commit("setSessionId", "console-" + generateId(""));
        return null;
      }
      let target = null;
      if (this.sessionId) {
        target = sessions.find(function (s) {
          return s.sessionId === this.sessionId;
        }.bind(this));
      }
      if (!target) {
        target = sessions[0];
      }
      this.currentSessionRecordId = target.id || "";
      if (target.sessionId) {
        this.$store.commit("setSessionId", target.sessionId);
      }
      return target;
    },
    async loadHistory() {
      if (!this.selectedAgentId) { return; }
      try {
        const session = await this.resolveInitialSession();
        if (!session || !session.id) {
          return;
        }
        const res = await sessionsApi.getMessages(this.selectedAgentId, session.id);
        const msgs = res.messages || [];
        this.messages = msgs.map(function (m) {
          return {
            id: generateId("msg_"),
            role: m.role || "assistant",
            content: m.content || "",
            streaming: false,
            error: false,
            timeLabel: m.createdAt ? timeLabelFrom(m.createdAt) : ""
          };
        });
        this.scrollToBottom();
      } catch (e) {
        this.messages = [];
      }
    },
    onKeydown: function onKeydown(e) {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        this.sendMessage();
      }
    },
    scrollToBottom: function scrollToBottom() {
      const el = this.$refs.messageList;
      if (el) {
        this.$nextTick(function tick() {
          el.scrollTop = el.scrollHeight;
        });
      }
    },
    createMessage: function createMessage(role, content) {
      const msg = {
        id: generateId("msg_"),
        role: role,
        content: content || "",
        streaming: false,
        error: false,
        timeLabel: timeLabel()
      };
      this.messages.push(msg);
      this.scrollToBottom();
      return msg;
    },
    async sendMessage() {
      if (!this.selectedAgentId) { return; }
      const text = String(this.composer || "").trim();
      if (!text || this.streamController) { return; }

      const sessionId = this.sessionId || ("console-" + generateId(""));
      this.$store.commit("setSessionId", sessionId);
      const chatId = generateId("chat_");
      this.currentChatId = chatId;

      this.createMessage("user", text);
      const assistantMsg = this.createMessage("assistant", "");
      assistantMsg.streaming = true;
      this.composer = "";
      this.sending = true;

      const stream = openJsonSse({
        url: buildApiUrl("/console/chat?agentId=" + encodeURIComponent(this.selectedAgentId)),
        body: {
          message: text,
          chatId: chatId,
          sessionId: sessionId,
          userId: this.userId,
          reconnect: false,
          channel: "console"
        },
        onMessage: this.onSseEvent.bind(this, assistantMsg),
        onError: this.onSseError.bind(this, assistantMsg),
        onFinish: this.onSseFinish.bind(this, assistantMsg)
      });

      this.streamController = stream;

      try {
        await stream.promise;
      } finally {
        this.sending = false;
        this.streamController = null;
      }
    },
    onSseEvent: function onSseEvent(msg, event) {
      if (!event || typeof event !== "object") {
        msg.content += stringifyContent(event);
        this.scrollToBottom();
        return;
      }
      if (event.type === "start") {
        if (event.content && event.content.chat_id) {
          this.currentChatId = event.content.chat_id;
        }
        return;
      }
      if (event.type === "delta") {
        msg.content += stringifyContent(event.content);
        this.scrollToBottom();
        return;
      }
      if (event.type === "done") {
        msg.streaming = false;
        return;
      }
      if (event.type === "stop") {
        msg.streaming = false;
        if (!msg.content) { msg.content = "[已停止]"; }
        return;
      }
      if (event.type === "error") {
        msg.error = true;
        msg.streaming = false;
        msg.content += stringifyContent(event.content || "[后端错误]");
        return;
      }
      msg.content += stringifyContent(event.content || event);
      this.scrollToBottom();
    },
    onSseError: function onSseError(msg, error) {
      msg.error = true;
      msg.streaming = false;
      if (!msg.content) { msg.content = "请求失败：" + error.message; }
      this.$message.error("聊天失败：" + error.message);
    },
    onSseFinish: function onSseFinish(msg) {
      msg.streaming = false;
      this.streamController = null;
      this.sending = false;
      this.resolveInitialSession().catch(function () { /* ignore */ });
    },
    async stopStream() {
      if (!this.streamController) { return; }
      try {
        if (this.currentChatId && this.selectedAgentId) {
          await chatApi.stopChat(this.selectedAgentId, this.currentChatId);
        }
      } catch (e) {
        // ignore
      } finally {
        this.streamController.abort();
        this.streamController = null;
        this.sending = false;
      }
    }
  }
};
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100%;          /* 填满 app-main 剩余高度 */
  min-height: 400px;
}

.chat-no-agent {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 0;
}

.message-row {
  display: flex;
  flex-direction: column;
  max-width: 72%;
}

.message-row--user {
  align-self: flex-end;
  align-items: flex-end;
}

.message-row--assistant,
.message-row--system {
  align-self: flex-start;
  align-items: flex-start;
}

.message-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
  white-space: pre-wrap;
}

.message-row--user .message-bubble {
  background: var(--color-primary);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.message-row--assistant .message-bubble {
  background: var(--surface);
  border: 1px solid var(--border-soft);
  color: var(--text);
  border-bottom-left-radius: 4px;
}

.message-row--system .message-bubble {
  background: var(--surface-strong);
  color: var(--text-muted);
  font-size: 12px;
}

.message-bubble.is-error {
  background: #fef0f0;
  border-color: #fbc4c4;
  color: #f56c6c;
}

.message-bubble.is-streaming::after {
  content: "▌";
  animation: blink 0.8s steps(1) infinite;
  margin-left: 2px;
  color: var(--color-primary);
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.message-time {
  font-size: 11px;
  color: var(--text-soft);
  margin-top: 4px;
  padding: 0 4px;
}

.message-content {
  margin: 0;
}

/* Composer */
.chat-composer {
  border-top: 1px solid var(--border-soft);
  background: var(--surface);
  padding: 12px 0 0;
  flex-shrink: 0;
}

.composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 4px 0;
}

.composer-count {
  font-size: 12px;
  color: var(--text-soft);
}

.composer-actions {
  display: flex;
  gap: 8px;
}
</style>
