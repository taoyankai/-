// pages/packages/packages.js
const api = require('../../utils/api.js');

Page({
  data: {
    list: [],
    loading: true,
    canClaim: true
  },

  onShow() {
    if (!api.isLoggedIn()) {
      api.gotoLogin();
      return;
    }
    this.load();
  },

  load() {
    this.setData({ loading: true });
    // 套餐列表与领取额度并行拉取：额度已用尽时给出明确提示，而不是等用户填完地址才报错
    Promise.all([api.get('/client/packages'), api.get('/client/profile')])
      .then((res) => {
        const list = res[0] || [];
        const p = res[1] || {};
        this.setData({
          list: list,
          canClaim: p.canClaim !== false,
          loading: false
        });
      })
      .catch((e) => {
        this.setData({ loading: false });
        api.toast(e.message);
        if (e.code === 401) api.gotoLogin();
      });
  },

  open(e) {
    wx.navigateTo({ url: '/pages/package/package?id=' + e.currentTarget.dataset.id });
  }
});
