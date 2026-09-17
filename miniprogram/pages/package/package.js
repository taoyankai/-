// pages/package/package.js
const api = require('../../utils/api.js');

Page({
  data: {
    p: null
  },

  onLoad(query) {
    if (!api.isLoggedIn()) {
      api.gotoLogin();
      return;
    }
    this.id = query.id;
    api
      .get('/client/packages/' + this.id)
      .then((p) => this.setData({ p: p }))
      .catch((e) => {
        api.toast(e.message);
        if (e.code === 401) api.gotoLogin();
      });
  },

  choose() {
    if (!this.data.p) return;
    wx.navigateTo({ url: '/pages/confirm/confirm?packageId=' + this.id });
  },

  onShareAppMessage() {
    return {
      title: '华中商贸配送中心 · 教职工慰问品领取',
      path: '/pages/index/index'
    };
  }
});
