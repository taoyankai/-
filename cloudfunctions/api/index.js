// cloudfunctions/api/index.js
//
// 统一转发云函数：把小程序端的请求转发给现有的 Spring Boot 后端。
//
// 为什么需要它：个人主体小程序不能用 web-view，而且小程序直接 wx.request 要求
// 目标域名在小程序后台「服务器域名」白名单里（需 ICP 备案）。走云函数则完全没有这个限制 ——
// 云函数运行在腾讯云侧，出网请求不受小程序域名白名单约束，因此后端只要有公网地址即可，
// 连域名备案都不需要。
//
// 部署：开发者工具右键 cloudfunctions/api → 「上传并部署：云端安装依赖」
// 环境变量：BACKEND_BASE_URL —— 后端**根地址，不带 /api**（函数会自动补齐）
//   本项目实际值：http://221.192.236.175
//   用 IP + HTTP 也可以：云函数出网不受小程序「服务器域名」白名单约束，因此无需备案域名
//
// 仅使用 Node 内置模块（http/https），运行期**零第三方依赖**。
//
// 这里刻意把 wx-server-sdk 做成**可选加载**：
// 早前它是硬 require，只为调一次 cloud.init()，代价却是整个函数必须在云端
// 安装依赖才能跑 —— 一旦没装（例如用 CLI 部署时没带 --remote-npm-install），
// 调用会直接以 "Cannot find module 'wx-server-sdk'" 执行失败（实测已踩到）。
// 本函数不使用云数据库 / getWXContext / openapi 中的任何能力，所以：
//   装了 → 正常 init（无副作用）；没装 → 照样能转发。
let cloud = null;
try {
  cloud = require('wx-server-sdk');
  if (cloud && typeof cloud.init === 'function') {
    cloud.init({ env: cloud.DYNAMIC_CURRENT_ENV });
  }
} catch (e) {
  // 未安装 wx-server-sdk：纯转发场景不需要，忽略即可
}

const http = require('http');
const https = require('https');
const { URL } = require('url');

// 后端根地址（不含 /api，函数会自动补齐）。
//
// 取值优先级：云函数环境变量 BACKEND_BASE_URL → 下面的内置默认值。
//
// 为什么保留内置默认值（而不是"必须配环境变量"）：
//   云函数的「环境变量」和「超时」属于**环境配置（type=3）**，而 config.json 只能声明
//   permissions / triggers —— 也就是说这两项**只能在云开发控制台手点**（官方文档明示；
//   实测 CLI 部署也不会同步 config.json，写成严格 JSON 同样不生效）。
//   漏配一次环境变量就会把整条免备案通道直接打死，因此这里用显式默认值兜底：
//   要换后端地址时在控制台配 BACKEND_BASE_URL 即可（**环境变量优先**），无需改代码。
const FALLBACK_BACKEND = 'http://221.192.236.175'; // 本项目线上后端根地址（不带 /api）
const BACKEND = (process.env.BACKEND_BASE_URL || FALLBACK_BACKEND).replace(/\/+$/, '');
const TIMEOUT_MS = 15000;

function httpRequest(urlStr, method, body, token) {
  return new Promise(function (resolve, reject) {
    let target;
    try {
      target = new URL(urlStr);
    } catch (e) {
      return reject(new Error('后端地址不合法：' + urlStr));
    }

    const mod = target.protocol === 'https:' ? https : http;
    const payload = body && method !== 'GET' ? JSON.stringify(body) : null;
    const headers = { 'Content-Type': 'application/json; charset=utf-8' };
    if (token) headers['X-Token'] = token;
    if (payload) headers['Content-Length'] = Buffer.byteLength(payload);

    const req = mod.request(
      {
        protocol: target.protocol,
        hostname: target.hostname,
        port: target.port,
        path: target.pathname + target.search,
        method: method,
        headers: headers,
        timeout: TIMEOUT_MS
      },
      function (res) {
        let raw = '';
        res.setEncoding('utf8');
        res.on('data', function (c) {
          raw += c;
        });
        res.on('end', function () {
          try {
            resolve(JSON.parse(raw));
          } catch (e) {
            reject(
              new Error(
                '后端返回的不是 JSON（HTTP ' + res.statusCode + '），' +
                  '请确认 BACKEND_BASE_URL 指向后端根地址，且路径 /api 已由本函数补齐'
              )
            );
          }
        });
      }
    );

    req.on('timeout', function () {
      req.destroy(new Error('请求后端超时，请确认后端已启动且云函数可访问其地址'));
    });
    req.on('error', function (e) {
      reject(e);
    });
    if (payload) req.write(payload);
    req.end();
  });
}

/** GET 参数拼到 query 上（数组/对象按 JSON 处理，本项目用不到复杂结构） */
function toQuery(data) {
  const parts = [];
  Object.keys(data || {}).forEach(function (k) {
    const v = data[k];
    if (v === undefined || v === null || v === '') return;
    parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
  });
  return parts.join('&');
}

exports.main = async function (event) {
  const method = String((event && event.method) || 'GET').toUpperCase();
  const path = (event && event.path) || '';
  const token = (event && event.token) || '';

  if (!path) {
    return { code: 400, msg: '缺少请求路径', data: null };
  }

  // 防御：本项目所有路径都以 / 开头；非法的相对路径可能被拼成
  // http://host/api../../x 这类越界地址，这里直接拒绝而不是交给后端。
  if (path.charAt(0) !== '/') {
    return { code: 400, msg: '请求路径必须以 / 开头', data: null };
  }

  const isGet = method === 'GET';

  // 兜底：只有内置默认值也被清空时才会走到这里（正常配置下不可能命中）。
  // 与其去连一个空地址报难懂的网络错误，不如直接给出可操作的指引。
  if (!BACKEND) {
    return {
      code: 500,
      msg:
        '云函数未配置后端地址：请检查云函数 index.js 里的内置默认后端，或在「云开发控制台 → ' +
        '云函数 → api → 配置 → 环境配置」新增环境变量 BACKEND_BASE_URL' +
        '（填后端根地址，不带 /api），保存后云函数会自动重启',
      data: null
    };
  }

  // 有些调用（如 send-code）把参数直接写在 path 的 query 里，
  // 有些（如 track）也写在 path 里 —— 此时 event.data 通常为空。
  // 只有 path 自身**不含** query 时，才把 event.data 拼成 query，
  // 否则会出现 `?a=1?b=2` 这种畸形 URL。
  const pathHasQuery = path.indexOf('?') >= 0;
  const extra = isGet && !pathHasQuery ? toQuery(event.data) : '';
  const url = BACKEND + '/api' + path + (extra ? '?' + extra : '');

  try {
    return await httpRequest(url, method, isGet ? null : event.data, token);
  } catch (e) {
    return { code: 500, msg: '云函数转发失败：' + (e && e.message ? e.message : e), data: null };
  }
};
