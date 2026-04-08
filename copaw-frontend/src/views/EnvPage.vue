<template>
  <div class="env-page">
    <div class="env-toolbar">
      <el-button size="small" icon="el-icon-refresh" :loading="loading" @click="loadEnv">刷新</el-button>
      <el-button type="primary" size="small" icon="el-icon-plus" @click="openAdd">新增变量</el-button>
    </div>

    <el-table v-loading="loading" :data="entries" border>
      <el-table-column label="Key" min-width="260">
        <template slot-scope="scope">
          <code class="env-key">{{ scope.row.key }}</code>
        </template>
      </el-table-column>
      <el-table-column label="Value" min-width="300">
        <template slot-scope="scope">
          <span v-if="scope.row.secret" class="env-secret">••••••••</span>
          <span v-else class="env-value">{{ scope.row.value }}</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="90">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.secret" size="mini" type="warning" effect="plain">密钥</el-tag>
          <el-tag v-else size="mini" type="info" effect="plain">普通</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template slot-scope="scope">
          <el-button size="mini" icon="el-icon-edit" circle title="编辑" @click="openEdit(scope.row)" />
          <el-button size="mini" icon="el-icon-delete" circle type="danger" plain title="删除" @click="removeEntry(scope.row)" />
        </template>
      </el-table-column>
    </el-table>

    <div v-if="!loading && !entries.length" class="env-empty">
      <el-empty description="暂无环境变量，点击「新增变量」添加">
        <el-button size="small" type="primary" @click="openAdd">新增变量</el-button>
      </el-empty>
    </div>

    <!-- 新增 / 编辑对话框 -->
    <el-dialog :title="editMode ? '编辑变量' : '新增变量'" :visible.sync="dialogVisible" width="480px" @close="resetForm">
      <el-form :model="form" label-width="60px">
        <el-form-item label="Key">
          <el-input v-model="form.key" :disabled="editMode" placeholder="例如 OPENAI_API_KEY" />
        </el-form-item>
        <el-form-item label="Value">
          <el-input
            v-model="form.value"
            :type="showValue ? 'text' : 'password'"
            placeholder="变量值"
          >
            <el-button slot="append" @click="showValue = !showValue">
              <i :class="showValue ? 'el-icon-view' : 'el-icon-hide'"></i>
            </el-button>
          </el-input>
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import axios from "axios";
import { API_BASE } from "@/services/api";

const http = axios.create({ baseURL: API_BASE, timeout: 15000 });
http.interceptors.response.use(function r(res) { return res.data; });

export default {
  name: "EnvPage",
  data: function data() {
    return {
      loading: false,
      submitting: false,
      dialogVisible: false,
      editMode: false,
      showValue: false,
      entries: [],
      form: { key: "", value: "" }
    };
  },
  mounted: function mounted() {
    this.loadEnv();
  },
  methods: {
    async loadEnv() {
      this.loading = true;
      try {
        const res = await http.get("/env");
        this.entries = res.env || [];
      } catch (e) {
        this.$message.error("加载环境变量失败：" + e.message);
      } finally {
        this.loading = false;
      }
    },
    openAdd: function openAdd() {
      this.editMode = false;
      this.form = { key: "", value: "" };
      this.showValue = false;
      this.dialogVisible = true;
    },
    openEdit: function openEdit(row) {
      this.editMode = true;
      this.form = { key: row.key, value: row.secret ? "" : row.value };
      this.showValue = false;
      this.dialogVisible = true;
    },
    resetForm: function resetForm() {
      this.form = { key: "", value: "" };
      this.showValue = false;
    },
    async submit() {
      if (!this.form.key.trim()) {
        this.$message.warning("Key 不能为空");
        return;
      }
      this.submitting = true;
      try {
        await http.post("/env", { key: this.form.key.trim(), value: this.form.value });
        this.$message.success("已保存：" + this.form.key);
        this.dialogVisible = false;
        await this.loadEnv();
      } catch (e) {
        this.$message.error("保存失败：" + e.message);
      } finally {
        this.submitting = false;
      }
    },
    async removeEntry(row) {
      const ok = await this.$confirm("确认删除环境变量 " + row.key + "？", "删除确认", { type: "warning" })
        .then(function () { return true; }).catch(function () { return false; });
      if (!ok) { return; }
      try {
        await http.delete("/env/" + encodeURIComponent(row.key));
        this.$message.success("已删除：" + row.key);
        await this.loadEnv();
      } catch (e) {
        this.$message.error("删除失败：" + e.message);
      }
    }
  }
};
</script>

<style scoped>
.env-page { display: flex; flex-direction: column; gap: 14px; }
.env-toolbar { display: flex; justify-content: flex-end; gap: 8px; }
.env-empty { padding: 40px; text-align: center; }
.env-key { font-family: monospace; font-size: 13px; color: var(--text); background: var(--surface-strong); padding: 2px 6px; border-radius: 4px; }
.env-value { font-family: monospace; font-size: 13px; color: var(--text-muted); word-break: break-all; }
.env-secret { color: var(--text-soft); letter-spacing: 2px; }
</style>
