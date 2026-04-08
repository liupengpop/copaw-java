const apiTarget = process.env.VUE_APP_API_TARGET || "http://localhost:8080";

module.exports = {
  productionSourceMap: false,
  devServer: {
    host: "0.0.0.0",
    port: 18081,
    proxy: {
      "^/api": {
        target: apiTarget,
        changeOrigin: true
      }
    }
  }
};
