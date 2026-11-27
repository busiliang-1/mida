# -*- coding: utf-8 -*-
"""觅搭 M1/M2/M6 端到端业务闭环验证。"""
import json, time, urllib.request, urllib.error, sys

BASE = "http://127.0.0.1:48080"
PASS = []
FAIL = []

def req(method, path, body=None, token=None, tenant="1", headers=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    r.add_header("tenant-id", tenant)
    if token:
        r.add_header("Authorization", "Bearer " + token)
    for k, v in (headers or {}).items():
        r.add_header(k, v)
    try:
        with urllib.request.urlopen(r, timeout=15) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode())

def check(name, cond, detail=""):
    (PASS if cond else FAIL).append(name)
    print(("  [PASS] " if cond else "  [FAIL] ") + name + (("  -> " + str(detail)) if detail else ""))

def wait_server():
    for _ in range(40):
        try:
            r = req("POST", "/admin-api/system/auth/login", {"username": "admin", "password": "admin123"})
            if r.get("code") == 0:
                return r["data"]["accessToken"]
        except Exception:
            pass
        time.sleep(3)
    print("服务器未就绪"); sys.exit(1)

def register_login(phone, nickname):
    req("POST", "/app-api/mate/auth/sms-codes", {"phone": phone})
    reg = req("POST", "/app-api/mate/auth/registrations", {"phone": phone, "code": "123456", "nickname": nickname})
    assert reg.get("code") == 0, reg
    uid = reg["data"]
    login = req("POST", "/app-api/mate/auth/sessions", {"phone": phone, "code": "123456"})
    assert login.get("code") == 0, login
    return uid, login["data"]["accessToken"]

print("=== 等待服务器启动 ===")
admin_token = wait_server()
print("服务器就绪，admin 登录成功")

print("\n=== M1 注册/登录/实名 ===")
uidA, tokA = register_login("13800000001", "甲同学")
uidB, tokB = register_login("13800000002", "乙同学")
check("注册+登录两个用户", uidA and uidB and tokA and tokB, f"A={uidA} B={uidB}")

vA = req("POST", "/app-api/mate/auth/verifications",
         {"authType": 1, "realName": "张三", "idCard": "110101200001011234", "materialDigest": "digestA"}, tokA)
check("A 实名(L2)自动通过", vA.get("code") == 0, vA)
profA = req("GET", "/app-api/mate/auth/profile", token=tokA)
check("A 等级升到 L2", profA.get("data", {}).get("authLevel") == 2, profA.get("data"))
req("POST", "/app-api/mate/auth/verifications",
    {"authType": 1, "realName": "李四", "idCard": "110101200002022345", "materialDigest": "digestB"}, tokB)

# L3 学校认证（转人工）
vSchool = req("POST", "/app-api/mate/auth/verifications",
              {"authType": 2, "reportNo": "REPORT-2026-001", "materialDigest": "schoolA"}, tokA)
check("A 学校认证转人工", vSchool.get("code") == 0, vSchool)
schoolRecordId = vSchool.get("data")
# 重复报告编号应拒
vDup = req("POST", "/app-api/mate/auth/verifications",
           {"authType": 2, "reportNo": "REPORT-2026-001", "materialDigest": "x"}, tokB)
check("重复学籍报告编号被拒", vDup.get("code") == 2002001, vDup)

print("\n=== M2 需求单/报名/成团 ===")
import datetime
start = (datetime.datetime.now() + datetime.timedelta(hours=2)).strftime("%Y-%m-%dT%H:%M:%S")
d = req("POST", "/app-api/mate/demand/create",
        {"title": "周五羽毛球缺2人", "activityType": "运动", "startTime": start,
         "locationName": "校体育馆", "cityCode": "110100", "needCount": 2,
         "longitude": 116.40, "latitude": 39.90, "authLimit": 2, "feeMode": 1}, tokA)
check("A 发布需求单", d.get("code") == 0, d)
demandId = d.get("data")

s = req("POST", f"/app-api/mate/demand/{demandId}/signups", {"message": "我来"}, tokB)
check("B 报名", s.get("code") == 0, s)
signupId = s.get("data")
sDup = req("POST", f"/app-api/mate/demand/{demandId}/signups", {"message": "再来"}, tokB)
check("B 重复报名被拒", sDup.get("code") == 2002004, sDup)

rv = req("PATCH", f"/app-api/mate/demand/{demandId}/signups/{signupId}", {"action": 1}, tokA)
check("A 审核通过报名", rv.get("code") == 0, rv)
dg = req("GET", f"/app-api/mate/demand/get?id={demandId}", token=tokA)
check("满员自动成团(status=3)", dg.get("data", {}).get("status") == 3, dg.get("data", {}).get("status"))
check("成团后joinedCount=2", dg.get("data", {}).get("joinedCount") == 2, dg.get("data", {}).get("joinedCount"))

print("\n=== M2 附近的人/滑动匹配 ===")
req("POST", "/app-api/mate/match/location", {"longitude": 116.40, "latitude": 39.90, "cityCode": "110100"}, tokA)
req("POST", "/app-api/mate/match/location", {"longitude": 116.41, "latitude": 39.91, "cityCode": "110100"}, tokB)
nb = req("GET", "/app-api/mate/match/nearby?cityCode=110100&longitude=116.40&latitude=39.90&radius=5", token=tokA)
check("A 附近的人查到 B", nb.get("code") == 0 and any(u["userId"] == uidB for u in nb.get("data", [])), nb)

sw1 = req("POST", "/app-api/mate/match/swipes", {"targetId": uidB, "action": 1}, tokA)
check("A 喜欢 B(未匹配)", sw1.get("code") == 0 and sw1["data"]["matched"] is False, sw1)
sw2 = req("POST", "/app-api/mate/match/swipes", {"targetId": uidA, "action": 1}, tokB)
check("B 喜欢 A → 双向匹配", sw2.get("code") == 0 and sw2["data"]["matched"] is True, sw2)

print("\n=== M6 举报/拉黑/风险评分/敏感词 ===")
rp = req("POST", "/app-api/mate/report/create", {"targetType": 1, "targetId": uidA, "reason": 1}, tokB)
check("B 举报 A", rp.get("code") == 0, rp)
reportId = rp.get("data")
rv2 = req("PUT", "/admin-api/mate/risk/report/review", {"reportId": reportId, "action": 1}, admin_token)
check("admin 处置举报(拉黑A)", rv2.get("code") == 0, rv2)
score = req("POST", "/admin-api/mate/risk/score", {"userId": uidA, "scene": 1, "amount": 10}, admin_token)
check("A 被拉黑后风险=高/冻结", score.get("data", {}).get("riskLevel") == 2 and score["data"]["advice"] == 2, score)
scoreB = req("POST", "/admin-api/mate/risk/score", {"userId": uidB, "scene": 1, "amount": 50}, admin_token)
check("B 正常风险=低/放行", scoreB.get("data", {}).get("riskLevel") == 0, scoreB)

aw = req("POST", "/admin-api/mate/risk/audit/check", {"text": "想私下转账"}, admin_token)
check("敏感词命中(私下转账)", aw.get("data", {}).get("verdict") == "HIT", aw)
aw2 = req("POST", "/admin-api/mate/risk/audit/check", {"text": "周五打羽毛球"}, admin_token)
check("正常文本通过", aw2.get("data", {}).get("verdict") == "PASS", aw2)

print("\n=== 后台认证审核(L3 人工通过) ===")
ap = req("GET", "/admin-api/mate/auth-record/page?verifyResult=3", token=admin_token)
check("后台查到待人工认证", ap.get("code") == 0 and ap["data"]["total"] >= 1, ap.get("data", {}).get("total"))
ar = req("PUT", "/admin-api/mate/auth-record/review", {"recordId": schoolRecordId, "action": 1, "schoolId": 1}, admin_token)
check("admin 通过 A 学校认证", ar.get("code") == 0, ar)
profA2 = req("GET", "/app-api/mate/auth/profile", token=tokA)
check("A 等级升到 L3", profA2.get("data", {}).get("authLevel") == 3, profA2.get("data"))

print("\n=== M3 即时通讯（成团自动建群 + 消息 + 撤回） ===")
ss = req("GET", "/api/v1/sessions", token=tokA)
ok_sessions = ss.get("code") == 0 and ss.get("data", {}).get("list")
check("A 查到成团自动建的群会话", bool(ok_sessions), ss.get("data"))
sid = ss["data"]["list"][0]["sessionId"] if ok_sessions else None

cmid = "e2e-msg-%d" % int(time.time())
m1 = req("POST", "/api/v1/messages",
         {"sessionId": sid, "contentType": 1, "content": "周五体育馆见", "clientMsgId": cmid}, tokA)
check("A 发文本消息", m1.get("code") == 0, m1)
m_dup = req("POST", "/api/v1/messages",
            {"sessionId": sid, "contentType": 1, "content": "周五体育馆见", "clientMsgId": cmid}, tokA)
check("同 clientMsgId 重发不产生新消息",
      m_dup.get("code") == 0 and m_dup["data"]["messageId"] == m1["data"]["messageId"], m_dup)

hist = req("GET", f"/api/v1/messages?sessionId={sid}&size=20", token=tokB)
check("B 能拉到 A 的消息", hist.get("code") == 0 and any(
    x["messageId"] == m1["data"]["messageId"] for x in hist.get("data", {}).get("list", [])), hist.get("data"))

rc = req("PATCH", f"/api/v1/messages/{m1['data']['messageId']}/recall", None, tokB)
check("B 撤回 A 的消息被拒(1004)", rc.get("code") == 1004, rc)
rc2 = req("PATCH", f"/api/v1/messages/{m1['data']['messageId']}/recall", None, tokA)
check("A 撤回自己的消息", rc2.get("code") == 0 and rc2["data"]["recalled"] is True, rc2)

print("\n=== M7 二手交易（挂售/乐观锁下单/线下确认） ===")
g = req("POST", "/api/v1/goods",
        {"title": "九成新台灯", "category": "其他", "price": 35.00,
         "description": "宿舍用了一年", "images": ["goods/lamp1.jpg"]},
        tokA, headers={"Idempotency-Key": "e2e-goods-1"})
check("A(L3) 挂售成功", g.get("code") == 0, g)
goods_id = g.get("data", {}).get("goodsId")

gl = req("GET", "/api/v1/goods?size=10", token=tokB)
check("B 在二手列表看到该商品", gl.get("code") == 0 and any(
    x["goodsId"] == goods_id for x in gl.get("data", {}).get("list", [])), gl.get("data"))

go = req("POST", f"/api/v1/goods/{goods_id}/orders", None, tokB,
         headers={"Idempotency-Key": "e2e-goods-order-1"})
check("B 下单成功", go.get("code") == 0, go)
go_dup = req("POST", f"/api/v1/goods/{goods_id}/orders", None, tokB,
             headers={"Idempotency-Key": "e2e-goods-order-1"})
check("同幂等键重放返回同一单号",
      go_dup.get("code") == 0 and go_dup["data"]["orderNo"] == go["data"]["orderNo"], go_dup)
go_again = req("POST", f"/api/v1/goods/{goods_id}/orders", None, tokB,
               headers={"Idempotency-Key": "e2e-goods-order-2"})
check("商品已锁定后再次下单被拒(2002)", go_again.get("code") == 2002, go_again)

mo = req("GET", "/api/v1/goods-orders?role=1", token=tokB)
orders = mo.get("data", {}).get("list", [])
order_id = next((o["orderId"] for o in orders if o["goodsId"] == goods_id), None)
check("B 查到自己作为买家的订单", order_id is not None, mo.get("data"))

cf = req("PATCH", f"/api/v1/goods-orders/{order_id}/confirm", None, tokA,
         headers={"Idempotency-Key": "e2e-goods-confirm-1"})
check("线下确认完成(status=1)", cf.get("code") == 0 and cf["data"]["status"] == 1, cf)
gd = req("GET", f"/api/v1/goods/{goods_id}", token=tokA)
check("商品转为已完成(status=2)", gd.get("data", {}).get("goods", {}).get("status") == 2,
      gd.get("data", {}).get("goods"))

print("\n=== M4 钱包/转账（准入顺序 + 余额） ===")
w0 = req("GET", "/api/v1/wallet", token=tokB)
check("B 钱包可查且余额为 0", w0.get("code") == 0 and float(w0["data"]["available"]) == 0.0, w0)

t_over = req("POST", "/api/v1/transfers", {"payeeId": uidA, "amount": 2500.00, "scene": 1},
             tokB, headers={"Idempotency-Key": "e2e-tr-over"})
check("单笔超限 3002（限额先于余额）", t_over.get("code") == 3002, t_over)
t_poor = req("POST", "/api/v1/transfers", {"payeeId": uidA, "amount": 100.00, "scene": 1},
             tokB, headers={"Idempotency-Key": "e2e-tr-poor"})
check("余额不足 3001", t_poor.get("code") == 3001, t_poor)

print("\n=== M4 会员（下单 + 回调验签 + 权益生效） ===")
import hmac, hashlib
mp = req("GET", "/api/v1/member-packages", token=tokB)
check("会员套餐可查", mp.get("code") == 0 and len(mp.get("data") or []) >= 1, mp.get("data"))

morder = req("POST", "/api/v1/member-orders", {"packageId": 1, "durationMonth": 1},
             tokB, headers={"Idempotency-Key": "e2e-member-1"})
check("B 创建会员订单待支付", morder.get("code") == 0 and morder["data"]["status"] == 0, morder)
order_no = morder.get("data", {}).get("orderNo")
amount = morder.get("data", {}).get("amount")

def pay_callback(event_id, status="SUCCESS", sign=b"wrong"):
    trade_no = "TRADE-" + event_id
    body = {"event_id": event_id, "order_no": order_no, "trade_no": trade_no,
            "amount": float(amount), "status": status,
            "occurred_at": datetime.datetime.now().strftime("%Y-%m-%dT%H:%M:%S")}
    return req("POST", "/api/v1/payment-callbacks", body, headers={"Signature": sign.decode()}), trade_no

bad, trade_no = pay_callback("EV-BAD")
check("错误签名回调被拒(5001)", bad.get("code") == 5001, bad)

canonical = "%s|%s|%s|%.2f|SUCCESS" % ("EV-OK", order_no, trade_no, float(amount))
good_sign = hmac.new(b"mock-pay-secret", canonical.encode(), hashlib.sha256).hexdigest()
ok1, _ = pay_callback("EV-OK", sign=good_sign.encode())
check("正确签名回调成功", ok1.get("code") == 0, ok1)

os1 = req("GET", f"/api/v1/orders/{order_no}", token=tokB)
check("会员订单生效(status=2)", os1.get("data", {}).get("status") == 2, os1.get("data"))
ok2, _ = pay_callback("EV-OK", sign=good_sign.encode())
os2 = req("GET", f"/api/v1/orders/{order_no}", token=tokB)
check("同 event_id 重复回调仍只生效一次",
      ok2.get("code") == 0 and os2.get("data", {}).get("status") == 2, os2.get("data"))

print("\n=== M5 内容社区（先审后发 + 互动 + 审核通过后可见） ===")
p = req("POST", "/api/v1/posts", {"content": "求一起上自习的搭子", "images": ["moment/a.jpg"], "topicId": 1},
        tokA, headers={"Idempotency-Key": "e2e-post-1"})
check("A 发动态进审核(auditStatus=0)", p.get("code") == 0 and p["data"]["auditStatus"] == 0, p)
post_id = p.get("data", {}).get("postId")

pd = req("GET", f"/api/v1/posts/{post_id}", token=tokB)
check("待审动态对 B 不可见(1004)", pd.get("code") == 1004, pd)
pd_self = req("GET", f"/api/v1/posts/{post_id}", token=tokA)
check("待审动态对作者可见", pd_self.get("code") == 0, pd_self)

feed = req("GET", "/api/v1/feeds?type=recommend&size=20", token=tokB)
check("待审动态不出现在信息流", feed.get("code") == 0 and not any(
    x["postId"] == post_id for x in feed.get("data", {}).get("list", [])), feed.get("data"))

q = req("GET", "/api/v1/admin/audit-queues", headers={"X-Internal-Token": "mida-internal-dev-token"})
check("审核队列能查到待审动态", q.get("code") == 0 and any(
    x["bizId"] == post_id for x in q.get("data", {}).get("list", [])), q.get("data"))
q_deny = req("GET", "/api/v1/admin/audit-queues", headers={"X-Internal-Token": "wrong"})
check("审核队列错误密钥被拒(1002)", q_deny.get("code") == 1002, q_deny)

dis = req("PUT", f"/api/v1/admin/audit-queues/{post_id}?type=post", {"action": 1},
          headers={"X-Internal-Token": "mida-internal-dev-token"})
check("审核通过", dis.get("code") == 0 and dis["data"]["updated"] is True, dis)

feed2 = req("GET", "/api/v1/feeds?type=recommend&size=20", token=tokB)
check("审核通过后进入信息流", any(x["postId"] == post_id for x in feed2.get("data", {}).get("list", [])),
      feed2.get("data"))

lk1 = req("POST", f"/api/v1/posts/{post_id}/likes", None, tokB)
check("B 点赞 likeCount=1", lk1.get("code") == 0 and lk1["data"]["likeCount"] == 1, lk1)
lk2 = req("POST", f"/api/v1/posts/{post_id}/likes", None, tokB)
check("重复点赞仍为 1（唯一键防重）", lk2["data"]["likeCount"] == 1, lk2)
lk3 = req("DELETE", f"/api/v1/posts/{post_id}/likes", None, tokB)
check("取消点赞归 0", lk3["data"]["likeCount"] == 0, lk3)

ct = req("POST", f"/api/v1/posts/{post_id}/comments", {"content": "我也去"},
         tokB, headers={"Idempotency-Key": "e2e-comment-1"})
check("评论进审核(auditStatus=0)", ct.get("code") == 0 and ct["data"]["auditStatus"] == 0, ct)
cd = req("GET", f"/api/v1/posts/{post_id}", token=tokA)
check("评论未通过时不出现在评论列表", not any(
    c["commentId"] == ct["data"]["commentId"] for c in cd.get("data", {}).get("comments", [])), cd.get("data"))
req("PUT", f"/api/v1/admin/audit-queues/{ct['data']['commentId']}?type=comment", {"action": 1},
    headers={"X-Internal-Token": "mida-internal-dev-token"})
cd2 = req("GET", f"/api/v1/posts/{post_id}", token=tokA)
check("评论通过后可见", any(
    c["commentId"] == ct["data"]["commentId"] for c in cd2.get("data", {}).get("comments", [])), cd2.get("data"))

fo = req("POST", f"/api/v1/users/{uidA}/follows", None, tokB)
check("B 关注 A", fo.get("code") == 0 and fo["data"]["followed"] is True, fo)
fl = req("GET", "/api/v1/feeds?type=follow&size=20", token=tokB)
check("关注流含 A 的动态", any(x["postId"] == post_id for x in fl.get("data", {}).get("list", [])),
      fl.get("data"))

hp = req("PUT", "/api/v1/homepages/config", {"signature": "爱打球", "modulesJson": {"order": ["posts", "goods"]}}, tokA)
check("更新主页配置", hp.get("code") == 0 and hp["data"]["updated"] is True, hp)
hm = req("GET", f"/api/v1/users/{uidA}/homepage", token=tokB)
check("主页返回配置+动态+二手",
      hm.get("code") == 0 and hm["data"]["config"]["signature"] == "爱打球" and hm["data"]["posts"], hm.get("data"))

print("\n========== 结果 ==========")
print(f"通过 {len(PASS)} / 失败 {len(FAIL)}")
if FAIL:
    print("失败项:", FAIL)
    sys.exit(1)
print("全部业务闭环验证通过 ✅")
