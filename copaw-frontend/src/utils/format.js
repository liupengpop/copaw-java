export function generateId(prefix) {
  return (prefix || "id_") + Math.random().toString(16).slice(2, 10);
}

export function formatDateTime(value) {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const hour = String(date.getHours()).padStart(2, "0");
  const minute = String(date.getMinutes()).padStart(2, "0");
  const second = String(date.getSeconds()).padStart(2, "0");
  return year + "-" + month + "-" + day + " " + hour + ":" + minute + ":" + second;
}

export function prettyJson(value) {
  try {
    return JSON.stringify(value, null, 2);
  } catch (error) {
    return String(value || "");
  }
}

export function toLineArray(text) {
  return String(text || "")
    .split(/[\n,]+/)
    .map(function trimItem(item) {
      return item.trim();
    })
    .filter(Boolean);
}

export function parseJsonText(text, fallback) {
  if (!text || !String(text).trim()) {
    return fallback;
  }
  const parsed = JSON.parse(text);
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("请输入 JSON 对象");
  }
  return parsed;
}

export function normalizeAgent(agent) {
  return {
    id: agent.id,
    name: agent.name || agent.id,
    workspaceDir: agent.workspaceDir || agent.workspace_dir || "",
    enabled: agent.enabled !== false,
    loaded: Boolean(agent.loaded)
  };
}

export function statusTagType(status) {
  const normalized = String(status || "").toUpperCase();
  if (normalized === "ACTIVE") {
    return "success";
  }
  if (normalized === "PAUSED") {
    return "warning";
  }
  if (normalized === "ERROR") {
    return "danger";
  }
  return "info";
}

export function severityTagType(severity) {
  const normalized = String(severity || "").toUpperCase();
  if (normalized === "CRITICAL") {
    return "danger";
  }
  if (normalized === "HIGH") {
    return "warning";
  }
  if (normalized === "MEDIUM") {
    return "";
  }
  if (normalized === "LOW") {
    return "success";
  }
  return "info";
}

export function pushMessageTagType(type) {
  if (type === "tool_approval_required") {
    return "warning";
  }
  if (type === "tool_approval_resolved") {
    return "success";
  }
  if (type === "tool_approval_timeout" || type === "tool_approval_cancelled" || type === "tool_denied") {
    return "danger";
  }
  return "info";
}

export function stringifyContent(value) {
  if (value == null) {
    return "";
  }
  if (typeof value === "string") {
    return value;
  }
  return prettyJson(value);
}
