# CoPaw Java 验证前端

这是一个基于 Vue 2 + Element UI 的轻量验证面板，放在 `copaw-java/copaw-frontend/` 下，用来手工验收当前 `copaw-java` 已打通的 P0/P1 能力。

## 已覆盖的验证路径

- SSE 聊天：`POST /api/console/chat?agentId=`
- 停止聊天：`POST /api/console/chat/stop?agentId=&chatId=`
- Push message 轮询：`GET /api/console/push-messages?agentId=&sessionId=`
- 工具审批：`POST /api/console/approvals/{approvalId}/resolve?agentId=`
- Agent 管理：列表、创建、编辑、启停、热重载、删除
- Cron 管理：列表、创建/更新、暂停、恢复、立即执行、删除
- MCP 客户端：列表、添加、启用、禁用、删除
- Skill 管理：列表、ZIP 导入、启用、禁用、渠道路由更新、删除
- Console 文件上传：`POST /api/console/upload?agentId=`

## 本地运行

先确保 `copaw-java` 后端已经启动，默认监听 `http://localhost:8080`，且 API 前缀为 `/api`。

然后在前端目录执行：

```bash
cd /Users/lmax/WorkBuddy/workbuddy/copaw/copaw-java/copaw-frontend
npm install
npm run serve
```

默认开发端口是 `18081`，浏览器打开：

```text
http://localhost:18081
```

开发模式下通过 `vue.config.js` 把 `/api` 代理到 `http://localhost:8080`，所以不需要单独处理浏览器跨域。

如果你的后端不是 8080，可以这样启动前端：

```bash
cd /Users/lmax/WorkBuddy/workbuddy/copaw/copaw-java/copaw-frontend
VUE_APP_API_TARGET=http://localhost:8081 npm run serve
```

## 页面说明

### 1. 聊天验证

右上角先选 Agent。聊天页支持：

- 输入 `session_id` / `user_id`
- 发起 SSE 流式会话
- 查看 delta 拼接后的助手回复
- 停止当前 chat
- 上传附件并把返回路径插入输入框
- 轮询当前 session 的 push messages
- 展示待审批的工具调用卡片，并直接点“通过 / 拒绝”

注意：push message 后端是 `drain(sessionId)` 的消费式读取，所以前端会把已拉到的消息保存在本地时间线里，避免你一刷新就看不到刚才的审批事件。

### 2. Agent 管理

用于验证多 workspace / 多 agent 生命周期相关接口。支持：

- 创建 agent
- 更新名称、描述、启用状态
- 触发 reload
- 删除配置
- 删除配置并清理 workspace 目录
- 将任意 agent 设为当前验证对象

### 3. Cron 管理

用于验证 `CronManager` 链路。支持：

- 查看现有任务
- 新建 / 编辑任务
- 立即触发任务
- 暂停 / 恢复
- 删除任务

### 4. MCP 客户端

用于验证 stdio / sse / http 三种 transport 的基础配置写入和启停操作。

### 6. Skill 管理

用于验证 manifest 读取、ZIP 导入、启停、渠道路由更新和删除。

## 构建验证结果

已经执行过：

```bash
cd /Users/lmax/WorkBuddy/workbuddy/copaw/copaw-java/copaw-frontend
npm run build
```

结果为构建成功。当前只有 Vue CLI 常见的包体积告警，没有阻塞错误。

## 目录结构

```text
copaw-frontend/
├── public/
├── src/
│   ├── components/
│   ├── router/
│   ├── services/
│   ├── store/
│   ├── styles/
│   ├── utils/
│   └── views/
├── package.json
└── vue.config.js
```
