<template>
  <div class="channels-page">
    <div v-if="!selectedAgentId" class="page-empty">
      <el-empty description="请先选择一个智能体" :image-size="80" />
    </div>

    <template v-else>
      <div class="channels-bar">
        <el-button size="small" icon="el-icon-refresh" :loading="loading" @click="loadChannels">刷新</el-button>
      </div>

      <div v-loading="loading" class="channels-list">
        <div v-for="ch in channels" :key="ch.name" class="channel-item">
          <div class="channel-item__icon" :class="'channel-item__icon--' + ch.name">
            <i :class="channelIcon(ch.name)"></i>
          </div>
          <div class="channel-item__info">
            <div class="channel-item__name">{{ channelLabel(ch.name) }}</div>
            <div class="channel-item__desc">{{ channelDesc(ch.name) }}</div>
          </div>
          <div class="channel-item__status">
            <el-tag v-if="ch.enabled" size="small" type="success" effect="plain">运行中</el-tag>
            <el-tag v-else size="small" type="info" effect="plain">未启用</el-tag>
          </div>
          <div class="channel-item__actions">
            <el-switch
              :value="ch.enabled"
              :disabled="toggling === ch.name"
              @change="toggleChannel(ch, $event)"
            />
          </div>
        </div>
        <div v-if="!loading && !channels.length" class="page-empty">
          <el-empty description="暂无频道配置" :image-size="60" />
        </div>
      </div>
    </template>
  </div>
</template>

<script>
import axios from "axios";
import { API_BASE } from "@/services/api";

const http = axios.create({ baseURL: API_BASE, timeout: 15000 });
http.interceptors.response.use(function r(res) { return res.data; });

const CHANNEL_META = {
  console:  { label: "Console（内置）", desc: "内置控制台频道，始终可用", icon: "el-icon-monitor" },
  telegram: { label: "Telegram",        desc: "Telegram Bot 频道",         icon: "el-icon-chat-line-round" },
  dingtalk: { label: "钉钉",            desc: "钉钉机器人频道",              icon: "el-icon-chat-dot-round" },
  feishu:   { label: "飞书",            desc: "飞书机器人频道",              icon: "el-icon-s-comment" },
  wecom:    { label: "企业微信",        desc: "企业微信应用频道",            icon: "el-icon-message" },
  discord:  { label: "Discord",         desc: "Discord Bot 频道",           icon: "el-icon-headset" },
  mqtt:     { label: "MQTT",            desc: "MQTT IoT 消息频道",           icon: "el-icon-connection" },
  onebot:   { label: "OneBot",          desc: "QQ / OneBot 协议频道",       icon: "el-icon-chat-square" }
};

export default {
  name: "ChannelsPage",
  data: function data() {
    return {
      loading: false,
      channels: [],
      toggling: ""
    };
  },
  computed: {
    selectedAgentId: function selectedAgentId() {
      return this.$store.state.selectedAgentId;
    }
  },
  watch: {
    selectedAgentId: function watchAgent() {
      this.loadChannels();
    }
  },
  mounted: function mounted() {
    this.loadChannels();
  },
  methods: {
    channelIcon: function channelIcon(name) {
      return (CHANNEL_META[name] && CHANNEL_META[name].icon) || "el-icon-connection";
    },
    channelLabel: function channelLabel(name) {
      return (CHANNEL_META[name] && CHANNEL_META[name].label) || name;
    },
    channelDesc: function channelDesc(name) {
      return (CHANNEL_META[name] && CHANNEL_META[name].desc) || "";
    },
    async loadChannels() {
      if (!this.selectedAgentId) { return; }
      this.loading = true;
      try {
        const res = await http.get("/agents/" + encodeURIComponent(this.selectedAgentId) + "/channels");
        this.channels = res.channels || [];
      } catch (e) {
        this.$message.error("加载频道失败：" + e.message);
      } finally {
        this.loading = false;
      }
    },
    async toggleChannel(ch, enabled) {
      this.toggling = ch.name;
      const action = enabled ? "enable" : "disable";
      try {
        await http.post("/agents/" + encodeURIComponent(this.selectedAgentId) + "/channels/" + ch.name + "/" + action);
        ch.enabled = enabled;
        this.$message.success((enabled ? "已启用" : "已停用") + "：" + this.channelLabel(ch.name));
      } catch (e) {
        this.$message.error("操作失败：" + e.message);
      } finally {
        this.toggling = "";
      }
    }
  }
};
</script>

<style scoped>
.channels-page { display: flex; flex-direction: column; gap: 14px; }
.channels-bar { display: flex; justify-content: flex-end; }
.page-empty { display: flex; align-items: center; justify-content: center; height: 300px; }

.channels-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.channel-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  background: var(--surface);
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  transition: background 0.15s;
}

.channel-item:hover { background: var(--surface-strong); }

.channel-item__icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--surface-highlight);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.channel-item__info {
  flex: 1;
  min-width: 0;
}

.channel-item__name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.channel-item__desc {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

.channel-item__status {
  flex-shrink: 0;
}

.channel-item__actions {
  flex-shrink: 0;
  padding-left: 8px;
}
</style>
