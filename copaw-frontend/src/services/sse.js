export function openJsonSse(options) {
  const controller = new AbortController();
  const signal = controller.signal;

  const promise = (async function runStream() {
    try {
      const response = await fetch(options.url, {
        method: options.method || "POST",
        headers: Object.assign(
          {
            Accept: "text/event-stream",
            "Content-Type": "application/json"
          },
          options.headers || {}
        ),
        body: options.body ? JSON.stringify(options.body) : undefined,
        signal: signal,
        credentials: "same-origin"
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || "SSE 请求失败: " + response.status);
      }

      if (!response.body) {
        throw new Error("浏览器不支持可读流响应");
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder("utf-8");
      let buffer = "";

      while (true) {
        const result = await reader.read();
        if (result.done) {
          buffer += decoder.decode();
          flushBuffer(buffer, options.onMessage);
          break;
        }
        buffer += decoder.decode(result.value, { stream: true });
        buffer = consumeBlocks(buffer, options.onMessage);
      }
    } catch (error) {
      if (error && error.name === "AbortError") {
        return;
      }
      if (typeof options.onError === "function") {
        options.onError(error);
      } else {
        throw error;
      }
    } finally {
      if (typeof options.onFinish === "function") {
        options.onFinish();
      }
    }
  })();

  return {
    abort: function abort() {
      controller.abort();
    },
    promise: promise
  };
}

function consumeBlocks(buffer, onMessage) {
  let cursor = buffer.indexOf("\n\n");
  while (cursor !== -1) {
    const block = buffer.slice(0, cursor);
    emitBlock(block, onMessage);
    buffer = buffer.slice(cursor + 2);
    cursor = buffer.indexOf("\n\n");
  }
  return buffer;
}

function flushBuffer(buffer, onMessage) {
  const trimmed = String(buffer || "").trim();
  if (trimmed) {
    emitBlock(trimmed, onMessage);
  }
}

function emitBlock(block, onMessage) {
  const dataLines = [];
  block.split(/\r?\n/).forEach(function eachLine(line) {
    if (line.indexOf("data:") === 0) {
      dataLines.push(line.slice(5).trim());
    }
  });

  if (!dataLines.length || typeof onMessage !== "function") {
    return;
  }

  const payloadText = dataLines.join("\n");
  let payload = payloadText;
  try {
    payload = JSON.parse(payloadText);
  } catch (error) {
    payload = payloadText;
  }
  onMessage(payload);
}
