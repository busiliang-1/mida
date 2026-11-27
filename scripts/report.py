#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 mate 模块测试报告单页 HTML。

只读操作：解析 surefire XML、跑契约校验脚本、扫 controller 路由、探测端口，
不修改任何源码、不写数据库。

用法：
    python scripts/report.py            # 生成 reports/test-report.html
    python scripts/report.py --open     # 生成后用默认浏览器打开
"""

import glob
import html
import os
import re
import socket
import subprocess
import sys
import webbrowser
from datetime import datetime
from xml.etree import ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MATE = os.path.join(ROOT, "ruoyi-vue-pro", "yudao-module-mate")
SUREFIRE = os.path.join(MATE, "target", "surefire-reports")
CTRL = os.path.join(
    MATE, "src", "main", "java", "cn", "iocoder", "yudao", "module", "mate", "controller", "v1"
)
OUT_DIR = os.path.join(ROOT, "reports")
OUT = os.path.join(OUT_DIR, "test-report.html")

MODULE_LABEL = {"im": "M3 即时通讯", "trade": "M4 钱包交易", "moment": "M5 内容社区", "goods": "M7 二手交易"}


# ---------------------------------------------------------------- 单测
def parse_tests():
    suites = []
    for f in sorted(glob.glob(os.path.join(SUREFIRE, "TEST-*.xml"))):
        try:
            root = ET.parse(f).getroot()
        except ET.ParseError:
            continue
        cls = root.get("name") or os.path.basename(f)
        cases = []
        for tc in root.findall("testcase"):
            status, msg = "pass", ""
            for tag, label in (("failure", "fail"), ("error", "error"), ("skipped", "skip")):
                node = tc.find(tag)
                if node is not None:
                    status = label
                    msg = (node.get("message") or "").strip()
                    break
            cases.append(
                {
                    "name": tc.get("name") or "",
                    "time": float(tc.get("time") or 0),
                    "status": status,
                    "msg": msg,
                }
            )
        suites.append(
            {
                "cls": cls.split(".")[-1],
                "fqcn": cls,
                "tests": int(root.get("tests") or len(cases)),
                "failures": int(root.get("failures") or 0),
                "errors": int(root.get("errors") or 0),
                "skipped": int(root.get("skipped") or 0),
                "time": float(root.get("time") or 0),
                "cases": cases,
            }
        )
    suites.sort(key=lambda s: s["cls"].lower())
    return suites


# ---------------------------------------------------------------- 契约校验
def run_schema_check():
    script = os.path.join(ROOT, "scripts", "verify_schema.py")
    if not os.path.exists(script):
        return {"ok": False, "code": -1, "output": "未找到 scripts/verify_schema.py"}
    try:
        p = subprocess.run(
            [sys.executable, script],
            cwd=ROOT,
            capture_output=True,
            text=True,
            timeout=120,
            encoding="utf-8",
            errors="replace",
        )
        return {"ok": p.returncode == 0, "code": p.returncode,
                "output": (p.stdout or "") + (p.stderr or "")}
    except Exception as exc:  # noqa: BLE001
        return {"ok": False, "code": -1, "output": f"执行失败：{exc}"}


# ---------------------------------------------------------------- 路由
METHOD_MAP = {"Get": "GET", "Post": "POST", "Put": "PUT", "Patch": "PATCH", "Delete": "DELETE"}


def parse_routes():
    routes = []
    files = sorted(glob.glob(os.path.join(CTRL, "**", "*.java"), recursive=True))
    for path in files:
        src = open(path, encoding="utf-8", errors="replace").read()
        group = os.path.basename(os.path.dirname(path))
        m = re.search(r'@RequestMapping\("([^"]*)"\)', src)
        base = m.group(1) if m else ""
        cls = os.path.basename(path)[:-5]
        permit_all = "@PermitAll" in src
        for mm in re.finditer(
            r'@(Get|Post|Put|Patch|Delete)Mapping(?:\s*\(\s*(?:value\s*=\s*)?"([^"]*)"[^)]*\))?', src
        ):
            verb = METHOD_MAP[mm.group(1)]
            sub = mm.group(2) or ""
            routes.append(
                {
                    "method": verb,
                    "path": base + sub,
                    "cls": cls,
                    "group": group,
                    "permit_all": permit_all,
                }
            )
    order = {"GET": 0, "POST": 1, "PUT": 2, "PATCH": 3, "DELETE": 4}
    routes.sort(key=lambda r: (r["group"], r["path"], order.get(r["method"], 9)))
    return routes


# ---------------------------------------------------------------- 环境探测
def port_open(port, host="127.0.0.1", timeout=0.4):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(timeout)
        return s.connect_ex((host, port)) == 0


def git_info():
    def run(*args):
        try:
            p = subprocess.run(
                ["git", *args], cwd=ROOT, capture_output=True, text=True, timeout=10,
                encoding="utf-8", errors="replace",
            )
            return p.stdout.strip()
        except Exception:  # noqa: BLE001
            return ""

    return {"branch": run("branch", "--show-current"), "commit": run("log", "-1", "--format=%h %s")}


# ---------------------------------------------------------------- 渲染
CSS = """
:root{
  --bg:#f4f6f9; --card:#ffffff; --line:#e3e8ef; --ink:#1b2432; --muted:#6b7787;
  --ok:#0f9d58; --ok-bg:#e8f5ee; --bad:#d93025; --bad-bg:#fdecea;
  --skip:#8a6d00; --skip-bg:#fdf6dc; --accent:#2b6cb0;
}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--ink);
  font:14px/1.6 -apple-system,BlinkMacSystemFont,"Segoe UI","Microsoft YaHei","PingFang SC",sans-serif}
.wrap{max-width:1180px;margin:0 auto;padding:28px 20px 64px}
header{display:flex;flex-wrap:wrap;align-items:flex-end;justify-content:space-between;gap:12px;margin-bottom:22px}
h1{font-size:22px;margin:0 0 6px;font-weight:650;letter-spacing:.2px}
.meta{color:var(--muted);font-size:12.5px}
.meta code{background:#eaeef4;padding:1px 6px;border-radius:4px;font-size:12px}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:14px;margin-bottom:26px}
.card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:16px 18px}
.card .k{font-size:12px;color:var(--muted);margin-bottom:6px}
.card .v{font-size:26px;font-weight:680;line-height:1.15}
.card .s{font-size:12px;color:var(--muted);margin-top:4px}
.v.ok{color:var(--ok)} .v.bad{color:var(--bad)}
section{background:var(--card);border:1px solid var(--line);border-radius:10px;margin-bottom:20px;overflow:hidden}
section > h2{font-size:15px;margin:0;padding:14px 18px;border-bottom:1px solid var(--line);font-weight:640;
  display:flex;justify-content:space-between;align-items:center;gap:10px}
section > h2 .hint{font-weight:400;font-size:12px;color:var(--muted)}
table{width:100%;border-collapse:collapse;font-size:13px}
th,td{text-align:left;padding:9px 14px;border-bottom:1px solid #eef1f6;vertical-align:top}
th{background:#fafbfd;font-weight:600;font-size:12px;color:#55616f;white-space:nowrap}
tr:last-child td{border-bottom:none}
tr:hover td{background:#fbfcfe}
td.num{text-align:right;font-variant-numeric:tabular-nums;white-space:nowrap}
.tag{display:inline-block;padding:1px 8px;border-radius:999px;font-size:11.5px;font-weight:600;white-space:nowrap}
.tag.ok{background:var(--ok-bg);color:var(--ok)}
.tag.bad{background:var(--bad-bg);color:var(--bad)}
.tag.skip{background:var(--skip-bg);color:var(--skip)}
.tag.plain{background:#eef1f6;color:#5b6572}
.mono{font-family:Consolas,"SF Mono",Menlo,monospace;font-size:12.5px}
pre{margin:0;padding:16px 18px;background:#0f1724;color:#d7e2ef;font-family:Consolas,"SF Mono",Menlo,monospace;
  font-size:12.5px;line-height:1.65;overflow:auto;max-height:420px;white-space:pre}
details{border-bottom:1px solid #eef1f6}
details:last-of-type{border-bottom:none}
summary{cursor:pointer;padding:11px 18px;display:flex;align-items:center;gap:12px;list-style:none;font-size:13px}
summary::-webkit-details-marker{display:none}
summary:hover{background:#fbfcfe}
summary .caret{color:#9aa6b5;transition:transform .15s;font-size:11px}
details[open] summary .caret{transform:rotate(90deg)}
summary .name{font-family:Consolas,Menlo,monospace;font-size:12.5px;flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis}
summary .cnt{color:var(--muted);font-size:12px;white-space:nowrap}
.cases{padding:0 18px 12px 44px;background:#fcfdff}
.cases div{display:flex;gap:10px;padding:3px 0;font-size:12.5px;color:#42505f;align-items:baseline}
.cases .dot{width:7px;height:7px;border-radius:50%;flex:none;margin-top:6px;background:var(--ok)}
.cases .dot.fail,.cases .dot.error{background:var(--bad)}
.cases .dot.skip{background:var(--skip)}
.cases .t{margin-left:auto;color:#9aa6b5;font-size:11.5px;font-variant-numeric:tabular-nums}
.cases .msg{color:var(--bad);font-size:11.5px;width:100%;padding-left:17px}
.note{padding:14px 18px;font-size:13px;color:#42505f;background:#fafbfd;border-top:1px solid var(--line)}
.note code,td code{background:#eef1f6;padding:1px 5px;border-radius:4px;font-size:12px;
  font-family:Consolas,Menlo,monospace}
.env{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:0}
.env div{padding:13px 18px;border-right:1px solid var(--line);font-size:13px}
.env div:last-child{border-right:none}
.env .lbl{color:var(--muted);font-size:12px;margin-bottom:3px}
footer{color:var(--muted);font-size:12px;text-align:center;margin-top:26px}
.m{font-family:Consolas,Menlo,monospace;font-size:11.5px;font-weight:700}
.m.GET{color:#0f9d58}.m.POST{color:#2b6cb0}.m.PUT{color:#b45309}.m.PATCH{color:#7c3aed}.m.DELETE{color:#d93025}
@media (max-width:640px){ .wrap{padding:18px 12px 48px} th,td{padding:8px 10px} }
"""


def esc(s):
    return html.escape(str(s), quote=True)


def render():
    suites = parse_tests()
    schema = run_schema_check()
    routes = parse_routes()
    git = git_info()
    env = [("yudao-server", 48080), ("MySQL 临时实例", 3307), ("Redis", 6379)]
    env_rows = [(name, port, port_open(port)) for name, port in env]

    total = sum(s["tests"] for s in suites)
    bad = sum(s["failures"] + s["errors"] for s in suites)
    skipped = sum(s["skipped"] for s in suites)
    passed = total - bad - skipped
    elapsed = sum(s["time"] for s in suites)

    groups = {}
    for r in routes:
        groups.setdefault(r["group"], []).append(r)

    p = []
    p.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">")
    p.append('<meta name="viewport" content="width=device-width,initial-scale=1">')
    p.append("<title>mate 模块测试报告</title><style>%s</style></head><body><div class=\"wrap\">" % CSS)

    # 头部
    p.append("<header><div><h1>mate 模块测试报告</h1>")
    p.append('<div class="meta">生成时间 %s &nbsp;|&nbsp; 分支 <code>%s</code> &nbsp;|&nbsp; HEAD <code>%s</code></div>'
             % (esc(datetime.now().strftime("%Y-%m-%d %H:%M:%S")), esc(git["branch"]), esc(git["commit"])))
    p.append("</div><div class=\"meta\">后端乙 · M3/M4/M5/M7</div></header>")

    # 概览卡
    p.append('<div class="cards">')
    p.append('<div class="card"><div class="k">单元测试</div><div class="v %s">%d / %d</div>'
             '<div class="s">%d 个测试类 · 耗时 %.2fs</div></div>'
             % ("ok" if bad == 0 else "bad", passed, total, len(suites), elapsed))
    p.append('<div class="card"><div class="k">失败 / 错误</div><div class="v %s">%d</div>'
             '<div class="s">跳过 %d 项</div></div>' % ("ok" if bad == 0 else "bad", bad, skipped))
    schema_tables = "23"
    m = re.search(r"文档表数=(\d+)", schema["output"])
    if m:
        schema_tables = m.group(1)
    p.append('<div class="card"><div class="k">契约表比对</div><div class="v %s">%s</div>'
             '<div class="s">%s</div></div>'
             % ("ok" if schema["ok"] else "bad", schema_tables,
                "与文档逐列一致" if schema["ok"] else "存在差异，见下方"))
    p.append('<div class="card"><div class="k">已实现路由</div><div class="v">%d</div>'
             '<div class="s">覆盖 %d 个模块分组</div></div>' % (len(routes), len(groups)))
    p.append("</div>")

    # 单测明细
    p.append('<section><h2>单元测试<span class="hint">点击测试类展开用例</span></h2>')
    if not suites:
        p.append('<div class="note">未找到 surefire 报告，请先执行 '
                 '<code>mvn -pl yudao-module-mate -am test</code>。</div>')
    for s in suites:
        ok = (s["failures"] + s["errors"]) == 0
        p.append("<details%s>" % ("" if ok else " open"))
        p.append('<summary><span class="caret">&#9654;</span>'
                 '<span class="name">%s</span>'
                 '<span class="tag %s">%s</span>'
                 '<span class="cnt">%d 项 · %.2fs</span></summary>'
                 % (esc(s["cls"]), "ok" if ok else "bad",
                    "全通过" if ok else "有失败", s["tests"], s["time"]))
        p.append('<div class="cases">')
        for c in s["cases"]:
            p.append('<div><span class="dot %s"></span><span>%s</span><span class="t">%.2fs</span></div>'
                     % (c["status"], esc(c["name"]), c["time"]))
            if c["msg"]:
                p.append('<div class="msg">%s</div>' % esc(c["msg"][:300]))
        p.append("</div></details>")
    p.append("</section>")

    # 契约校验
    p.append('<section><h2>契约静态校验 <span class="hint">数据库文档 vs sql/mate DDL，逐列比对</span></h2>')
    p.append('<div class="note">脚本 <code>scripts/verify_schema.py</code> · 退出码 %d · <span class="tag %s">%s</span></div>'
             % (schema["code"], "ok" if schema["ok"] else "bad",
                "一致" if schema["ok"] else "有差异"))
    p.append("<pre>%s</pre>" % esc(schema["output"].rstrip() or "(无输出)"))
    p.append("</section>")

    # 路由清单
    p.append('<section><h2>接口清单 <span class="hint">从 controller/v1 静态扫描</span></h2>')
    for g in sorted(groups, key=lambda x: list(MODULE_LABEL).index(x) if x in MODULE_LABEL else 99):
        rows = groups[g]
        p.append("<table><thead><tr><th style=\"width:80px\">方法</th><th>路径</th>"
                 "<th style=\"width:250px\">Controller</th><th style=\"width:80px\">鉴权</th></tr></thead><tbody>")
        p.append('<tr><td colspan="4" style="background:#f7f9fc;font-weight:640;color:#42505f">'
                 '%s <span style="font-weight:400;color:#8b96a5">（%d 个）</span></td></tr>'
                 % (esc(MODULE_LABEL.get(g, g)), len(rows)))
        for r in rows:
            auth = "PermitAll" if r["permit_all"] else "需登录"
            p.append('<tr><td><span class="m %s">%s</span></td><td class="mono">%s</td>'
                     '<td class="mono" style="color:#5b6572">%s</td><td><span class="tag plain">%s</span></td></tr>'
                     % (r["method"], r["method"], esc(r["path"]), esc(r["cls"]), auth))
        p.append("</tbody></table>")
    p.append("</section>")

    # 环境
    p.append('<section><h2>运行环境 <span class="hint">生成时刻探测</span></h2><div class="env">')
    for name, port, up in env_rows:
        p.append('<div><div class="lbl">%s</div><span class="tag %s">%s</span>'
                 '<span class="mono" style="margin-left:8px;color:#8b96a5">:%d</span></div>'
                 % (esc(name), "ok" if up else "bad", "运行中" if up else "未启动", port))
    p.append("</div>")
    p.append('<div class="note">端到端脚本 <code>python scripts/e2e_test.py</code> 需要上述三项全部运行中，'
             '且数据库为初始状态（脚本会注册固定手机号，重跑前需重建库）。'
             '接口调试页：<code>http://127.0.0.1:48080/doc.html</code></div>')
    p.append("</section>")

    p.append('<footer>由 scripts/report.py 生成 · 纯静态页面，可直接双击打开</footer>')
    p.append("</div></body></html>")
    return "\n".join(p)


def main():
    if not os.path.isdir(SUREFIRE):
        print("警告：未找到 surefire 报告目录 %s" % SUREFIRE)
    os.makedirs(OUT_DIR, exist_ok=True)
    content = render()
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(content)
    print("报告已生成：%s" % OUT)
    print("大小：%.1f KB" % (os.path.getsize(OUT) / 1024))
    if "--open" in sys.argv:
        webbrowser.open("file:///" + OUT.replace("\\", "/"))


if __name__ == "__main__":
    main()
