// pages/result/result.js
const api = require('../../utils/api.js');
const fmt = require('../../utils/fmt.js');

Page({
  data: {
    o: null,
    editMode: false,
    phoneMasked: '',
    expectTime: ''
  },

  onLoad(query) {
    this.setData({ editMode: query.edit === '1' });
    const id = query.orderId;
    if (!id) return;
    api
      .get('/client/orders/' + id)
      .then((o) => {
        this.setData({
          o: o,
          phoneMasked: fmt.maskPhone(o.phone),
          expectTime: fmt.fmt(o.slaDeadline, false)
        });
      })
      .catch((e) => {
        // 详情拉取失败不影响「提交成功」的结论展示
        console.warn('订单详情加载失败', e.message);
      });
  },

  goOrders() {
    wx.redirectTo({ url: '/pages/orders/orders' });
  },

  goHome() {
    wx.reLaunch({ url: '/pages/index/index' });
  }
});
