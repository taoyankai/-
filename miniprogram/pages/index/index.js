// pages/index/index.js —— 首页
const api = require('../../utils/api.js');

const NOTICE_TYPE = { 1: '公告', 2: '配送说明', 3: '偏远地区说明' };

Page({
  data: {
    profile: null,
    canClaim: true,
    notices: [],
    // 首屏状态：必须有明确的「加载中 / 加载失败」表现。
    // 否则真机上接口一慢或出错，页面除了一张标题卡什么都没有，用户会以为"白屏"。
    loading: true,
    errMsg: ''
  },

  onShow() {
    if (!api.isLoggedIn()) {
      api.gotoLogin();
      return;
    }
    this.load();
  },

  load() {
    this.setData({ loading: true, errMsg: '' });
    Promise.all([api.get('/client/profile'), api.get('/client/notices')])
      .then((res) => {
        const p = res[0] || {};
        const notices = (res[1] || []).map((n) => {
          n.typeName = NOTICE_TYPE[n.type] || '说明';
          return n;
        });
        this.setData({
          profile: p,
          canClaim: p.canClaim !== false,
          notices: notices,
          loading: false,
          errMsg: ''
        });
      })
      .catch((e) => {
        // 失败也要把 loading 关掉，并把原因留在页面上（toast 一闪而过，用户抓不到）
        this.setData({ loading: false, errMsg: (e && e.message) || '信息加载失败，请稍后重试' });
        api.toast(e.message);
        if (e.code === 401) api.gotoLogin();
      });
  },

  retry() {
    this.load();
  },

  goPackages() {
    wx.navigateTo({ url: '/pages/packages/packages' });
  },
  goOrders() {
    wx.navigateTo({ url: '/pages/orders/orders' });
  },
  goTrack() {
    wx.navigateTo({ url: '/pages/track/track' });
  },
  goMine() {
    wx.navigateTo({ url: '/pages/mine/mine' });
  },

  onShareAppMessage() {
    return {
      title: '华中商贸配送中心 · 教职工慰问品领取',
      path: '/pages/index/index'
    };
  }
});
