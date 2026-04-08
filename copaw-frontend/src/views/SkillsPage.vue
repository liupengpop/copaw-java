<template>
  <div>
    <div class="page-header">
      <div>
        <h1 class="page-title">Skill 管理验证</h1>
        <div class="page-subtitle">
          这里直接调用 <code>/api/skills</code>，用于验证技能清单、ZIP 导入、启停、渠道路由更新和删除。
        </div>
      </div>
      <div>
        <el-button icon="el-icon-refresh" @click="loadSkills">刷新</el-button>
        <el-upload
          :show-file-list="false"
          :http-request="uploadSkillZip"
          accept=".zip"
          action="#"
          :disabled="!selectedAgentId"
        >
          <el-button type="primary" icon="el-icon-upload2" :disabled="!selectedAgentId">导入 ZIP</el-button>
        </el-upload>
      </div>
    </div>

    <el-alert
      v-if="!selectedAgentId"
      title="请先在右上角选择一个 Agent，再查看 Skill 清单。"
      type="warning"
      :closable="false"
      show-icon
      class="page-card"
    />

    <el-card shadow="never" class="page-card">
      <el-table v-loading="loading" :data="skills" border>
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="dirName" label="目录" width="140" />
        <el-table-column prop="description" label="描述" min-width="220" />
        <el-table-column label="Tools" min-width="170">
          <template slot-scope="scope">
            <el-tag
              v-for="tool in scope.row.tools || []"
              :key="tool"
              size="mini"
              effect="plain"
              style="margin-right: 6px; margin-bottom: 6px;"
            >{{ tool }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Channels" min-width="160">
          <template slot-scope="scope">
            <span v-if="!(scope.row.channels || []).length" class="muted-text">全部渠道</span>
            <el-tag
              v-for="channel in scope.row.channels || []"
              :key="channel"
              size="mini"
              effect="plain"
              style="margin-right: 6px; margin-bottom: 6px;"
            >{{ channel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="90" align="center">
          <template slot-scope="scope">
            <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small">
              {{ scope.row.enabled ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="260" fixed="right">
          <template slot-scope="scope">
            <el-button
              v-if="scope.row.enabled"
              size="mini"
              type="warning"
              @click="toggleSkill(scope.row, false)"
            >禁用</el-button>
            <el-button
              v-else
              size="mini"
              type="success"
              @click="toggleSkill(scope.row, true)"
            >启用</el-button>
            <el-button size="mini" @click="openChannelsDialog(scope.row)">改渠道</el-button>
            <el-button size="mini" type="danger" @click="removeSkill(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog title="更新渠道路由" :visible.sync="channelsDialogVisible" width="560px">
      <el-form :model="channelsForm" label-width="110px">
        <el-form-item label="Skill 目录">
          <el-input v-model="channelsForm.dirName" disabled />
        </el-form-item>
        <el-form-item label="渠道列表">
          <el-input
            v-model="channelsForm.channelsText"
            type="textarea"
            :rows="4"
            placeholder="每行一个渠道；留空表示全部渠道"
          />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="channelsDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="saveChannels">保存</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { skillsApi } from "@/services/api";
import { toLineArray } from "@/utils/format";

export default {
  name: "SkillsPage",
  data: function data() {
    return {
      loading: false,
      submitting: false,
      skills: [],
      channelsDialogVisible: false,
      channelsForm: {
        dirName: "",
        channelsText: ""
      }
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
        this.loadSkills();
      }
    }
  },
  methods: {
    async loadSkills() {
      if (!this.selectedAgentId) {
        this.skills = [];
        return;
      }
      this.loading = true;
      try {
        const response = await skillsApi.list(this.selectedAgentId);
        this.skills = response.skills || [];
      } catch (error) {
        this.$message.error("加载 Skill 清单失败：" + error.message);
      } finally {
        this.loading = false;
      }
    },
    async toggleSkill(skill, enabled) {
      try {
        if (enabled) {
          await skillsApi.enable(this.selectedAgentId, skill.dirName);
        } else {
          await skillsApi.disable(this.selectedAgentId, skill.dirName);
        }
        this.$message.success((enabled ? "已启用：" : "已禁用：") + skill.dirName);
        await this.loadSkills();
      } catch (error) {
        this.$message.error("操作失败：" + error.message);
      }
    },
    openChannelsDialog(skill) {
      this.channelsForm = {
        dirName: skill.dirName,
        channelsText: (skill.channels || []).join("\n")
      };
      this.channelsDialogVisible = true;
    },
    async saveChannels() {
      this.submitting = true;
      try {
        await skillsApi.updateChannels(
          this.selectedAgentId,
          this.channelsForm.dirName,
          toLineArray(this.channelsForm.channelsText)
        );
        this.$message.success("渠道路由已更新");
        this.channelsDialogVisible = false;
        await this.loadSkills();
      } catch (error) {
        this.$message.error("更新失败：" + error.message);
      } finally {
        this.submitting = false;
      }
    },
    async removeSkill(skill) {
      try {
        await this.$confirm("确认删除 Skill " + skill.dirName + " 吗？", "删除确认", {
          type: "warning"
        });
        await skillsApi.remove(this.selectedAgentId, skill.dirName);
        this.$message.success("已删除：" + skill.dirName);
        await this.loadSkills();
      } catch (error) {
        if (error !== "cancel") {
          this.$message.error("删除失败：" + error.message);
        }
      }
    },
    async uploadSkillZip(request) {
      if (!this.selectedAgentId) {
        this.$message.warning("请先选择 Agent");
        request.onError(new Error("missing agentId"));
        return;
      }
      try {
        const response = await skillsApi.importZip(this.selectedAgentId, request.file);
        this.$message.success("导入成功：" + response.name);
        request.onSuccess(response);
        await this.loadSkills();
      } catch (error) {
        request.onError(error);
        this.$message.error("导入失败：" + error.message);
      }
    }
  }
};
</script>
