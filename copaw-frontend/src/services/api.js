import axios from "axios";

export const API_BASE = process.env.VUE_APP_API_BASE || "/api";

const http = axios.create({
  baseURL: API_BASE,
  timeout: 30000
});

http.interceptors.response.use(
  function handleSuccess(response) {
    return response.data;
  },
  function handleError(error) {
    const message =
      (error.response && error.response.data && (error.response.data.message || error.response.data.error)) ||
      error.message ||
      "请求失败";
    return Promise.reject(new Error(message));
  }
);

function buildParams(agentId) {
  return { agentId: agentId };
}

export function buildApiUrl(path) {
  if (!path) {
    return API_BASE;
  }
  if (path.indexOf("http://") === 0 || path.indexOf("https://") === 0) {
    return path;
  }
  return API_BASE.replace(/\/$/, "") + (path.charAt(0) === "/" ? path : "/" + path);
}

export const agentsApi = {
  list: function listAgents() {
    return http.get("/agents");
  },
  create: function createAgent(payload) {
    return http.post("/agents", payload);
  },
  update: function updateAgent(agentId, payload) {
    return http.put("/agents/" + agentId, payload);
  },
  reload: function reloadAgent(agentId) {
    return http.post("/agents/" + agentId + "/reload");
  },
  remove: function removeAgent(agentId, deleteFiles) {
    return http.delete("/agents/" + agentId, {
      params: { deleteFiles: Boolean(deleteFiles) }
    });
  },
  listFiles: function listAgentFiles(agentId, path) {
    const params = {};
    if (path) {
      params.path = path;
    }
    return http.get("/agents/" + encodeURIComponent(agentId) + "/files", { params: params });
  },
  getFile: function getAgentFile(agentId, path) {
    return http.get("/agents/" + encodeURIComponent(agentId) + "/file", {
      params: { path: path }
    });
  },
  saveFile: function saveAgentFile(agentId, path, content) {
    return http.post("/agents/" + encodeURIComponent(agentId) + "/file", {
      path: path,
      content: content
    });
  },
  deleteFile: function deleteAgentFile(agentId, path) {
    return http.delete("/agents/" + encodeURIComponent(agentId) + "/file", {
      params: { path: path }
    });
  }
};

export const chatApi = {
  stopChat: function stopChat(agentId, chatId) {
    return http.post("/console/chat/stop", null, {
      params: {
        agentId: agentId,
        chatId: chatId
      }
    });
  },
  getPushMessages: function getPushMessages(agentId, sessionId) {
    return http.get("/console/push-messages", {
      params: {
        agentId: agentId,
        sessionId: sessionId
      }
    });
  },
  resolveApproval: function resolveApproval(agentId, approvalId, payload) {
    return http.post("/console/approvals/" + approvalId + "/resolve", payload, {
      params: buildParams(agentId)
    });
  },
  uploadFile: function uploadFile(agentId, file) {
    const formData = new FormData();
    formData.append("file", file);
    return http.post("/console/upload", formData, {
      params: buildParams(agentId),
      headers: {
        "Content-Type": "multipart/form-data"
      }
    });
  }
};

export const sessionsApi = {
  list: function listSessions(agentId, params) {
    return http.get("/sessions", {
      params: Object.assign({ agentId: agentId }, params || {})
    });
  },
  getMessages: function getSessionMessages(agentId, chatId) {
    return http.get("/sessions/" + encodeURIComponent(chatId) + "/messages", {
      params: { agentId: agentId }
    });
  }
};

export const cronApi = {
  list: function listJobs(agentId) {
    return http.get("/cron", { params: buildParams(agentId) });
  },
  save: function saveJob(agentId, payload) {
    return http.post("/cron", payload, { params: buildParams(agentId) });
  },
  remove: function removeJob(agentId, jobId) {
    return http.delete("/cron/" + jobId, { params: buildParams(agentId) });
  },
  pause: function pauseJob(agentId, jobId) {
    return http.post("/cron/" + jobId + "/pause", null, { params: buildParams(agentId) });
  },
  resume: function resumeJob(agentId, jobId) {
    return http.post("/cron/" + jobId + "/resume", null, { params: buildParams(agentId) });
  },
  runNow: function runNow(agentId, jobId) {
    return http.post("/cron/" + jobId + "/run", null, { params: buildParams(agentId) });
  }
};

export const mcpApi = {
  list: function listClients(agentId) {
    return http.get("/mcp", { params: buildParams(agentId) });
  },
  add: function addClient(agentId, payload) {
    return http.post("/mcp", payload, { params: buildParams(agentId) });
  },
  enable: function enableClient(agentId, clientId) {
    return http.post("/mcp/" + clientId + "/enable", null, { params: buildParams(agentId) });
  },
  disable: function disableClient(agentId, clientId) {
    return http.post("/mcp/" + clientId + "/disable", null, { params: buildParams(agentId) });
  },
  remove: function removeClient(agentId, clientId) {
    return http.delete("/mcp/" + clientId, { params: buildParams(agentId) });
  }
};

export const skillsApi = {
  list: function listSkills(agentId) {
    return http.get("/skills", { params: buildParams(agentId) });
  },
  enable: function enableSkill(agentId, dirName) {
    return http.post("/skills/" + encodeURIComponent(dirName) + "/enable", null, {
      params: buildParams(agentId)
    });
  },
  disable: function disableSkill(agentId, dirName) {
    return http.post("/skills/" + encodeURIComponent(dirName) + "/disable", null, {
      params: buildParams(agentId)
    });
  },
  updateChannels: function updateChannels(agentId, dirName, channels) {
    return http.put(
      "/skills/" + encodeURIComponent(dirName) + "/channels",
      { channels: channels },
      { params: buildParams(agentId) }
    );
  },
  remove: function removeSkill(agentId, dirName) {
    return http.delete("/skills/" + encodeURIComponent(dirName), {
      params: buildParams(agentId)
    });
  },
  importZip: function importZip(agentId, file) {
    const formData = new FormData();
    formData.append("file", file);
    return http.post("/skills/import-zip", formData, {
      params: buildParams(agentId),
      headers: {
        "Content-Type": "multipart/form-data"
      }
    });
  }
};

export const envApi = {
  list: function listEnv() {
    return http.get("/env");
  },
  upsert: function upsertEnv(key, value) {
    return http.post("/env", { key: key, value: value });
  },
  remove: function removeEnv(key) {
    return http.delete("/env/" + encodeURIComponent(key));
  }
};

export const runtimeApi = {
  get: function getRuntime(agentId) {
    return http.get("/agents/" + encodeURIComponent(agentId) + "/runtime");
  },
  save: function saveRuntime(agentId, config) {
    return http.post("/agents/" + encodeURIComponent(agentId) + "/runtime", config);
  }
};

export const channelsApi = {
  list: function listChannels(agentId) {
    return http.get("/agents/" + encodeURIComponent(agentId) + "/channels");
  },
  enable: function enableChannel(agentId, channel) {
    return http.post("/agents/" + encodeURIComponent(agentId) + "/channels/" + channel + "/enable");
  },
  disable: function disableChannel(agentId, channel) {
    return http.post("/agents/" + encodeURIComponent(agentId) + "/channels/" + channel + "/disable");
  }
};

export default http;
