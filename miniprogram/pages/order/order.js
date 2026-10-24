// pages/order/order.js —— 订单详情（含物流轨迹）
const api = require('../../utils/api.js');
const fmt = require('../../utils/fmt.js');
const C = require('../../utils/const.js');

Page({
  data: {
    o: null,
    receiptSubmitting: false
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
    return api
      .get('/client/orders/' + this.id)
      .then((o) => {
        o.statusName = C.STATUS_NAME[o.status] || '未知状态';
        o.statusDesc = C.STATUS_DESC[o.status] || '';
        o.createTimeText = fmt.fmt(o.createTime);
        o.signTimeText = fmt.fmt(o.signTime);
        o.canConfirmReceipt = [10, 20, 30].includes(o.status) && !!o.waybillNo && !!o.shipTime;
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

  confirmReceipt() {
    if (this.data.receiptSubmitting || this.receiptModalOpen || !this.data.o || !this.data.o.canConfirmReceipt) return;
    this.receiptModalOpen = true;
    wx.showModal({
      title: '确认收货',
      content: '请确认您已实际收到商品。确认后订单将更新为已签收。',
      confirmText: '已收到商品',
      cancelText: '暂未收到',
      success: (r) => {
        if (!r.confirm) {
          this.receiptModalOpen = false;
          return;
        }
        this.setData({ receiptSubmitting: true });
        api.post('/client/orders/' + this.id + '/confirm-receipt', {})
          .then((result) => {
            api.toast(result.changed ? '已确认收货' : '订单已签收，无需重复确认');
            return this.load();
          })
          .catch((e) => api.toast(e.message))
          .finally(() => {
            this.receiptModalOpen = false;
            this.setData({ receiptSubmitting: false });
          });
      },
      fail: () => { this.receiptModalOpen = false; }
    });
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
