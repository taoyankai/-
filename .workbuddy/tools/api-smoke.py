# -*- coding: utf-8 -*-
"""
华中商贸配送中心 · 后端接口冒烟测试
覆盖：后台登录 / 看板 / 名单 / C 端核验登录 / 套餐 / 下单 / 发货 / SLA / 导出
"""
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sms_helper import login_body  # 登录方式自适应：sms 模式注入可控验证码

BASE = "http://localhost"
passed = 0
failed = 0
failures = []


def call(method, path, token=None, body=None, raw=False):
    # 路径中可能含中文（如搜索关键词），必须先做 URL 编码
    url = BASE + urllib.parse.quote(path, safe="/?&=:")
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["X-Token"] = token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            content = resp.read()
            if raw:
                return resp.status, content
            return resp.status, json.loads(content.decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "ignore")


def chk(name, cond, detail=""):
    global passed, failed
    if cond:
        passed += 1
        print(f"  [PASS] {name}")
    else:
        failed += 1
        failures.append(f"{name} :: {detail}")
        print(f"  [FAIL] {name}  {detail}")


print("=" * 72)
print(" 华中商贸配送中心 · 接口冒烟测试")
print("=" * 72)

# ---------------------------------------------------------------- 1 基础连通
print("\n[1] 基础连通性")
st, body = call("GET", "/healthz", raw=True)
chk("Nginx 网关可达", st == 200 and b"ok" in body, f"status={st}")

# 经 Nginx 的健康检查：曾因未透传 Host 头（默认用上游名 hz_api，含下划线被
# Tomcat 以 400 拒绝）而失败，保留该断言防止回归
st, body = call("GET", "/actuator/health", raw=True)
chk("经网关访问健康检查返回 UP", st == 200 and b'"UP"' in body, f"status={st} body={body[:60]!r}")

st, data = call("GET", "/api/admin/ping")
chk("API 未授权访问被拦截", st == 200 and data.get("code") == 401, f"resp={data}")

# ---------------------------------------------------------------- 2 后台登录
print("\n[2] 后台登录与鉴权")
st, data = call("POST", "/api/admin/auth/login",
                body={"username": "admin", "password": "hz@2026"})
chk("管理员登录成功", st == 200 and data.get("code") == 0, f"resp={data}")
admin_token = (data.get("data") or {}).get("token")
chk("登录返回 token", bool(admin_token))

st, data = call("POST", "/api/admin/auth/login",
                body={"username": "admin", "password": "wrong-password"})
chk("错误密码被拒绝", data.get("code") != 0, f"resp={data}")

st, data = call("GET", "/api/admin/dashboard", token=admin_token)
chk("携带 token 可访问看板", data.get("code") == 0, f"resp={str(data)[:200]}")

# ---------------------------------------------------------------- 3 看板数据
print("\n[3] 看板统计")
dash = data.get("data") or {}
order_stat = dash.get("orderStat") or {}
grantee_stat = dash.get("granteeStat") or {}
sla_stat = dash.get("slaStat") or {}
chk("订单总数正确", order_stat.get("total") == 12, f"total={order_stat.get('total')}")
chk("待发货数正确", order_stat.get("pending") == 6, f"pending={order_stat.get('pending')}")
chk("已签收数正确", order_stat.get("signed") == 2, f"signed={order_stat.get('signed')}")
chk("名单总数正确", grantee_stat.get("total") == 37, f"total={grantee_stat.get('total')}")
chk("已领取人数正确", grantee_stat.get("claimed") == 12, f"claimed={grantee_stat.get('claimed')}")
chk("SLA 超时单数正确", sla_stat.get("overdue") == 1, f"overdue={sla_stat.get('overdue')}")
chk("SLA 24 小时内到期单数正确", sla_stat.get("within1d") == 1, f"within1d={sla_stat.get('within1d')}")
chk("SLA 3 天内到期单数正确", sla_stat.get("within3d") == 1, f"within3d={sla_stat.get('within3d')}")
# 演示数据现为单一单位（河北大学）；导入甲方真实名单后此值会随实际单位数变化
chk("单位维度统计非空", len(dash.get("orgStat") or []) >= 1, f"orgs={len(dash.get('orgStat') or [])}")
chk("套餐维度统计非空", len(dash.get("packageStat") or []) >= 3, f"pkgs={len(dash.get('packageStat') or [])}")
chk("趋势数据补齐 14 天", len(dash.get("trend") or []) == 14, f"trend={len(dash.get('trend') or [])}")
chk("库存预警含售罄套餐", any("团圆" in x.get("name", "") for x in (dash.get("lowStock") or [])),
    f"lowStock={dash.get('lowStock')}")

# ---------------------------------------------------------------- 4 名单
print("\n[4] 教职工名单")
st, data = call("GET", "/api/admin/grantees?page=1&size=10", token=admin_token)
chk("名单分页查询成功", data.get("code") == 0, f"resp={str(data)[:150]}")
chk("分页总数正确", (data.get("data") or {}).get("total") == 37,
    f"total={(data.get('data') or {}).get('total')}")

st, data = call("GET", "/api/admin/grantees?keyword=张明远", token=admin_token)
chk("按姓名搜索命中", (data.get("data") or {}).get("total") == 1,
    f"total={(data.get('data') or {}).get('total')}")

st, data = call("GET", "/api/admin/grantees?claimStatus=0", token=admin_token)
chk("未领取筛选正确", (data.get("data") or {}).get("total") == 25,
    f"total={(data.get('data') or {}).get('total')}")

st, data = call("GET", "/api/admin/grantees?org=河北大学", token=admin_token)
chk("按单位筛选正确", (data.get("data") or {}).get("total") == 37,
    f"total={(data.get('data') or {}).get('total')}")

# ---------------------------------------------------------------- 5 C 端核验
# 登录方式由 LOGIN_MODE 决定：sms=短信验证码（正式默认）/ name=手机号+姓名（仅演示）
print("\n[5] C 端身份核验")
st, data = call("GET", "/api/client/auth/mode")
login_mode = (data.get("data") or {}).get("mode")
chk("登录方式接口可用", data.get("code") == 0 and login_mode in ("name", "sms"), f"mode={login_mode}")
chk("已下发失败锁定参数（防姓名枚举/验证码爆破）",
    isinstance((data.get("data") or {}).get("maxFail"), int)
    and isinstance((data.get("data") or {}).get("lockMinutes"), int),
    f"data={data.get('data')}")

st, data = call("GET", "/api/client/auth/check?phone=13900139003")
chk("名单内手机号校验通过", (data.get("data") or {}).get("inList") is True, f"resp={data}")
chk("名单校验不泄露姓名/单位/部门（防花名册被探测）",
    not ({"name", "org", "dept", "canClaim"} & set(data.get("data") or {})),
    f"keys={sorted((data.get('data') or {}).keys())}")

if login_mode == "sms":
    # 短信模式：演示号码收不到短信，验证码由注入提供
    st, data = call("POST", "/api/client/auth/send-code?phone=13000000000")
    chk("名单外手机号不发验证码（避免无效短信费用）", data.get("code") == 1001, f"resp={data}")

    st, data = call("POST", "/api/client/auth/login",
                    body={"phone": "13000000000", "code": "000000"})
    chk("名单外手机号被拦截", data.get("code") == 1001, f"resp={data}")

    st, data = call("POST", "/api/client/auth/login",
                    body={"phone": "13900139003", "code": "000000"})
    chk("验证码错误被拒绝", data.get("code") == 1003, f"resp={data}")
    chk("失败时提示剩余次数", "还可尝试" in (data.get("msg") or ""), f"msg={data.get('msg')}")

    body, _ = login_body("13900139003", "刘嘉怡")
    data = call("POST", "/api/client/auth/login", body=body)[1]
    chk("正确验证码核验通过", data.get("code") == 0, f"resp={str(data)[:200]}")
    chk("短信通道不回传验证码（防越权登录）",
        "mockCode" not in (data.get("data") or {}), f"data={data.get('data')}")
else:
    st, data = call("POST", "/api/client/auth/send-code?phone=13900139003")
    chk("免短信方式下不开放验证码通道", data.get("code") == 1007, f"resp={data}")

    st, data = call("POST", "/api/client/auth/login",
                    body={"phone": "13000000000", "name": "张三"})
    chk("名单外手机号被拦截", data.get("code") == 1001, f"resp={data}")

    st, data = call("POST", "/api/client/auth/login",
                    body={"phone": "13900139003", "name": "李四"})
    chk("姓名与名单不符被拒绝", data.get("code") == 1005, f"resp={data}")
    chk("失败时提示剩余次数", "还可尝试" in (data.get("msg") or ""), f"msg={data.get('msg')}")

    st, data = call("POST", "/api/client/auth/login",
                    body={"phone": "13900139003", "name": " 刘 嘉 怡 "})
    chk("姓名含空格仍能核验通过（归一化）", data.get("code") == 0, f"resp={str(data)[:200]}")
client = data.get("data") or {}
client_token = client.get("token")
chk("登录返回领取额度", client.get("canClaim") is True, f"canClaim={client.get('canClaim')}")
chk("登录后额度为 1 份", client.get("quota") == 1, f"quota={client.get('quota')}")

# ---------------------------------------------------------------- 6 套餐
print("\n[6] 套餐与公告")
st, data = call("GET", "/api/client/packages", token=client_token)
pkgs = data.get("data") or []
chk("套餐列表返回 4 个", len(pkgs) == 4, f"len={len(pkgs)}")
chk("套餐含商品明细", all(len(p.get("goods") or []) > 0 for p in pkgs), "有空明细")
chk("售罄套餐库存为 0", any(p.get("stock") == 0 for p in pkgs), f"stocks={[p.get('stock') for p in pkgs]}")

st, data = call("GET", "/api/client/notices", token=client_token)
notices = data.get("data") or []
chk("公告返回 3 条", len(notices) == 3, f"len={len(notices)}")
chk("配送说明含「10 日」承诺", any("10 日" in (n.get("content") or "") for n in notices), "未找到")
chk("配送说明含代收点规则", any("代收点" in (n.get("content") or "") for n in notices), "未找到")

# ---------------------------------------------------------------- 7 地址校验
print("\n[7] 配送地址校验（招标要求：上楼入户）")
base_addr = {"packageId": 1, "receiver": "刘嘉怡", "phone": "13900139003",
             "province": "湖北省", "city": "武汉市", "district": "洪山区"}

st, data = call("POST", "/api/client/addresses/validate", token=client_token,
                body=dict(base_addr, detail="五四东路1号"))
chk("地址过短被拦截", (data.get("data") or {}).get("valid") is False, f"resp={data}")

st, data = call("POST", "/api/client/addresses/validate", token=client_token,
                body=dict(base_addr, detail="河北大学校门口随便一个地方"))
chk("地址无门牌号被拦截", (data.get("data") or {}).get("valid") is False, f"resp={data}")

st, data = call("POST", "/api/client/addresses/validate", token=client_token,
                body=dict(base_addr, detail="五四东路180号3栋502室"))
chk("合规门牌地址通过", (data.get("data") or {}).get("valid") is True, f"resp={data}")

# ---------------------------------------------------------------- 8 下单
print("\n[8] 提交配送信息")
st, data = call("POST", "/api/client/orders", token=client_token,
                body=dict(base_addr, detail="五四东路180号河北大学3栋502室",
                          allowStation=False, remark="工作日在家", saveAddress=True))
chk("下单成功", data.get("code") == 0, f"resp={str(data)[:300]}")
order = data.get("data") or {}
order_id = order.get("id")
order_no = order.get("orderNo")
chk("生成订单号", bool(order_no) and order_no.startswith("HZ"), f"orderNo={order_no}")
chk("初始状态为待发货", order.get("status") == 0, f"status={order.get('status')}")
chk("写入 SLA 截止时间", bool(order.get("slaDeadline")), f"sla={order.get('slaDeadline')}")
chk("剩余天数为 10 天", order.get("remainDays") == 10, f"remainDays={order.get('remainDays')}")
chk("套餐内容快照已保存", len(order.get("goods") or []) == 3, f"goods={order.get('goods')}")
chk("生成首条物流轨迹", len(order.get("traces") or []) == 1, f"traces={len(order.get('traces') or [])}")
chk("代收点开关默认关闭", order.get("allowStation") is False, f"allowStation={order.get('allowStation')}")

st, data = call("POST", "/api/client/orders", token=client_token,
                body=dict(base_addr, detail="五四东路180号河北大学3栋502室"))
chk("重复提交被幂等拦截", data.get("code") == 1304, f"resp={data}")

st, data = call("GET", "/api/client/profile", token=client_token)
chk("下单后额度已用尽", (data.get("data") or {}).get("canClaim") is False,
    f"used={(data.get('data') or {}).get('used')}")
chk("下单后已领份数为 1", (data.get("data") or {}).get("used") == 1,
    f"used={(data.get('data') or {}).get('used')}")

# 同一人再次下单（换套餐）应被「每人限领 1 份」拦截
st, data = call("POST", "/api/client/orders", token=client_token,
                body={"packageId": 2, "receiver": "刘嘉怡", "phone": "13900139003",
                      "province": "湖北省", "city": "武汉市", "district": "洪山区",
                      "detail": "五四东路180号河北大学5栋801室"})
chk("换套餐重复领取被拦截（每人限领 1 份）", data.get("code") == 1103, f"resp={data}")

st, data = call("GET", "/api/client/addresses", token=client_token)
chk("下单时勾选保存地址生效", len(data.get("data") or []) == 1, f"len={len(data.get('data') or [])}")

st, data = call("GET", "/api/client/orders", token=client_token)
chk("我的订单可查询", len(data.get("data") or []) == 1, f"len={len(data.get('data') or [])}")

# ---------------------------------------------------------------- 9 发货
print("\n[9] 发货管理")
st, data = call("GET", "/api/admin/orders?status=0&size=50", token=admin_token)
pending = data.get("data") or {}
chk("待发货列表含新订单", pending.get("total") == 7, f"total={pending.get('total')}")
new_order = next((o for o in (pending.get("list") or []) if o.get("orderNo") == order_no), None)
chk("新订单出现在待发货池", new_order is not None)

st, data = call("POST", "/api/admin/orders/ship", token=admin_token,
                body={"orderIds": [order_id], "carrier": "SF", "waybillNo": "SF9999888877776"})
chk("发货成功", (data.get("data") or {}).get("successCount") == 1, f"resp={data}")

st, data = call("GET", f"/api/admin/orders/{order_id}", token=admin_token)
shipped = data.get("data") or {}
chk("订单状态更新为已发货", shipped.get("status") == 10, f"status={shipped.get('status')}")
chk("写入运单号", shipped.get("waybillNo") == "SF9999888877776", f"waybill={shipped.get('waybillNo')}")
chk("写入承运商", shipped.get("carrierName") == "顺丰速运", f"carrier={shipped.get('carrierName')}")
chk("写入发货时间", bool(shipped.get("shipTime")))
chk("追加发货轨迹", len(shipped.get("traces") or []) == 2, f"traces={len(shipped.get('traces') or [])}")

st, data = call("POST", "/api/admin/orders/ship", token=admin_token,
                body={"orderIds": [order_id], "carrier": "SF", "waybillNo": "SF9999888877777"})
chk("重复发货被拦截", (data.get("data") or {}).get("failCount") == 1, f"resp={data}")

st, data = call("POST", "/api/admin/orders/ship", token=admin_token,
                body={"orderIds": [order_id], "carrier": "BAD", "waybillNo": "X1"})
chk("非法承运商被拦截", data.get("code") != 0, f"resp={data}")

# ---------------------------------------------------------------- 10 物流 & SLA
print("\n[10] 物流跟踪与 SLA")
st, data = call("POST", f"/api/admin/orders/{order_id}/push-status", token=admin_token,
                body={"status": 30, "description": "快件正在派送，配送员正在为您送货上门"})
chk("推进物流状态成功", data.get("code") == 0, f"resp={data}")

st, data = call("GET", f"/api/admin/orders/{order_id}", token=admin_token)
chk("物流状态更新为派送中", (data.get("data") or {}).get("status") == 30,
    f"status={(data.get('data') or {}).get('status')}")
chk("轨迹追加成功", len((data.get("data") or {}).get("traces") or []) == 3)

st, data = call("GET", "/api/admin/orders/sla?level=3", token=admin_token)
overdue = (data.get("data") or {}).get("total")
chk("SLA 超时订单可筛选", overdue == 1, f"overdue={overdue}")

st, data = call("GET", "/api/admin/orders/carriers", token=admin_token)
chk("承运商字典返回 5 条", len(data.get("data") or []) == 5, f"len={len(data.get('data') or [])}")

# ---------------------------------------------------------------- 11 Excel 导出
print("\n[11] Excel 导出")
st, body = call("GET", "/api/admin/grantees/export", token=admin_token, raw=True)
is_xlsx = body[:2] == b"PK"
chk("名单导出返回 xlsx 文件", st == 200 and is_xlsx, f"status={st} size={len(body)}")
chk("导出文件体积合理", len(body) > 5000, f"size={len(body)}")

st, body = call("GET", "/api/admin/orders/export", token=admin_token, raw=True)
chk("订单导出返回 xlsx 文件", st == 200 and body[:2] == b"PK", f"status={st} size={len(body)}")

st, body = call("GET", "/api/admin/orders/export-pending", token=admin_token, raw=True)
chk("待发货清单导出成功", st == 200 and body[:2] == b"PK", f"status={st} size={len(body)}")

st, body = call("GET", "/api/admin/grantees/template", token=admin_token, raw=True)
chk("名单导入模板可下载", st == 200 and body[:2] == b"PK", f"status={st} size={len(body)}")

st, body = call("GET", "/api/admin/orders/waybill-template", token=admin_token, raw=True)
chk("运单导入模板可下载", st == 200 and body[:2] == b"PK", f"status={st} size={len(body)}")

# ---------------------------------------------------------------- 12 取消订单
print("\n[12] 取消订单回滚")
_body, _ = login_body("13900139006", "黄静怡")
st, data = call("POST", "/api/client/auth/login", body=_body)
t2 = (data.get("data") or {}).get("token")
st, data = call("POST", "/api/client/orders", token=t2,
                body={"packageId": 2, "receiver": "黄静怡", "phone": "13900139006",
                      "province": "湖北省", "city": "武汉市", "district": "洪山区",
                      "detail": "七一东路2666号河北大学8栋303室"})
oid2 = (data.get("data") or {}).get("id")
chk("第二名教职工下单成功", data.get("code") == 0, f"resp={str(data)[:200]}")

st, data = call("GET", "/api/admin/packages/2", token=admin_token)
stock_before = (data.get("data") or {}).get("stock")
st, data = call("POST", f"/api/client/orders/{oid2}/cancel", token=t2, body={"reason": "填错地址"})
chk("订单取消成功", data.get("code") == 0, f"resp={data}")
st, data = call("GET", "/api/admin/packages/2", token=admin_token)
stock_after = (data.get("data") or {}).get("stock")
chk("取消后库存已回补", stock_after == stock_before + 1, f"{stock_before} -> {stock_after}")

st, data = call("GET", "/api/client/profile", token=t2)
chk("取消后领取额度已释放", (data.get("data") or {}).get("canClaim") is True,
    f"used={(data.get('data') or {}).get('used')}")

# ---------------------------------------------------------------- 13 导出筛选
print("\n[13] 按条件导出筛选")
st, body = call("GET", "/api/admin/grantees/export?org=河北大学&claimStatus=0", token=admin_token, raw=True)
chk("按单位+状态筛选举措可用", st == 200 and body[:2] == b"PK", f"status={st}")

st, data = call("GET", "/api/admin/grantees/batches", token=admin_token)
chk("导入批次接口可用", data.get("code") == 0, f"resp={str(data)[:150]}")

# ---------------------------------------------------------------- 汇总
print("\n" + "=" * 72)
print(f" 汇总：{passed} 项通过 / {failed} 项失败")
if failures:
    print("\n 失败明细：")
    for f in failures:
        print("   - " + f)
print("=" * 72)
sys.exit(1 if failed else 0)
