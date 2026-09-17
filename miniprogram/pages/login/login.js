// pages/login/login.js
// 登录方式由服务端下发（LOGIN_MODE）：name=手机号+姓名核验 / sms=手机号+短信验证码。
// 页面按 mode 自适应渲染，切换登录方式时前端零改动。
const api = require('../../utils/api.js');
const config = require('../../config.js');

Page({
  data: {
    phone: '',
    name: '',
    code: '',
    needName: true,
    needCode: false,
    err: '',
    submitting: false,
    counting: false,
    codeText: '获取验证码',
    showDemo: config.showDemoHint
  },

  onLoad() {
    api
      .get('/client/auth/mode')
      .then((m) => {
        if (m && m.mode) {
          this.setData({
            needName: !!m.nameRequired,
            needCode: !!m.codeRequired
          });
        }
      })
      .catch(() => {
        // 接口不可达时保持默认（姓名核验），提交时会给出明确报错
      });

    // 演示环境可预填手机号/姓名便于体验；正式环境 config.showDemoHint 为 false，
    // 此处**不写入任何具体凭据**，避免演示账号回流到真实用户界面
  },

  onUnload() {
    if (this.timer) clearInterval(this.timer);
  },

  onPhone(e) {
    this.setData({ phone: e.detail.value });
  },
  onName(e) {
    this.setData({ name: e.detail.value });
  },
  onCode(e) {
    this.setData({ code: e.detail.value });
  },

  sendCode() {
    if (this.data.counting) return;
    const phone = (this.data.phone || '').trim();
    if (!/^1\d{10}$/.test(phone)) {
      this.setData({ err: '请输入正确的 11 位手机号' });
      return;
    }
    api
      .post('/client/auth/send-code?phone=' + phone)
      .then(() => {
        api.toast('验证码已发送');
        this.startCountdown();
      })
      .catch((e) => this.setData({ err: e.message }));
  },

  startCountdown() {
    let s = 60;
    this.setData({ counting: true, codeText: s + 's 后重发' });
    this.timer = setInterval(() => {
      s -= 1;
      if (s <= 0) {
        clearInterval(this.timer);
        this.setData({ counting: false, codeText: '获取验证码' });
      } else {
        this.setData({ codeText: s + 's 后重发' });
      }
    }, 1000);
  },

  submit() {
    const phone = (this.data.phone || '').trim();
    const name = (this.data.name || '').trim();
    const code = (this.data.code || '').trim();

    if (!/^1\d{10}$/.test(phone)) {
      this.setData({ err: '请输入正确的 11 位手机号' });
      return;
    }

    const body = { phone: phone };
    if (this.data.needName) {
      if (!name) {
        this.setData({ err: '请填写本人真实姓名' });
        return;
      }
      body.name = name;
    }
    if (this.data.needCode) {
      if (!/^\d{6}$/.test(code)) {
        this.setData({ err: '请输入 6 位数字验证码' });
        return;
      }
      body.code = code;
    }

    this.setData({ err: '', submitting: true });
    api
      .post('/client/auth/login', body)
      .then((r) => {
        api.setToken(r.token);
        getApp().globalData.grantee = r;
        wx.reLaunch({ url: '/pages/index/index' });
      })
      .catch((e) => {
        // 1001 不在名单 / 1005 姓名不符 / 1006 失败次数过多 —— 文案由后端给出
        this.setData({ err: e.message, submitting: false });
      });
  }
});
