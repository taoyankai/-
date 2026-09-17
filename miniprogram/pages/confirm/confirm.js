// pages/confirm/confirm.js
// 两种用法：
//   ?packageId=1        新建：提交配送信息（下单）
//   ?orderId=12         编辑：修改已提交订单的收货地址（仅待发货、限 1 次）
const api = require('../../utils/api.js');

/** 地址完整度校验：与后端 AddressValidator 保持同一套规则 */
function validateAddress(f) {
  if (!(f.name || '').trim()) return '请填写收货人姓名';
  if (!/^1\d{10}$/.test((f.phone || '').trim())) return '请填写正确的 11 位手机号';
  if (!f.province || !f.city || !f.district) return '请选择完整的省 / 市 / 区';
  const d = (f.detail || '').replace(/\s/g, '');
  if (d.length < 8) return '详细地址太短，请补充到楼栋与门牌号';
  if (!/\d/.test(d)) return '详细地址缺少门牌号，请补充数字门牌，以免快递无法上门';
  if (!/(室|栋|幢|号楼|单元|号|巷|组|村|大厦|公寓|小区|组团|座)/.test(d)) {
    return '详细地址缺少楼栋 / 门牌信息，例：3 栋 502 室';
  }
  return null;
}

Page({
  data: {
    pkg: null,
    carriers: [],
    editMode: false,
    orderNo: '',
    region: [],
    form: {
      name: '',
      phone: '',
      province: '',
      city: '',
      district: '',
      detail: '',
      remark: '',
      allowStation: false,
      carrier: ''
    },
    err: '',
    submitting: false
  },

  onLoad(query) {
    if (!api.isLoggedIn()) {
      api.gotoLogin();
      return;
    }
    this.orderId = query.orderId || null;
    this.packageId = query.packageId || null;

    if (this.orderId) {
      this.setData({ editMode: true });
      this.loadOrder();
    } else {
      this.loadPackage();
      this.loadCarriers();
      this.loadProfile();
    }
  },

  loadProfile() {
    api
      .get('/client/profile')
      .then((p) => {
        if (!p) return;
        this.setData({ 'form.name': p.name || '', 'form.phone': p.phone || '' });
      })
      .catch(() => {
        /* 预填失败不阻塞填写 */
      });
  },

  loadPackage() {
    api
      .get('/client/packages/' + this.packageId)
      .then((p) => this.setData({ pkg: p }))
      .catch((e) => {
        api.toast(e.message);
        if (e.code === 401) api.gotoLogin();
      });
  },

  loadCarriers() {
    api
      .get('/client/carriers')
      .then((list) => {
        const carriers = list || [];
        const first = carriers[0] ? carriers[0].code : '';
        this.setData({ carriers: carriers, 'form.carrier': this.data.form.carrier || first });
      })
      .catch(() => {
        /* 承运商拉取失败时后端会用默认第一家兜底 */
      });
  },

  loadOrder() {
    api
      .get('/client/orders/' + this.orderId)
      .then((o) => {
        this.packageId = o.packageId;
        this.setData({
          pkg: { name: o.packageName, sub: '' },
          orderNo: o.orderNo,
          region: [o.province, o.city, o.district],
          form: {
            name: o.receiver,
            phone: o.phone,
            province: o.province,
            city: o.city,
            district: o.district,
            detail: o.detail,
            remark: o.remark || '',
            allowStation: !!o.allowStation,
            carrier: o.expectCarrier || ''
          }
        });
        // 补全套餐副标题（失败也不影响提交）
        api
          .get('/client/packages/' + o.packageId)
          .then((p) => this.setData({ pkg: p }))
          .catch(() => {});
      })
      .catch((e) => {
        api.toast(e.message);
        if (e.code === 401) api.gotoLogin();
      });
  },

  onName(e) {
    this.setData({ 'form.name': e.detail.value });
  },
  onPhone(e) {
    this.setData({ 'form.phone': e.detail.value });
  },
  onDetail(e) {
    this.setData({ 'form.detail': e.detail.value });
  },
  onRemark(e) {
    this.setData({ 'form.remark': e.detail.value });
  },
  onStation(e) {
    this.setData({ 'form.allowStation': e.detail.value });
  },
  onRegion(e) {
    const v = e.detail.value || [];
    this.setData({
      region: v,
      'form.province': v[0] || '',
      'form.city': v[1] || '',
      'form.district': v[2] || ''
    });
  },
  pickCarrier(e) {
    this.setData({ 'form.carrier': e.currentTarget.dataset.code });
  },

  fail(msg) {
    this.setData({ err: msg });
    api.toast(msg);
  },

  submit() {
    const f = this.data.form;
    const bad = validateAddress(f);
    if (bad) {
      this.fail(bad);
      return;
    }

    const body = {
      receiver: f.name.trim(),
      phone: f.phone.trim(),
      province: f.province,
      city: f.city,
      district: f.district,
      detail: f.detail.trim(),
      allowStation: !!f.allowStation,
      remark: f.remark || ''
    };

    this.setData({ err: '', submitting: true });

    let req;
    if (this.data.editMode) {
      req = api.post('/client/orders/' + this.orderId + '/address', body);
    } else {
      body.packageId = this.packageId;
      body.quantity = 1;
      if (f.carrier) body.carrierCode = f.carrier;
      req = api.post('/client/orders', body);
    }

    req
      .then((o) => {
        const id = this.data.editMode ? this.orderId : o.id;
        wx.redirectTo({
          url: '/pages/result/result?orderId=' + id + '&edit=' + (this.data.editMode ? '1' : '0')
        });
      })
      .catch((e) => {
        this.setData({ submitting: false });
        this.fail(e.message);
      });
  }
});
