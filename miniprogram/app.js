// app.js
const config = require('./config.js');

App({
  globalData: {
    config: config,
    grantee: null,        // 登录后的领取人信息（姓名 / 单位 / 剩余额度）
    cloudReady: false,    // 云开发是否初始化成功（云函数通道的前提）
    cloudError: ''        // 初始化失败原因（供页面给出可读提示）
  },

  onLaunch() {
    // 云开发初始化：仅「云函数转发」通道需要。
    // 走云函数后小程序端不必配置任何服务器域名，也不需要 ICP 备案。
    //
    // ⚠️ 这里必须 try/catch：
    // onLaunch 里抛出的异常会让**整个小程序白屏**，而且真机上没有任何提示，
    // 用户只会看到"空白页"，完全无从排查（模拟器正常、真机白屏的经典成因）。
    // 所以初始化失败只降级为「云函数通道不可用」，页面与提示照常工作。
    if (!config.useCloud) {
      this.globalData.cloudReady = false;
      return;
    }

    try {
      if (!wx.cloud) {
        throw new Error('当前微信版本过低，不支持云开发（需基础库 2.2.3 及以上）');
      }
      if (!config.cloudEnvId || config.cloudEnvId.indexOf('REPLACE_') === 0) {
        throw new Error('config.js 的 cloudEnvId 尚未填写');
      }
      wx.cloud.init({ env: config.cloudEnvId, traceUser: true });
      this.globalData.cloudReady = true;
    } catch (e) {
      this.globalData.cloudReady = false;
      this.globalData.cloudError = (e && e.message) || String(e);
      console.error('[云开发初始化失败] ' + this.globalData.cloudError);
    }
  },

  // 全局错误捕获：真机上看不到控制台，落到本地存储便于后续排查
  onError(err) {
    console.error('[全局错误] ' + err);
    try {
      wx.setStorageSync('hz_last_error', String(err).slice(0, 500));
    } catch (e) {
      /* 忽略存储异常 */
    }
  },

  onUnhandledRejection(res) {
    const msg = (res && res.reason && (res.reason.message || res.reason)) || '未知 Promise 异常';
    console.error('[未处理的 Promise 异常] ' + msg);
    try {
      wx.setStorageSync('hz_last_error', String(msg).slice(0, 500));
    } catch (e) {
      /* 忽略存储异常 */
    }
  }
});
