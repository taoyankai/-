// utils/const.js —— 枚举与状态文案（与后端 Constants.ORDER_* 一致）
const STATUS_NAME = {
  0: '待发货',
  10: '已发货',
  20: '运输中',
  30: '派送中',
  40: '已签收',
  50: '已取消',
  60: '配送异常',
  70: '已退回'
};

const STATUS_TAG = {
  0: 'tag-warn',
  10: 'tag-brand',
  20: 'tag-brand',
  30: 'tag-brand',
  40: 'tag-ok',
  50: 'tag-gray',
  60: 'tag-danger',
  70: 'tag-danger'
};

const STATUS_DESC = {
  0: '我们将在承诺时间内为您安排发货',
  10: '您的慰问品已发出，可在下方查看物流轨迹',
  20: '快件运输中',
  30: '快件正在派送，配送员将送货上门',
  40: '快件已送达，感谢您的耐心等待',
  50: '订单已取消，领取额度已释放',
  60: '配送出现异常，我们正在跟进处理',
  70: '快件已退回'
};

module.exports = {
  STATUS_NAME: STATUS_NAME,
  STATUS_TAG: STATUS_TAG,
  STATUS_DESC: STATUS_DESC
};
