// utils/api.js —— 统一请求层
//
// 两条通道，页面代码完全不用关心走哪条：
//   useCloud = true  →  wx.cloud.callFunction('api')，云函数再转发到后端
//                       （小程序端无需配置服务器域名、无需 ICP 备案）
//   useCloud = false →  wx.request 直连后端（本地联调，开发者工具勾「不校验合法域名」）
//
// 后端统一响应 { code, msg, data }，code === 0 为成功。
const config = require('../config.js');

const TOKEN_KEY = 'hz_client_token';

function getToken() {
  try {
    return wx.getStorageSync(TOKEN_KEY) || '';
  } catch (e) {
    return '';
  }
}

function setToken(token) {
  try {
    if (token) {
      wx.setStorageSync(TOKEN_KEY, token);
    } else {
      wx.removeStorageSync(TOKEN_KEY);
    }
  } catch (e) {
    /* 忽略存储异常 */
  }
}

function isLoggedIn() {
  return !!getToken();
}

// 直连通道（useCloud=false）使用的后端地址。
// 走云函数通道时不会用到它 —— 那种情况下后端地址由云函数的环境变量 BACKEND_BASE_URL 决定。
function baseUrl() {
  return config.backendDebugUrl;
}

/* ---------- 通道实现 ---------- */

function viaCloud(method, path, data, token) {
  // ⚠️ 这里必须把「同步异常」也转成 rejected Promise。
  // 真机上 wx.cloud 不存在（基础库过低）、或 cloud.init 没成功时，
  // wx.cloud.callFunction 可能**同步抛错**；同步异常不会被下面的 .catch 接住，
  // 会一路冒泡到页面的 onShow/onLoad，导致整页渲染不出来 —— 这正是
  // "模拟器一切正常、真机扫码后白屏" 的典型成因。
  let p;
  try {
    if (!wx.cloud || typeof wx.cloud.callFunction !== 'function') {
      throw new Error('云开发不可用：当前基础库过低，或未初始化成功');
    }
    p = wx.cloud.callFunction({
      name: 'api',
      data: { method: method, path: path, data: data || {}, token: token }
    });
  } catch (e) {
    p = Promise.reject(e);
  }

  return p
    .then(function (res) {
      return res && res.result;
    })
    .catch(function (e) {
      // 保留**原始错误**再包一层：云函数未部署 / 环境 ID 不对 / 超时，
      // 三种情况的原始 errMsg 完全不同，丢掉它会让排查只能靠猜。
      const detail = (e && (e.errMsg || e.message)) || String(e);
      throw new Error(
        '云函数调用失败：请确认已部署云函数 api，且 config.js 的 cloudEnvId 正确（原始错误：' + detail + '）'
      );
    });
}

function viaHttp(method, path, data, token) {
  return new Promise(function (resolve, reject) {
    wx.request({
      url: baseUrl() + path,
      method: method,
      data: data || {},
      header: token ? { 'X-Token': token } : {},
      timeout: 20000,
      success: function (r) {
        resolve(r.data);
      },
      fail: function () {
        reject(new Error('网络连接失败，请检查网络后重试'));
      }
    });
  });
}

/* ---------- 统一解包 ---------- */

function unwrap(body) {
  if (!body || typeof body.code === 'undefined') {
    throw new Error('服务无响应，请稍后重试');
  }
  if (body.code !== 0) {
    // 登录态失效：清掉本地 token，由页面决定跳登录
    if (body.code === 401 || body.code === 403) {
      setToken('');
    }
    const err = new Error(body.msg || '操作失败，请稍后重试');
    err.code = body.code;
    throw err;
  }
  return body.data;
}

function request(method, path, data) {
  const token = getToken();
  const p = config.useCloud ? viaCloud(method, path, data, token) : viaHttp(method, path, data, token);
  return p.then(unwrap);
}

/* ---------- 便捷提示 ---------- */

function toast(title) {
  wx.showToast({ title: title, icon: 'none', duration: 2200 });
}

/* 统一处理「登录失效」：清 token 并跳登录页 */
function gotoLogin() {
  setToken('');
  wx.reLaunch({ url: '/pages/login/login' });
}

module.exports = {
  get: function (path, data) {
    return request('GET', path, data);
  },
  post: function (path, data) {
    return request('POST', path, data);
  },
  getToken: getToken,
  setToken: setToken,
  isLoggedIn: isLoggedIn,
  toast: toast,
  gotoLogin: gotoLogin
};
