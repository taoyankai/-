// pages/track/track.js —— 物流进度查询（仅限本人订单）
const api = require('../../utils/api.js');
const fmt = require('../../utils/fmt.js');
const C = require('../../utils/const.js');

Page({
  data: {
    no: '',
    loading: false,
    err: '',
    result: null,
    myWaybills: []
  },

  onLoad(query) {
    if (!api.isLoggedIn()) {
      api.gotoLogin();
      return;
    }
    if (query && query.waybillNo) {
      this.setData({ no: query.waybillNo });
      this.query();
    }
    this.loadMine();
  },

  /** 已发货的本人订单，点一下即可带入运单号，省得手抄长串单号 */
  loadMine() {
    api
      .get('/client/orders')
      .then((list) => {
        const mine = (list || [])
          .filter((o) => o.waybillNo && o.status >= 10 && o.status !== 50)
          .map((o) => {
            o.statusName = C.STATUS_NAME[o.status] || '';
            o.tagCls = C.STATUS_TAG[o.status] || 'tag-gray';
            return o;
          });
        this.setData({ myWaybills: mine });
      })
      .catch(() => {
        /* 拉不到也不影响手工输入运单号查询 */
      });
  },

  onInput(e) {
    this.setData({ no: e.detail.value });
  },

  pick(e) {
    this.setData({ no: e.currentTarget.dataset.no });
    this.query();
  },

  query() {
    const no = (this.data.no || '').trim();
    if (!no) {
      this.setData({ err: '请输入运单号' });
      return;
    }
    this.setData({ loading: true, err: '', result: null });
    api
      .get('/client/orders/track?waybillNo=' + encodeURIComponent(no))
      .then((o) => {
        o.statusName = C.STATUS_NAME[o.status] || '';
        o.traces = (o.traces || []).map((t) => {
          t.desc = t.description || t.desc || '';
          t.timeText = fmt.fmt(t.trackTime || t.time);
          return t;
        });
        this.setData({ result: o, loading: false });
      })
      .catch((e) => {
        // 1301 未查询到该运单号 / 1303 该运单不属于本人订单
        this.setData({ err: e.message, loading: false });
      });
  }
});
