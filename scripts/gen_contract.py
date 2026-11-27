# -*- coding: utf-8 -*-
"""
从 yudao-module-mate 控制器源码反向生成 contract/openapi.yaml。

为什么不用 /v3/api-docs：
  1. 起服务才能导出，契约冻结不该被环境阻塞；
  2. 源码是唯一真相，手抄文档已证明会漂移（路径前缀全错）；
  3. 本脚本可进 CI 做「契约漂移检测」——源码注解改了但 openapi.yaml 没改，直接红。

用法：python scripts/gen_contract.py
"""
import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CTRL_DIR = ROOT / "ruoyi-vue-pro/yudao-module-mate/src/main/java/cn/iocoder/yudao/module/mate/controller"
OUT = ROOT / "contract/openapi.yaml"

# 包路径 -> 网关前缀（芋道约定：controller.app -> /app-api，controller.admin -> /admin-api）
PKG_PREFIX = {
    ".controller.app.": "/app-api",
    ".controller.admin.": "/admin-api",
}

TAG_OF_CTRL = {
    "AppAuthController": "M1 用户认证",
    "AdminAuthRecordController": "M1 用户认证(后台)",
    "AppDemandController": "M2 搭子匹配",
    "AppMatchController": "M2 搭子匹配",
    "AppImSessionController": "M3 即时通讯",
    "AppImMessageController": "M3 即时通讯",
    "AppImCardController": "M3 即时通讯",
    "AppImBlacklistController": "M3 即时通讯",
    "InternalImController": "M3 即时通讯(内部)",
    "AppWalletController": "M4 交易",
    "AppTransferController": "M4 交易",
    "AppSplitController": "M4 交易",
    "AppMemberController": "M4 交易",
    "AppPaymentController": "M4 交易",
    "InternalReconController": "M4 交易(内部)",
    "AppFeedController": "M5 动态",
    "AppMomentPostController": "M5 动态",
    "AppUserFollowController": "M5 动态",
    "AppHomepageController": "M5 动态",
    "InternalAuditController": "M5 动态(内部)",
    "AppReportController": "M6 风控",
    "AdminRiskController": "M6 风控(后台)",
    "AppGoodsController": "M7 二手",
    "AppGoodsOrderController": "M7 二手",
}

MODULE_OF_CTRL = {
    "AppAuthController": "M1", "AdminAuthRecordController": "M1",
    "AppDemandController": "M2", "AppMatchController": "M2",
    "AppImSessionController": "M3", "AppImMessageController": "M3",
    "AppImCardController": "M3", "AppImBlacklistController": "M3", "InternalImController": "M3",
    "AppWalletController": "M4", "AppTransferController": "M4", "AppSplitController": "M4",
    "AppMemberController": "M4", "AppPaymentController": "M4", "InternalReconController": "M4",
    "AppFeedController": "M5", "AppMomentPostController": "M5", "AppUserFollowController": "M5",
    "AppHomepageController": "M5", "InternalAuditController": "M5",
    "AppReportController": "M6", "AdminRiskController": "M6",
    "AppGoodsController": "M7", "AppGoodsOrderController": "M7",
}


def parse_controllers():
    """解析控制器，返回路由列表。方法级注解优先于类级。"""
    routes = []
    for f in sorted(CTRL_DIR.rglob("*.java")):
        text = f.read_text(encoding="utf-8", errors="replace")
        pkg = re.search(r"package\s+([\w.]+);", text)
        pkg = pkg.group(1) if pkg else ""
        cls = re.search(r"public class (\w+)", text)
        cls = cls.group(1) if cls else f.stem
        rm = re.search(r'@RequestMapping\("([^"]*)"\)', text)
        class_path = rm.group(1) if rm else ""

        # 类级 @RequireAuthLevel（在 public class 之前）
        head = text.split("public class")[0] if "public class" in text else text
        class_level = re.search(r'@RequireAuthLevel\((\d+)\)', head)
        class_level = int(class_level.group(1)) if class_level else None
        class_permit = "@PermitAll" in head
        is_internal = "InternalTokenGuard" in text

        prefix = ""
        for k, v in PKG_PREFIX.items():
            if k in "." + pkg + ".":
                prefix = v
                break

        lines = text.splitlines()
        for i, line in enumerate(lines):
            m = re.search(r'@(Get|Post|Put|Patch|Delete)Mapping(?:\("([^"]*)"\))?', line)
            if not m:
                continue
            method = m.group(1).upper()
            sub = m.group(2) or ""

            level, idem, summary, permit = None, False, "", False
            for j in range(i, min(i + 15, len(lines))):
                l = lines[j]
                lm = re.search(r'@RequireAuthLevel\((\d+)\)', l)
                if lm and level is None:
                    level = int(lm.group(1))
                if "@PermitAll" in l:
                    permit = True
                if "Idempotency-Key" in l:
                    idem = True
                om = re.search(r'@Operation\(summary\s*=\s*"([^"]*)"', l)
                if om and not summary:
                    summary = om.group(1)
                if re.search(r"public\s+(CommonResult|void)", l):
                    break

            if level is None:
                level = class_level
            if not permit:
                permit = class_permit and level is None

            routes.append({
                "controller": cls,
                "module": MODULE_OF_CTRL.get(cls, "M0"),
                "tag": TAG_OF_CTRL.get(cls, cls),
                "method": method,
                "path": prefix + class_path + sub,
                "raw_path": class_path + sub,
                "level": level,
                "permit_all": permit,
                "internal": is_internal,
                "idempotent": idem,
                "summary": summary or f"{method} {class_path}{sub}",
            })
    return routes


def security_of(r):
    if r["internal"]:
        return "internal"
    if r["permit_all"] and r["level"] is None:
        return "public"
    if r["level"] is not None:
        return f"level{r['level']}"
    return "bearer"


def yaml_str(s):
    """生成安全的 YAML 双引号字符串。"""
    if s is None:
        s = ""
    s = str(s)
    s = s.replace("\\", "\\\\").replace('"', '\\"')
    return f'"{s}"'


def build_yaml(routes):
    L = []
    a = L.append
    a("# 觅搭 · 契约 v1（自动生成，勿手改）")
    a("#")
    a("# 生成器：scripts/gen_contract.py")
    a("# 来源：yudao-module-mate 控制器源码注解（方法级 @RequireAuthLevel 优先于类级）")
    a("#")
    a("# 重新生成：python scripts/gen_contract.py")
    a("# CI 会校验「源码注解 == 本文件」，不一致即失败，防止契约漂移。")
    a("")
    a("openapi: 3.0.3")
    a("info:")
    a("  title: 觅搭 API 契约")
    a("  version: 1.0.0")
    a("  description: >")
    a("    契约 v1。路由前缀：乙侧 /api/v1，甲侧 /app-api/mate + /admin-api/mate，")
    a("    内部 /internal/v1。错误码见 contract/error-codes.md，事件见 contract/events.md。")
    a("servers:")
    a("  - url: http://127.0.0.1:48080")
    a("    description: 本地联调")
    a("")
    a("tags:")
    for tag in sorted({r["tag"] for r in routes}):
        a(f"  - name: {yaml_str(tag)}")
    a("")
    a("paths:")

    by_path = {}
    for r in routes:
        by_path.setdefault(r["path"], []).append(r)

    for path in sorted(by_path):
        a(f"  {yaml_str(path)}:")
        for r in sorted(by_path[path], key=lambda x: x["method"]):
            a(f"    {r['method'].lower()}:")
            a(f"      tags: [{yaml_str(r['tag'])}]")
            a(f"      summary: {yaml_str(r['summary'])}")
            a(f"      operationId: {yaml_str(r['controller'] + '_' + r['method'] + '_' + path.strip('/').replace('/', '_').replace('{', '').replace('}', ''))}")
            a(f"      x-module: {yaml_str(r['module'])}")
            a(f"      x-auth: {yaml_str(security_of(r))}")
            a(f"      x-idempotent: {'true' if r['idempotent'] else 'false'}")
            if r["internal"]:
                a("      x-internal: true")

            params = []
            for pm in re.finditer(r"\{(\w+)\}", path):
                params.append(pm.group(1))
            if params:
                a("      parameters:")
                for p in params:
                    a(f"        - name: {p}")
                    a("          in: path")
                    a("          required: true")
                    a("          schema:")
                    a("            type: string")
            if r["idempotent"]:
                if not params:
                    a("      parameters:")
                a("        - name: Idempotency-Key")
                a("          in: header")
                a("          required: true")
                a("          description: 客户端生成，重试必须沿用同一个值")
                a("          schema:")
                a("            type: string")
                a("            maxLength: 32")
            if r["internal"]:
                if not params and not r["idempotent"]:
                    a("      parameters:")
                a("        - name: X-Internal-Token")
                a("          in: header")
                a("          required: true")
                a("          description: 内部接口共享密钥；配置为空时一律拒绝(1002)")
                a("          schema:")
                a("            type: string")

            if r["method"] in ("POST", "PUT", "PATCH"):
                a("      requestBody:")
                a("        required: false")
                a("        content:")
                a("          application/json:")
                a("            schema:")
                a("              type: object")
            a("      responses:")
            a("        '200':")
            a(f"          description: {yaml_str(r['summary'])}")
            a("          content:")
            a("            application/json:")
            a("              schema:")
            a("                $ref: '#/components/schemas/ApiResponse'")
    a("")
    a("components:")
    a("  schemas:")
    a("    ApiResponse:")
    a("      type: object")
    a("      description: 成功失败同构；code=0 表示成功")
    a("      required: [code, message, traceId]")
    a("      properties:")
    a("        code:")
    a("          type: integer")
    a("          description: 见 contract/error-codes.md，禁止新增")
    a("        message:")
    a("          type: string")
    a("        traceId:")
    a("          type: string")
    a("        data:")
    a("          description: 各接口字段以 /v3/api-docs 导出为准")
    a("          nullable: true")
    return "\n".join(L) + "\n"


def main():
    routes = parse_controllers()
    if not routes:
        print("未解析到任何路由，检查 CTRL_DIR", file=sys.stderr)
        return 1
    OUT.parent.mkdir(parents=True, exist_ok=True)
    content = build_yaml(routes)
    OUT.write_text(content, encoding="utf-8")
    print(f"已生成 {OUT}")
    print(f"路由数: {len(routes)}")
    for mod in sorted({r["module"] for r in routes}):
        print(f"  {mod}: {sum(1 for r in routes if r['module'] == mod)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())