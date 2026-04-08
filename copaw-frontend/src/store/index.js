import Vue from "vue";
import Vuex from "vuex";

Vue.use(Vuex);

const STORAGE_KEY = "copaw-frontend-state";

function createSessionId() {
  return "console-" + Math.random().toString(16).slice(2, 10);
}

function loadPersistedState() {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch (error) {
    return {};
  }
}

const persistedState = loadPersistedState();

const store = new Vuex.Store({
  state: {
    selectedAgentId: persistedState.selectedAgentId || "",
    sessionId: persistedState.sessionId || createSessionId(),
    userId: persistedState.userId || "console-user",
    agents: []
  },
  getters: {
    selectedAgent: function selectedAgent(state) {
      return state.agents.find(function findAgent(agent) {
        return agent.id === state.selectedAgentId;
      }) || null;
    }
  },
  mutations: {
    setSelectedAgentId: function setSelectedAgentId(state, agentId) {
      state.selectedAgentId = agentId || "";
    },
    setSessionId: function setSessionId(state, sessionId) {
      state.sessionId = sessionId || createSessionId();
    },
    regenerateSessionId: function regenerateSessionId(state) {
      state.sessionId = createSessionId();
    },
    setUserId: function setUserId(state, userId) {
      state.userId = userId || "console-user";
    },
    setAgents: function setAgents(state, agents) {
      state.agents = Array.isArray(agents) ? agents : [];
      if (state.selectedAgentId) {
        const exists = state.agents.some(function someAgent(agent) {
          return agent.id === state.selectedAgentId;
        });
        if (!exists) {
          state.selectedAgentId = state.agents[0] ? state.agents[0].id : "";
        }
      }
    }
  }
});

store.subscribe(function persistStore(mutation, state) {
  window.localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      selectedAgentId: state.selectedAgentId,
      sessionId: state.sessionId,
      userId: state.userId
    })
  );
});

export default store;
