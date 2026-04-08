<template>
  <div class="heartbeat-page">
    <el-card shadow="never">
      <el-form v-loading="loading" label-width="110px">
        <el-form-item label="开启心跳">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="执行间隔">
          <div class="interval-row">
            <el-input-number v-model="form.intervalValue" :min="1" :max="999" controls-position="right" style="width:130px" />
            <el-select v-model="form.intervalUnit" style="width:110px">
              <el-option label="分钟" value="minutes" />
              <el-option label="小时" value="hours" />
            </el-select>
          </div>
        </el-form-item>
        <el-form-item label="回复目标">
          <el-select v-model="form.target" style="width:280px">
            <el-option label="静默运行（默认，不发送到频道）" value="silent" />
            <el-option label="发送到默认频道" value="default_channel" />
          </el-select>
        </el-form-item>
        <el-form-item label="活跃时段（可选）">
          <el-switch v-model="form.activeWindowEnabled" />
          <template v-if="form.activeWindowEnabled">
            <div class="active-window-row">
              <el-time-picker v-model="form.activeStart" format="HH:mm" value-format="HH:mm" placeholder="开始" style="width:120px" />
              <span>至</span>
              <el-time-picker v-model="form.activeEnd" format="HH:mm" value-format="HH:mm" placeholder="结束" style="width:120px" />
            </div>
          </template>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script>
import axios from "axios";
import { API_BASE } from "@/services/api";

const http = axios.create({ baseURL: API_BASE, timeout: 15000 });
http.interceptors.response.use(function r(res) { return res.data; });

const DEFAULT_FORM = function () {
  return {
    enabled: false,
    intervalValue: 6,
    intervalUnit: "hours",
    target: "silent",
    activeWindowEnabled: false,
    activeStart: "09:00",
    activeEnd: "22:00"
  };
};

export default {
  name: "HeartbeatPage",
  data: function data() {
    return {
      loading: false,
      saving: false,
      form: DEFAULT_FORM()
    };
  },
  computed: {
    selectedAgentId: function selectedAgentId() {
      return this.$store.state.selectedAgentId;
    }
  },
  watch: {
    selectedAgentId: function watchAgent() {
      this.loadConfig();
    }
  },
  mounted: function mounted() {
    this.loadConfig();
  },
  methods: {
    async loadConfig() {
      if (!this.selectedAgentId) { return; }
      this.loading = true;
      try {
        const res = await http.get("/agents/" + encodeURIComponent(this.selectedAgentId) + "/heartbeat");
        if (res && typeof res === "object") {
          this.form = Object.assign(DEFAULT_FORM(), res);
        }
      } catch (e) {
        // 接口可能不存在，忽略 404
        if (!e.response || e.response.status !== 404) {
          this.$message.error("加载心跳配置失败：" + e.message);
        }
      } finally {
        this.loading = false;
      }
    },
    async save() {
      if (!this.selectedAgentId) { return; }
      this.saving = true;
      try {
        await http.post("/agents/" + encodeURIComponent(this.selectedAgentId) + "/heartbeat", this.form);
        this.$message.success("保存成功");
      } catch (e) {
        this.$message.error("保存失败：" + e.message);
      } finally {
        this.saving = false;
      }
    }
  }
};
</script>

<style scoped>
.heartbeat-page {}
.interval-row { display: flex; gap: 8px; align-items: center; }
.active-window-row { display: flex; gap: 8px; align-items: center; margin-top: 8px; }
</style>
