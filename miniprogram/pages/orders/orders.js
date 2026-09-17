// pages/orders/orders.js —— 我的订单
const api = require('../../utils/api.js');
const fmt = require('../../utils/fmt.js');
const C = require('../../utils/const.js');

Page({
  data: {
    list: [],
    loading: true
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
    api
      .get('/client/orders')
      .then((list) => {
        const rows = (list || []).map((o) => {
          o.statusName = C.STATUS_NAME[o.status] || '未知状态';
          o.tagCls = C.STATUS_TAG[o.status] || 'tag-gray';
          o.phoneMasked = fmt.maskPhone(o.phone);
          o.remainText = o.status === 0 ? fmt.remainText(o.slaDeadline) : '';
          // 与后端一致：仅待发货、且未用过自助改址次数时可改
          o.canEditAddr = o.status === 0 && !(o.addrChangeCount > 0);
          return o;
        });
        this.setData({ list: rows, loading: false });
      })
      .catch((e) => {
        this.setData({ loading: false });
        api.toast(e.message);
        if (e.code === 401) api.gotoLogin();
      });
  },

  open(e) {
    wx.navigateTo({ url: '/pages/order/order?id=' + e.currentTarget.dataset.id });
  },

  editAddr(e) {
    wx.navigateTo({ url: '/pages/confirm/confirm?orderId=' + e.currentTarget.dataset.id });
  },

  cancel(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '取消订单',
      content: '确认取消该订单？取消后领取额度与库存将释放。',
      confirmText: '确认取消',
      cancelText: '再想想',
      success: (r) => {
        if (!r.confirm) return;
        api
          .post('/client/orders/' + id + '/cancel', { reason: '用户主动取消' })
          .then(() => {
            api.toast('订单已取消，额度已释放');
            this.load();
          })
          .catch((err) => api.toast(err.message));
      }
    });
  }
});
