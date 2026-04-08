<template>
  <div>
    <div class="page-header">
      <div>
        <h1 class="page-title">MCP 客户端验证</h1>
        <div class="page-subtitle">
          用于验证 <code>/api/mcp</code> 的列表、添加、启用、禁用和删除。当前后端未显式暴露更新接口，因此页面采用“新增 + 启停 + 删除”的验证方式。
        </div>
      </div>
      <div>
        <el-button icon="el-icon-refresh" @click="loadClients">刷新</el-button>
        <el-button type="primary" icon="el-icon-plus" :disabled="!selectedAgentId" @click="openDialog">新增客户端</el-button>
      </div>
    </div>

    <el-alert
      v-if="!selectedAgentId"
      title="请先在右上角选择一个 Agent，再查看 MCP 客户端。"
      type="warning"
      :closable="false"
      show-icon
      class="page-card"
    />

    <el-card shadow="never" class="page-card">
      <el-table v-loading="loading" :data="clients" border>
        <el-table-column prop="id" label="ID" width="130" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="transport" label="Transport" width="100" />
        <el-table-column label="命令 / URL" min-width="240">
          <template slot-scope="scope">
            <div v-if="scope.row.transport === 'stdio'">
              <div>{{ scope.row.command || '-' }}</div>
              <div class="muted-text">{{ (scope.row.args || []).join(' ') }}</div>
            </div>
            <div v-else>{{ scope.row.url || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="90" align="center">
          <template slot-scope="scope">
            <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small">
              {{ scope.row.enabled ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="210" fixed="right">
          <template slot-scope="scope">
            <el-button
              v-if="scope.row.enabled"
              size="mini"
              type="warning"
              @click="disableClient(scope.row)"
            >禁用</el-button>
            <el-button
              v-else
              size="mini"
              type="success"
              @click="enableClient(scope.row)"
            >启用</el-button>
            <el-button size="mini" type="danger" @click="removeClient(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog title="新增 MCP 客户端" :visible.sync="dialogVisible" width="720px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="客户端 ID" required>
          <el-input v-model="form.id" placeholder="例如：filesystem" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="Transport">
          <el-radio-group v-model="form.transport">
            <el-radio-button label="stdio" />
            <el-radio-button label="sse" />
            <el-radio-button label="http" />
          </el-radio-group>
        </el-form-item>
        <template v-if="form.transport === 'stdio'">
          <el-form-item label="命令" required>
            <el-input v-model="form.command" placeholder="例如：npx" />
          </el-form-item>
          <el-form-item label="参数">
            <el-input v-model="form.argsText" type="textarea" :rows="3" placeholder="每行一个参数，或逗号分隔" />
          </el-form-item>
          <el-form-item label="环境变量 JSON">
            <el-input v-model="form.envText" type="textarea" :rows="4" placeholder='例如：{"API_KEY":"xxx"}' />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="URL" required>
            <el-input v-model="form.url" placeholder="例如：http://localhost:3000/sse" />
          </el-form-item>
          <el-form-item label="Headers JSON">
            <el-input v-model="form.headersText" type="textarea" :rows="4" placeholder='例如：{"Authorization":"Bearer xxx"}' />
          </el-form-item>
        </template>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="saveClient">添加</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { mcpApi } from "@/services/api";
import { parseJsonText, prettyJson, toLineArray } from "@/utils/format";

function createEmptyForm() {
  return {
    id: "",
    name: "",
    description: "",
    transport: "stdio",
    command: "",
    argsText: "",
    envText: "{}",
    url: "",
    headersText: "{}",
    enabled: true
  };
}

export default {
  name: "McpPage",
  data: function data() {
    return {
      loading: false,
      submitting: false,
      dialogVisible: false,
      clients: [],
      form: createEmptyForm()
    };
  },
  computed: {
    selectedAgentId: function selectedAgentId() {
      return this.$store.state.selectedAgentId;
    }
  },
  watch: {
    selectedAgentId: {
      immediate: true,
      handler: function onAgentChange() {
        this.loadClients();
      }
    }
  },
  methods: {
    prettyJson: prettyJson,
    async loadClients() {
      if (!this.selectedAgentId) {
        this.clients = [];
        return;
      }
      this.loading = true;
      try {
        const response = await mcpApi.list(this.selectedAgentId);
        this.clients = response.clients || [];
      } catch (error) {
        this.$message.error("加载 MCP 客户端失败：" + error.message);
      } finally {
        this.loading = false;
      }
    },
    openDialog() {
      this.form = createEmptyForm();
      this.dialogVisible = true;
    },
    async saveClient() {
      if (!this.selectedAgentId) {
        this.$message.warning("请先选择 Agent");
        return;
      }
      if (!this.form.id) {
        this.$message.warning("客户端 ID 必填");
        return;
      }
      if (this.form.transport === "stdio" && !this.form.command) {
        this.$message.warning("stdio 模式下命令必填");
        return;
      }
      if (this.form.transport !== "stdio" && !this.form.url) {
        this.$message.warning("非 stdio 模式下 URL 必填");
        return;
      }
      this.submitting = true;
      try {
        await mcpApi.add(this.selectedAgentId, {
          id: this.form.id,
          name: this.form.name,
          description: this.form.description,
          transport: this.form.transport,
          command: this.form.transport === "stdio" ? this.form.command : "",
          args: this.form.transport === "stdio" ? toLineArray(this.form.argsText) : [],
          env: this.form.transport === "stdio" ? parseJsonText(this.form.envText, {}) : {},
          url: this.form.transport === "stdio" ? "" : this.form.url,
          headers: this.form.transport === "stdio" ? {} : parseJsonText(this.form.headersText, {}),
          enabled: this.form.enabled
        });
        this.$message.success("MCP 客户端已添加");
        this.dialogVisible = false;
        await this.loadClients();
      } catch (error) {
        this.$message.error("添加失败：" + error.message);
      } finally {
        this.submitting = false;
      }
    },
    async enableClient(client) {
      try {
        await mcpApi.enable(this.selectedAgentId, client.id);
        this.$message.success("已启用：" + client.id);
        await this.loadClients();
      } catch (error) {
        this.$message.error("启用失败：" + error.message);
      }
    },
    async disableClient(client) {
      try {
        await mcpApi.disable(this.selectedAgentId, client.id);
        this.$message.success("已禁用：" + client.id);
        await this.loadClients();
      } catch (error) {
        this.$message.error("禁用失败：" + error.message);
      }
    },
    async removeClient(client) {
      try {
        await this.$confirm("确认删除 MCP 客户端 " + client.id + " 吗？", "删除确认", {
          type: "warning"
        });
        await mcpApi.remove(this.selectedAgentId, client.id);
        this.$message.success("已删除：" + client.id);
        await this.loadClients();
      } catch (error) {
        if (error !== "cancel") {
          this.$message.error("删除失败：" + error.message);
        }
      }
    }
  }
};
</script>
