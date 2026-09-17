// config.js
// 小程序端唯一的可变配置集中在这里：换主体 / 换环境 / 切通道都只改这个文件。
module.exports = {
  /* ---------- 请求通道 ---------- */
  // true  = 走云函数转发（**正式上线用这个**；无需服务器域名、无需 ICP 备案）
  // false = 直接 wx.request 调后端（只在本地联调用；需在开发者工具勾选
  //         「详情 → 本地设置 → 不校验合法域名、web-view、TLS 及 HTTPS 证书」）
  //
  // ⚠️ 体验版 / 正式版**必须**用 true：
  //    发布后 wx.request 只接受「HTTPS + 已 ICP 备案的域名」，IP 和 http 一律被拒；
  //    而云函数在腾讯云侧发起请求，不受该白名单约束，所以后端是 IP + HTTP 也能跑。
  //    改这里之前，先把下面的 cloudEnvId 填好（否则小程序启动会报配置错误）。
  useCloud: true,

  // 云开发环境 ID。获取方式二选一：
  //   ① 开发者工具 → 上方「云开发」按钮 → 首次进入按提示开通并创建环境
  //      → 设置 → 环境 ID（形如 hz-delivery-3gxxxxxxxxxxxxx）
  //   ② 云开发控制台 https://tcb.cloud.tencent.com/dev → 设置 → 环境 ID
  // 填好后本文件不必再改其它地方：云函数通道即可工作。
  cloudEnvId: 'cloud1-d2gar70gv5b552827',

  /* ---------- 后端地址 ---------- */
  // 含义：useCloud=false 时，小程序**直连**的后端地址（仅开发者工具可用）。
  // 走云函数通道（useCloud=true）时，后端地址由**云函数的环境变量**决定，
  // 与本文件无关 —— 云函数那边填 BACKEND_BASE_URL，见 cloudfunctions/api/README.md。
  //
  //   公网（当前服务器） http://221.192.236.175/api   ← 校内外均可访问
  //   内网服务器          http://10.191.19.25/api      ← 仅校园网内
  //   本机开发            http://localhost:8080/api
  backendDebugUrl: 'http://221.192.236.175/api',

  /* ---------- 其它 ---------- */
  // 演示提示开关：登录页会显示演示手机号与姓名，正式环境务必保持 false
  showDemoHint: false
};
