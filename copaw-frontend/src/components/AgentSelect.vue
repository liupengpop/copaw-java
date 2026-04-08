<template>
  <div class="agent-select">
    <el-select
      :value="value"
      size="small"
      filterable
      clearable
      placeholder="选择 Agent"
      :loading="loading"
      style="width: 100%"
      @change="emitSelection"
    >
      <el-option
        v-for="item in options"
        :key="item.id"
        :label="item.name + ' (' + item.id + ')'"
        :value="item.id"
      >
        <div class="option-row">
          <span>{{ item.name }}</span>
          <span class="muted-text">{{ item.id }}</span>
        </div>
      </el-option>
    </el-select>
    <el-button
      size="small"
      icon="el-icon-refresh"
      :loading="loading"
      @click="loadAgents"
    />
  </div>
</template>

<script>
import { agentsApi } from "@/services/api";
import { normalizeAgent } from "@/utils/format";

export default {
  name: "AgentSelect",
  props: {
    value: {
      type: String,
      default: ""
    }
  },
  data: function data() {
    return {
      loading: false,
      options: []
    };
  },
  created: function created() {
    this.loadAgents();
  },
  methods: {
    async loadAgents() {
      this.loading = true;
      try {
        const response = await agentsApi.list();
        this.options = (response.agents || []).map(normalizeAgent);
        this.$store.commit("setAgents", this.options);

        const current = this.value || this.$store.state.selectedAgentId;
        const exists = this.options.some(function hasAgent(agent) {
          return agent.id === current;
        });
        const next = exists ? current : this.options[0] && this.options[0].id;
        if (next && next !== current) {
          this.emitSelection(next);
        }
      } catch (error) {
        this.$message.error("加载 Agent 失败：" + error.message);
      } finally {
        this.loading = false;
      }
    },
    emitSelection(value) {
      const next = value || "";
      this.$store.commit("setSelectedAgentId", next);
      this.$emit("input", next);
      this.$emit("change", next);
    }
  }
};
</script>

<style scoped>
.agent-select {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
}

.option-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
</style>
