<template>
  <div class="agents-page">
    <div class="agents-toolbar">
      <el-button size="small" icon="el-icon-refresh" :loading="loading" @click="loadAgents">刷新</el-button>
      <el-button type="primary" size="small" icon="el-icon-plus" @click="openCreateDialog">+ 创建智能体</el-button>
    </div>

    <el-table v-loading="loading" :data="agents" border>
      <el-table-column label="名称" min-width="160">
        <template slot-scope="scope">
          <div class="agent-name-cell">
            <i class="el-icon-document" style="margin-right:6px;color:var(--text-soft)"></i>
            <strong>{{ scope.row.name }}</strong>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="ID" min-width="200" />
      <el-table-column label="描述" min-width="220">
        <template slot-scope="scope">
          <span class="desc-cell">{{ scope.row.description }}</span>
        </template>
      </el-table-column>
      <el-table-column label="工作区路径" min-width="280">
        <template slot-scope="scope">
          <span class="workspace-cell">{{ scope.row.workspaceDir }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template slot-scope="scope">
          <el-button size="mini" icon="el-icon-edit" circle title="编辑" @click="openEditDialog(scope.row)" />
          <el-button size="mini" icon="el-icon-refresh" circle title="热重载" @click="reloadAgent(scope.row)" />
          <el-button size="mini" icon="el-icon-delete" circle type="danger" plain title="删除" @click="removeAgent(scope.row)" />
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建对话框 -->
    <el-dialog title="新建智能体" :visible.sync="createDialogVisible" width="500px">
      <el-form ref="createForm" :model="createForm" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" placeholder="例如：default" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="Workspace">
          <el-input v-model="createForm.workspaceDir" placeholder="为空则使用默认路径" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="createAgent">创建</el-button>
      </span>
    </el-dialog>

    <!-- 编辑对话框 -->
    <el-dialog title="编辑智能体" :visible.sync="editDialogVisible" width="500px">
      <el-form ref="editForm" :model="editForm" label-width="100px">
        <el-form-item label="名称">
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="editForm.enabled" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="saveAgent">保存</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { agentsApi } from "@/services/api";
import { normalizeAgent } from "@/utils/format";

export default {
  name: "AgentsPage",
  data: function data() {
    return {
      loading: false,
      submitting: false,
      createDialogVisible: false,
      editDialogVisible: false,
      agents: [],
      createForm: { name: "", description: "", workspaceDir: "" },
      editForm: { id: "", name: "", description: "", enabled: true }
    };
  },
  mounted: function mounted() {
    this.loadAgents();
  },
  methods: {
    async loadAgents() {
      this.loading = true;
      try {
        const res = await agentsApi.list();
        this.agents = (res.agents || []).map(normalizeAgent);
        this.$store.commit("setAgents", this.agents);
      } catch (e) {
        this.$message.error("加载智能体失败：" + e.message);
      } finally {
        this.loading = false;
      }
    },
    openCreateDialog: function openCreateDialog() {
      this.createForm = { name: "", description: "", workspaceDir: "" };
      this.createDialogVisible = true;
    },
    openEditDialog: function openEditDialog(agent) {
      this.editForm = { id: agent.id, name: agent.name, description: agent.description || "", enabled: agent.enabled };
      this.editDialogVisible = true;
    },
    async createAgent() {
      if (!this.createForm.name) { this.$message.warning("名称不能为空"); return; }
      this.submitting = true;
      try {
        const res = await agentsApi.create({ name: this.createForm.name, description: this.createForm.description, workspaceDir: this.createForm.workspaceDir || undefined });
        this.$message.success("已创建：" + res.id);
        this.createDialogVisible = false;
        await this.loadAgents();
      } catch (e) {
        this.$message.error("创建失败：" + e.message);
      } finally {
        this.submitting = false;
      }
    },
    async saveAgent() {
      if (!this.editForm.id) { return; }
      this.submitting = true;
      try {
        await agentsApi.update(this.editForm.id, { name: this.editForm.name, description: this.editForm.description, enabled: this.editForm.enabled });
        this.$message.success("已保存");
        this.editDialogVisible = false;
        await this.loadAgents();
      } catch (e) {
        this.$message.error("保存失败：" + e.message);
      } finally {
        this.submitting = false;
      }
    },
    async reloadAgent(agent) {
      try {
        await agentsApi.reload(agent.id);
        this.$message.success("热重载已触发：" + agent.id);
        await this.loadAgents();
      } catch (e) {
        this.$message.error("重载失败：" + e.message);
      }
    },
    async removeAgent(agent) {
      const ok = await this.$confirm("确认删除 " + agent.name + "？此操作不可恢复。", "删除确认", { type: "warning" })
        .then(function () { return true; }).catch(function () { return false; });
      if (!ok) { return; }
      try {
        await agentsApi.remove(agent.id, false);
        this.$message.success("已删除：" + agent.id);
        if (this.$store.state.selectedAgentId === agent.id) {
          this.$store.commit("setSelectedAgentId", "");
        }
        await this.loadAgents();
      } catch (e) {
        this.$message.error("删除失败：" + e.message);
      }
    }
  }
};
</script>

<style scoped>
.agents-page { display: flex; flex-direction: column; gap: 14px; }
.agents-toolbar { display: flex; justify-content: flex-end; gap: 8px; }
.agent-name-cell { display: flex; align-items: center; }
.desc-cell { color: var(--text-muted); font-size: 13px; }
.workspace-cell { font-size: 12px; color: var(--text-muted); word-break: break-all; }
</style>
