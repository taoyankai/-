// pages/mine/mine.js —— 我的
const api = require('../../utils/api.js');
const fmt = require('../../utils/fmt.js');

Page({
  data: {
    profile: null,
    canClaim: true,
    phoneMasked: ''
  },

  onShow() {
    if (!api.isLoggedIn()) {
      api.gotoLogin();
      return;
    }
    api
      .get('/client/profile')
      .then((p) => {
        p = p || {};
        this.setData({
          profile: p,
          canClaim: p.canClaim !== false,
          phoneMasked: fmt.maskPhone(p.phone)
        });
      })
      .catch((e) => {
        api.toast(e.message);
        if (e.code === 401) api.gotoLogin();
      });
  },

  goOrders() {
    wx.navigateTo({ url: '/pages/orders/orders' });
  },
  goTrack() {
    wx.navigateTo({ url: '/pages/track/track' });
  },
  goPackages() {
    wx.navigateTo({ url: '/pages/packages/packages' });
  },

  showNotice() {
    api
      .get('/client/notices')
      .then((list) => {
        const rows = list || [];
        if (!rows.length) {
          api.toast('暂无说明');
          return;
        }
        wx.showModal({
          title: rows[0].title,
          content: rows[0].content,
          showCancel: false,
          confirmText: '知道了'
        });
      })
      .catch((e) => api.toast(e.message));
  },

  logout() {
    wx.showModal({
      title: '退出登录',
      content: '退出后需重新核验身份，确认退出？',
      success: (r) => {
        if (!r.confirm) return;
        // 先通知后端作废 token，失败也照样清本地（避免退不掉）
        api
          .post('/client/auth/logout')
          .catch(() => {})
          .then(() => {
            api.setToken('');
            getApp().globalData.grantee = null;
            wx.reLaunch({ url: '/pages/login/login' });
          });
      }
    });
  }
});
