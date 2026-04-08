<template>
  <div>
    <div class="page-header">
      <div>
        <h1 class="page-title">模型配置</h1>
        <div class="page-subtitle">
          先从全局视角看所有 Agent 的 <code>active_model</code> 和 <code>running.max_iters</code>，再深入编辑当前 Agent 的 <code>agent.json</code>，保存后自动触发热重载。
        </div>
      </div>
      <div>
        <el-button icon="el-icon-refresh" :loading="summaryLoading" @click="loadAgentSummaries">刷新矩阵</el-button>
        <el-button icon="el-icon-refresh-right" :loading="loading" @click="loadConfig">刷新当前配置</el-button>
        <el-button type="primary" icon="el-icon-check" :loading="saving" :disabled="!selectedAgentId" @click="saveConfig">保存并重载</el-button>
      </div>
    </div>

    <el-card shadow="never" class="page-card">
      <div slot="header" class="flex-between">
        <span>全局 Agent 模型矩阵</span>
        <el-tag effect="plain" type="info">点击任一行即可切换当前编辑目标</el-tag>
      </div>

      <el-alert
        title="Java 运行时当前仍按每个 Agent 独立维护 agent.json，因此这里的“全局模型配置”是统一总览 + 单点编辑，不是单份共享配置中心。"
        type="info"
        :closable="false"
        show-icon
        class="page-card page-card--nested"
      />

      <el-table
        v-loading="summaryLoading"
        :data="agentSummaries"
        border
        :row-class-name="getSummaryRowClassName"
        @row-click="selectAgentFromSummary"
      >
        <el-table-column label="当前" width="84" align="center">
          <template slot-scope="scope">
            <el-tag v-if="scope.row.id === selectedAgentId" size="mini" type="success">当前</el-tag>
            <span v-else class="muted-text">-</span>
          </template>
        </el-table-column>
        <el-table-column label="Agent" min-width="220">
          <template slot-scope="scope">
            <div class="agent-summary-cell">
              <strong>{{ scope.row.name }}</strong>
              <span>{{ scope.row.id }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="providerId" label="Provider" min-width="160" />
        <el-table-column prop="model" label="Model" min-width="220" />
        <el-table-column label="Max Iters" width="110" align="center">
          <template slot-scope="scope">
            {{ scope.row.maxIters }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="150">
          <template slot-scope="scope">
            <div class="agent-summary-status">
              <el-tag size="mini" :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag>
              <el-tag size="mini" :type="scope.row.loaded ? 'success' : 'warning'">{{ scope.row.loaded ? '已加载' : '未加载' }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="读取情况" min-width="200">
          <template slot-scope="scope">
            <span v-if="scope.row.error" class="error-text">{{ scope.row.error }}</span>
            <span v-else class="muted-text">agent.json 已读取</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template slot-scope="scope">
            <el-button size="mini" type="primary" plain @click.stop="selectAgentFromSummary(scope.row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-alert
      v-if="!selectedAgentId"
      title="还没有选中 Agent。先在上面的矩阵里点一行，再进入下面的模型配置编辑区。"
      type="warning"
      :closable="false"
      show-icon
      class="page-card"
    />

    <template v-else>
      <el-row :gutter="16">
        <el-col :xl="15" :lg="14" :md="24">
          <el-card shadow="never" class="page-card">
            <div slot="header" class="flex-between">
              <span>基础模型参数</span>
              <el-tag effect="plain" type="success">Agent：{{ selectedAgentId }}</el-tag>
            </div>

            <el-form label-width="130px">
              <el-form-item label="Provider ID" required>
                <el-select v-model="form.providerId" filterable allow-create default-first-option placeholder="例如 openai-compatible / deepseek / anthropic / ollama" style="width: 100%">
                  <el-option v-for="item in providerOptions" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>

              <el-form-item label="Model" required>
                <el-input v-model="form.model" placeholder="例如 gpt-4o-mini / deepseek-chat / claude-3-5-sonnet / qwen-max / llama3.1" />
              </el-form-item>

              <el-form-item label="Base URL">
                <el-input v-model="form.baseUrl" placeholder="可选，例如 http://localhost:11434/v1 或 OpenAI-compatible 网关地址" />
              </el-form-item>

              <el-form-item label="API Key">
                <el-input v-model="form.apiKey" show-password placeholder="可选；为空则走环境变量或 provider 默认配置" />
              </el-form-item>

              <el-row :gutter="12">
                <el-col :span="12">
                  <el-form-item label="Max Tokens">
                    <el-input-number v-model="form.maxTokens" :min="1" :step="128" :controls="true" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="Temperature">
                    <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="1" :controls="true" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>

              <el-row :gutter="12">
                <el-col :span="12">
                  <el-form-item label="Max Iters">
                    <el-input-number v-model="form.maxIters" :min="1" :step="1" :controls="true" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="文件路径">
                    <el-input value="agent.json" disabled />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-form>
          </el-card>
        </el-col>

        <el-col :xl="9" :lg="10" :md="24">
          <el-card shadow="never" class="page-card">
            <div slot="header" class="flex-between">
              <span>当前选中 Agent 摘要</span>
              <el-tag effect="plain" :type="selectedSummary && selectedSummary.loaded ? 'success' : 'warning'">
                {{ selectedSummary && selectedSummary.loaded ? '运行时已加载' : '运行时待刷新' }}
              </el-tag>
            </div>
            <div v-if="!selectedSummary">
              <el-empty description="还没有该 Agent 的汇总信息。" :image-size="90" />
            </div>
            <div v-else class="summary-card">
              <div class="summary-card__item">
                <span>Agent</span>
                <strong>{{ selectedSummary.name }} ({{ selectedSummary.id }})</strong>
              </div>
              <div class="summary-card__item">
                <span>Workspace</span>
                <strong>{{ selectedSummary.workspaceDir || '-' }}</strong>
              </div>
              <div class="summary-card__item">
                <span>Provider / Model</span>
                <strong>{{ selectedSummary.providerId || '-' }} / {{ selectedSummary.model || '-' }}</strong>
              </div>
              <div class="summary-card__item">
                <span>Max Iters</span>
                <strong>{{ selectedSummary.maxIters }}</strong>
              </div>
            </div>
          </el-card>

          <el-card shadow="never" class="page-card">
            <div slot="header" class="flex-between">
              <span>当前 active_model 预览</span>
              <el-tag type="info" effect="plain">保存前可先检查</el-tag>
            </div>
            <pre class="json-block">{{ previewActiveModel }}</pre>
          </el-card>

          <el-card shadow="never" class="page-card">
            <div slot="header" class="flex-between">
              <span>agent.json 原始片段</span>
              <el-tag type="warning" effect="plain">只读预览</el-tag>
            </div>
            <pre class="json-block raw-json-block">{{ rawContent || '{}' }}</pre>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script>
import { agentsApi } from "@/services/api";
import { normalizeAgent, parseJsonText, prettyJson } from "@/utils/format";

function createEmptyForm() {
  return {
    providerId: "",
    model: "",
    baseUrl: "",
    apiKey: "",
    maxTokens: null,
    temperature: null,
    maxIters: 30
  };
}

function cloneJson(value) {
  return JSON.parse(JSON.stringify(value || {}));
}

function normalizeString(value) {
  return String(value == null ? "" : value).trim();
}

function normalizeNullableNumber(value) {
  return typeof value === "number" && !Number.isNaN(value) ? value : null;
}

function readModelForm(config) {
  const activeModel = config && config.active_model && typeof config.active_model === "object"
    ? config.active_model
    : {};
  const routingLocal = config && config.llm_routing && config.llm_routing.local && typeof config.llm_routing.local === "object"
    ? config.llm_routing.local
    : {};

  const source = activeModel.provider_id || activeModel.model || activeModel.base_url || activeModel.api_key || activeModel.max_tokens || activeModel.temperature != null
    ? activeModel
    : routingLocal;

  const running = config && config.running && typeof config.running === "object"
    ? config.running
    : {};

  return {
    providerId: source.provider_id || "",
    model: source.model || "",
    baseUrl: source.base_url || "",
    apiKey: source.api_key || "",
    maxTokens: normalizeNullableNumber(source.max_tokens),
    temperature: normalizeNullableNumber(source.temperature),
    maxIters: normalizeNullableNumber(running.max_iters) || 30
  };
}

function buildAgentSummary(agent, parsed) {
  const form = readModelForm(parsed || {});
  return {
    id: agent.id,
    name: agent.name,
    workspaceDir: agent.workspaceDir,
    enabled: agent.enabled,
    loaded: agent.loaded,
    providerId: form.providerId || "-",
    model: form.model || "-",
    maxIters: form.maxIters || 30,
    error: ""
  };
}

export default {
  name: "ModelConfigPage",
  data: function data() {
    return {
      loading: false,
      saving: false,
      summaryLoading: false,
      rawContent: "",
      configObject: {},
      form: createEmptyForm(),
      agentSummaries: [],
      providerOptions: [
        "openai-compatible",
        "openai",
        "deepseek",
        "dashscope",
        "moonshot",
        "kimi",
        "anthropic",
        "ollama",
        "openrouter",
        "siliconflow",
        "vllm"
      ]
    };
  },
  computed: {
    selectedAgentId: function selectedAgentId() {
      return this.$store.state.selectedAgentId;
    },
    selectedSummary: function selectedSummary() {
      for (let i = 0; i < this.agentSummaries.length; i += 1) {
        if (this.agentSummaries[i].id === this.selectedAgentId) {
          return this.agentSummaries[i];
        }
      }
      return null;
    },
    previewActiveModel: function previewActiveModel() {
      return prettyJson({
        provider_id: normalizeString(this.form.providerId),
        model: normalizeString(this.form.model),
        base_url: normalizeString(this.form.baseUrl) || null,
        api_key: normalizeString(this.form.apiKey) || null,
        max_tokens: normalizeNullableNumber(this.form.maxTokens),
        temperature: normalizeNullableNumber(this.form.temperature),
        running: {
          max_iters: normalizeNullableNumber(this.form.maxIters) || 30
        }
      });
    }
  },
  watch: {
    selectedAgentId: function watchAgent() {
      this.loadConfig();
    }
  },
  mounted: function mounted() {
    this.loadAgentSummaries();
    this.loadConfig();
  },
  methods: {
    getSummaryRowClassName: function getSummaryRowClassName(payload) {
      return payload && payload.row && payload.row.id === this.selectedAgentId ? "current-agent-row" : "";
    },
    selectAgentFromSummary(agent) {
      if (!agent || !agent.id) {
        return;
      }
      this.$store.commit("setSelectedAgentId", agent.id);
    },
    async loadAgentSummaries() {
      this.summaryLoading = true;
      try {
        const response = await agentsApi.list();
        const agents = (response.agents || []).map(normalizeAgent);
        this.$store.commit("setAgents", agents);
        if (!this.$store.state.selectedAgentId && agents[0]) {
          this.$store.commit("setSelectedAgentId", agents[0].id);
        }

        const summaries = await Promise.all(agents.map(async function mapAgent(agent) {
          try {
            const fileResponse = await agentsApi.getFile(agent.id, "agent.json");
            const parsed = parseJsonText(fileResponse.content || "{}", {});
            return buildAgentSummary(agent, parsed);
          } catch (error) {
            return {
              id: agent.id,
              name: agent.name,
              workspaceDir: agent.workspaceDir,
              enabled: agent.enabled,
              loaded: agent.loaded,
              providerId: "-",
              model: "-",
              maxIters: "-",
              error: error.message || "读取失败"
            };
          }
        }));

        this.agentSummaries = summaries;
      } catch (error) {
        this.$message.error("加载模型矩阵失败：" + error.message);
      } finally {
        this.summaryLoading = false;
      }
    },
    async loadConfig() {
      if (!this.selectedAgentId) {
        this.rawContent = "";
        this.configObject = {};
        this.form = createEmptyForm();
        return;
      }
      this.loading = true;
      try {
        const response = await agentsApi.getFile(this.selectedAgentId, "agent.json");
        const content = response.content || "{}";
        const parsed = parseJsonText(content, {});
        this.rawContent = prettyJson(parsed);
        this.configObject = parsed;
        this.form = readModelForm(parsed);
      } catch (error) {
        this.$message.error("加载模型配置失败：" + error.message);
      } finally {
        this.loading = false;
      }
    },
    buildNextConfig() {
      const nextConfig = cloneJson(this.configObject);
      if (!nextConfig.active_model || typeof nextConfig.active_model !== "object" || Array.isArray(nextConfig.active_model)) {
        nextConfig.active_model = {};
      }
      if (!nextConfig.running || typeof nextConfig.running !== "object" || Array.isArray(nextConfig.running)) {
        nextConfig.running = {};
      }

      const providerId = normalizeString(this.form.providerId);
      const model = normalizeString(this.form.model);
      if (!providerId || !model) {
        throw new Error("provider_id 和 model 都必填");
      }

      nextConfig.active_model.provider_id = providerId;
      nextConfig.active_model.model = model;

      const baseUrl = normalizeString(this.form.baseUrl);
      const apiKey = normalizeString(this.form.apiKey);
      const maxTokens = normalizeNullableNumber(this.form.maxTokens);
      const temperature = normalizeNullableNumber(this.form.temperature);
      const maxIters = normalizeNullableNumber(this.form.maxIters) || 30;

      if (baseUrl) {
        nextConfig.active_model.base_url = baseUrl;
      } else {
        delete nextConfig.active_model.base_url;
      }

      if (apiKey) {
        nextConfig.active_model.api_key = apiKey;
      } else {
        delete nextConfig.active_model.api_key;
      }

      if (maxTokens != null) {
        nextConfig.active_model.max_tokens = maxTokens;
      } else {
        delete nextConfig.active_model.max_tokens;
      }

      if (temperature != null) {
        nextConfig.active_model.temperature = temperature;
      } else {
        delete nextConfig.active_model.temperature;
      }

      nextConfig.running.max_iters = maxIters;

      if (nextConfig.llm_routing && nextConfig.llm_routing.local && typeof nextConfig.llm_routing.local === "object") {
        nextConfig.llm_routing.local.provider_id = providerId;
        nextConfig.llm_routing.local.model = model;
      }

      return nextConfig;
    },
    async saveConfig() {
      if (!this.selectedAgentId) {
        this.$message.warning("请先选择 Agent");
        return;
      }

      this.saving = true;
      try {
        const nextConfig = this.buildNextConfig();
        const content = JSON.stringify(nextConfig, null, 2);
        await agentsApi.saveFile(this.selectedAgentId, "agent.json", content);
        await agentsApi.reload(this.selectedAgentId);
        this.rawContent = content;
        this.configObject = nextConfig;
        this.$message.success("模型配置已保存，并已触发 Agent 热重载");
        await this.loadAgentSummaries();
      } catch (error) {
        this.$message.error("保存模型配置失败：" + error.message);
      } finally {
        this.saving = false;
      }
    }
  }
};
</script>

<style scoped>
.page-card--nested {
  margin-bottom: 16px;
}

.agent-summary-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.agent-summary-cell strong {
  color: var(--text);
}

.agent-summary-cell span {
  color: var(--text-soft);
  font-size: 12px;
}

.agent-summary-status {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.error-text {
  color: var(--danger, #f56c6c);
}

.summary-card {
  display: grid;
  gap: 12px;
}

.summary-card__item {
  padding: 14px 16px;
  border-radius: 16px;
  background: var(--surface-subtle);
  border: 1px solid var(--border-soft);
}

.summary-card__item span {
  display: block;
  font-size: 12px;
  color: var(--text-soft);
}

.summary-card__item strong {
  display: block;
  margin-top: 6px;
  color: var(--text);
  line-height: 1.6;
  word-break: break-word;
}

.raw-json-block {
  max-height: 520px;
  overflow: auto;
}

::v-deep .current-agent-row > td {
  background: rgba(255, 157, 77, 0.08) !important;
}
</style>
