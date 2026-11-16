# -*- coding: utf-8 -*-
"""觅搭 M1/M2/M6 端到端业务闭环验证。"""
import json, time, urllib.request, urllib.error, sys

BASE = "http://127.0.0.1:48080"
PASS = []
FAIL = []

def req(method, path, body=None, token=None, tenant="1"):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    r.add_header("tenant-id", tenant)
    if token:
        r.add_header("Authorization", "Bearer " + token)
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

print("\n========== 结果 ==========")
print(f"通过 {len(PASS)} / 失败 {len(FAIL)}")
if FAIL:
    print("失败项:", FAIL)
    sys.exit(1)
print("全部业务闭环验证通过 ✅")
