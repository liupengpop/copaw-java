<template>
  <div>
    <div class="page-header">
      <div>
        <h1 class="page-title">Cron 任务验证</h1>
        <div class="page-subtitle">
          当前页直接对接 <code>/api/cron?agentId=</code>，验证任务列表、创建/更新、暂停、恢复、立即触发与删除。
        </div>
      </div>
      <div>
        <el-button icon="el-icon-refresh" @click="loadJobs">刷新</el-button>
        <el-button type="primary" icon="el-icon-plus" :disabled="!selectedAgentId" @click="openCreateDialog">新建任务</el-button>
      </div>
    </div>

    <el-alert
      v-if="!selectedAgentId"
      title="请先在右上角选择一个 Agent，再操作 Cron 任务。"
      type="warning"
      :closable="false"
      show-icon
      class="page-card"
    />

    <el-card shadow="never" class="page-card">
      <div slot="header" class="flex-between">
        <span>任务列表</span>
        <span class="muted-text">示例表达式：0 */5 * * * *</span>
      </div>
      <el-table v-loading="loading" :data="jobs" border>
        <el-table-column prop="id" label="ID" width="110" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="cronExpression" label="Cron 表达式" min-width="160" />
        <el-table-column prop="message" label="触发消息" min-width="220" />
        <el-table-column prop="sessionId" label="Session" width="120" />
        <el-table-column label="状态" width="100" align="center">
          <template slot-scope="scope">
            <el-tag :type="statusTagType(scope.row.status)" size="small">{{ scope.row.status || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下次执行" min-width="170">
          <template slot-scope="scope">{{ formatDateTime(scope.row.nextRunAt) }}</template>
        </el-table-column>
        <el-table-column label="最近执行" min-width="170">
          <template slot-scope="scope">{{ formatDateTime(scope.row.lastRunAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="310">
          <template slot-scope="scope">
            <el-button size="mini" @click="openEditDialog(scope.row)">编辑</el-button>
            <el-button size="mini" type="success" @click="runNow(scope.row)">立即执行</el-button>
            <el-button
              v-if="scope.row.status === 'PAUSED'"
              size="mini"
              type="warning"
              @click="resumeJob(scope.row)"
            >恢复</el-button>
            <el-button
              v-else
              size="mini"
              type="warning"
              plain
              @click="pauseJob(scope.row)"
            >暂停</el-button>
            <el-button size="mini" type="danger" @click="removeJob(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog :title="editing ? '编辑任务' : '新建任务'" :visible.sync="dialogVisible" width="620px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="任务 ID">
          <el-input v-model="form.id" :disabled="editing" placeholder="为空时后端自动生成" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="Cron 表达式" required>
          <el-input v-model="form.cronExpression" placeholder="例如：0 */5 * * * *" />
        </el-form-item>
        <el-form-item label="触发消息" required>
          <el-input v-model="form.message" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="Session ID">
          <el-input v-model="form.sessionId" placeholder="默认 _cron" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="saveJob">保存</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { cronApi } from "@/services/api";
import { formatDateTime, statusTagType } from "@/utils/format";

function createEmptyForm() {
  return {
    id: "",
    name: "",
    description: "",
    cronExpression: "",
    message: "",
    sessionId: "_cron",
    enabled: true,
    status: "ACTIVE"
  };
}

export default {
  name: "CronPage",
  data: function data() {
    return {
      loading: false,
      dialogVisible: false,
      submitting: false,
      editing: false,
      jobs: [],
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
      handler: function handleAgentChange() {
        this.loadJobs();
      }
    }
  },
  methods: {
    formatDateTime: formatDateTime,
    statusTagType: statusTagType,
    async loadJobs() {
      if (!this.selectedAgentId) {
        this.jobs = [];
        return;
      }
      this.loading = true;
      try {
        const response = await cronApi.list(this.selectedAgentId);
        this.jobs = response.jobs || [];
      } catch (error) {
        this.$message.error("加载 Cron 任务失败：" + error.message);
      } finally {
        this.loading = false;
      }
    },
    openCreateDialog() {
      this.editing = false;
      this.form = createEmptyForm();
      this.dialogVisible = true;
    },
    openEditDialog(job) {
      this.editing = true;
      this.form = Object.assign(createEmptyForm(), job);
      this.dialogVisible = true;
    },
    async saveJob() {
      if (!this.selectedAgentId) {
        this.$message.warning("请先选择 Agent");
        return;
      }
      if (!this.form.name || !this.form.cronExpression || !this.form.message) {
        this.$message.warning("名称、Cron 表达式和触发消息必填");
        return;
      }
      this.submitting = true;
      try {
        await cronApi.save(this.selectedAgentId, {
          id: this.form.id || undefined,
          name: this.form.name,
          description: this.form.description,
          cronExpression: this.form.cronExpression,
          message: this.form.message,
          sessionId: this.form.sessionId || "_cron",
          enabled: this.form.enabled,
          status: this.form.enabled ? "ACTIVE" : "PAUSED"
        });
        this.$message.success("任务已保存");
        this.dialogVisible = false;
        await this.loadJobs();
      } catch (error) {
        this.$message.error("保存失败：" + error.message);
      } finally {
        this.submitting = false;
      }
    },
    async pauseJob(job) {
      try {
        await cronApi.pause(this.selectedAgentId, job.id);
        this.$message.success("已暂停：" + job.id);
        await this.loadJobs();
      } catch (error) {
        this.$message.error("暂停失败：" + error.message);
      }
    },
    async resumeJob(job) {
      try {
        await cronApi.resume(this.selectedAgentId, job.id);
        this.$message.success("已恢复：" + job.id);
        await this.loadJobs();
      } catch (error) {
        this.$message.error("恢复失败：" + error.message);
      }
    },
    async runNow(job) {
      try {
        await cronApi.runNow(this.selectedAgentId, job.id);
        this.$message.success("已触发执行：" + job.id);
        await this.loadJobs();
      } catch (error) {
        this.$message.error("触发失败：" + error.message);
      }
    },
    async removeJob(job) {
      try {
        await this.$confirm("确认删除任务 " + job.id + " 吗？", "删除确认", {
          type: "warning"
        });
        await cronApi.remove(this.selectedAgentId, job.id);
        this.$message.success("已删除：" + job.id);
        await this.loadJobs();
      } catch (error) {
        if (error !== "cancel") {
          this.$message.error("删除失败：" + error.message);
        }
      }
    }
  }
};
</script>
