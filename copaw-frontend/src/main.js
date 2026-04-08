import Vue from "vue";
import ElementUI from "element-ui";
import "element-ui/lib/theme-chalk/index.css";
import App from "./App.vue";
import router from "./router";
import store from "./store";
import "./styles/global.css";

const THEME_STORAGE_KEY = "copaw-frontend-theme";

function applyInitialTheme() {
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY) || "system";
    const prefersDark = typeof window.matchMedia === "function"
      && window.matchMedia("(prefers-color-scheme: dark)").matches;
    const theme = stored === "light" || stored === "dark"
      ? stored
      : (prefersDark ? "dark" : "light");
    document.documentElement.setAttribute("data-theme", theme);
  } catch (error) {
    document.documentElement.setAttribute("data-theme", "light");
  }
}

applyInitialTheme();
Vue.use(ElementUI);
Vue.config.productionTip = false;

new Vue({
  router,
  store,
  render: function render(h) {
    return h(App);
  }
}).$mount("#app");
