// pages/order/order.js —— 订单详情（含物流轨迹）
const api = require('../../utils/api.js');
const fmt = require('../../utils/fmt.js');
const C = require('../../utils/const.js');

Page({
  data: {
    o: null
  },

  onLoad(query) {
    if (!api.isLoggedIn()) {
      api.gotoLogin();
      return;
    }
    this.id = query.id;
  },

  // 详情页可能在改地址后返回，用 onShow 保证数据刷新
  onShow() {
    if (this.id) this.load();
  },

  load() {
    api
      .get('/client/orders/' + this.id)
      .then((o) => {
        o.statusName = C.STATUS_NAME[o.status] || '未知状态';
        o.statusDesc = C.STATUS_DESC[o.status] || '';
        o.createTimeText = fmt.fmt(o.createTime);
        o.remainText = o.status === 0 ? fmt.remainText(o.slaDeadline) : '';
        o.canEditAddr = o.status === 0 && !(o.addrChangeCount > 0);
        // 轨迹字段归一化：后端是 description / trackTime
        o.traces = (o.traces || []).map((t) => {
          t.desc = t.description || t.desc || '';
          t.timeText = fmt.fmt(t.trackTime || t.time);
          return t;
        });
        this.setData({ o: o });
      })
      .catch((e) => {
        api.toast(e.message);
        if (e.code === 401) api.gotoLogin();
      });
  },

  editAddr() {
    wx.navigateTo({ url: '/pages/confirm/confirm?orderId=' + this.id });
  },

  cancel() {
    wx.showModal({
      title: '取消订单',
      content: '确认取消该订单？取消后领取额度与库存将释放。',
      confirmText: '确认取消',
      cancelText: '再想想',
      success: (r) => {
        if (!r.confirm) return;
        api
          .post('/client/orders/' + this.id + '/cancel', { reason: '用户主动取消' })
          .then(() => {
            api.toast('订单已取消，额度已释放');
            this.load();
          })
          .catch((err) => api.toast(err.message));
      }
    });
  }
});
