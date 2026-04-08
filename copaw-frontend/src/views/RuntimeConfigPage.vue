<template>
  <div class="runtime-page">
    <div v-if="!selectedAgentId" class="page-empty">
      <el-empty description="请先选择一个智能体" :image-size="80" />
    </div>

    <template v-else>
      <el-card shadow="never">
        <div slot="header" class="card-header">
          <span>运行时参数</span>
          <el-tag size="small" type="info" effect="plain">Agent：{{ selectedAgentId }}</el-tag>
        </div>

        <el-form v-loading="loading" :model="form" label-width="170px">
          <el-form-item label="最大迭代次数 (max_iters)">
            <el-input-number v-model="form.maxIters" :min="1" :max="200" controls-position="right" style="width:160px" />
            <span class="field-tip">每次请求最多执行多少轮 ReAct 迭代</span>
          </el-form-item>

          <el-form-item label="最大输入长度">
            <el-input-number v-model="form.maxInputLength" :min="1000" :max="200000" :step="1000" controls-position="right" style="width:160px" />
            <span class="field-tip">单次请求最大字符数</span>
          </el-form-item>

          <el-form-item label="并发 LLM 上限">
            <el-input-number v-model="form.maxConcurrentLlm" :min="1" :max="50" controls-position="right" style="width:160px" />
            <span class="field-tip">同时进行的 LLM 请求最大数量</span>
          </el-form-item>

          <el-form-item label="LLM 最大重试次数">
            <el-input-number v-model="form.llmMaxRetries" :min="0" :max="10" controls-position="right" style="width:160px" />
          </el-form-item>

          <el-form-item label="LLM 获取超时 (秒)">
            <el-input-number v-model="form.llmAcquireTimeout" :min="5" :max="300" controls-position="right" style="width:160px" />
          </el-form-item>

          <el-form-item label="记忆压缩阈值">
            <el-input-number v-model="form.memoryCompactThreshold" :min="0" :max="100000" :step="1000" controls-position="right" style="width:160px" />
            <span class="field-tip">0 = 自动；单位：token 数</span>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="saving" @click="save">保存并重载</el-button>
            <el-button @click="loadConfig">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </template>
  </div>
</template>

<script>
import axios from "axios";
import { API_BASE } from "@/services/api";

const http = axios.create({ baseURL: API_BASE, timeout: 15000 });
http.interceptors.response.use(function r(res) { return res.data; });

function defaultForm() {
  return {
    maxIters: 30,
    maxInputLength: 50000,
    maxConcurrentLlm: 5,
    llmMaxRetries: 3,
    llmAcquireTimeout: 30,
    memoryCompactThreshold: 0
  };
}

export default {
  name: "RuntimeConfigPage",
  data: function data() {
    return {
      loading: false,
      saving: false,
      form: defaultForm()
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
        const res = await http.get("/agents/" + encodeURIComponent(this.selectedAgentId) + "/runtime");
        this.form = {
          maxIters: res.maxIters != null ? res.maxIters : 30,
          maxInputLength: res.maxInputLength != null ? res.maxInputLength : 50000,
          maxConcurrentLlm: res.maxConcurrentLlm != null ? res.maxConcurrentLlm : 5,
          llmMaxRetries: res.llmMaxRetries != null ? res.llmMaxRetries : 3,
          llmAcquireTimeout: res.llmAcquireTimeout != null ? res.llmAcquireTimeout : 30,
          memoryCompactThreshold: res.memoryCompactThreshold != null ? res.memoryCompactThreshold : 0
        };
      } catch (e) {
        if (!e.response || e.response.status !== 404) {
          this.$message.error("加载运行配置失败：" + e.message);
        }
      } finally {
        this.loading = false;
      }
    },
    async save() {
      if (!this.selectedAgentId) { return; }
      this.saving = true;
      try {
        await http.post("/agents/" + encodeURIComponent(this.selectedAgentId) + "/runtime", this.form);
        this.$message.success("保存成功，Agent 已重载");
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
.runtime-page {}
.page-empty { display: flex; align-items: center; justify-content: center; height: 300px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.field-tip { font-size: 12px; color: var(--text-soft); margin-left: 10px; }
</style>
