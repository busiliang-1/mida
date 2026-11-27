#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从运行时 /v3/api-docs 导出「只含 mate 业务接口」的字段级契约。

为什么需要两套：
  - gen_contract.py    从源码注解生成，只有路由/鉴权/幂等标记，离线可跑，用于 CI 漂移检测
  - 本脚本              从运行时导出，含完整 request/response 字段，前端据此生成 TS 类型

两者接口数量必须一致（66 path / 74 operation）。本脚本会校验这一点并报告差异。

用法：
    # 1. 先起服务
    # 2. python scripts/ci/export_runtime_spec.py [--url http://127.0.0.1:48080/v3/api-docs]
"""
import argparse
import json
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "contract/openapi.full.json"

# mate 业务接口的前缀（排除底座 system/infra 的数百个接口）
MATE_PREFIXES = (
    "/api/v1/",
    "/app-api/mate/",
    "/admin-api/mate/",
    "/internal/v1/",
)

# 需要一并保留的 schema 前缀（VO 都在 mate 包下）
MATE_SCHEMA_PREFIXES = (
    "App", "Admin", "Internal", "Cursor", "Idempotency",
)


def fetch(url: str) -> dict:
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))


def collect_refs(obj, acc: set):
    """递归收集 $ref 引用的 schema 名。"""
    if isinstance(obj, dict):
        ref = obj.get("$ref")
        if isinstance(ref, str) and ref.startswith("#/components/schemas/"):
            acc.add(ref.split("/")[-1])
        for v in obj.values():
            collect_refs(v, acc)
    elif isinstance(obj, list):
        for v in obj:
            collect_refs(v, acc)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="http://127.0.0.1:48080/v3/api-docs")
    args = ap.parse_args()

    try:
        spec = fetch(args.url)
    except Exception as e:
        print(f"::error::拉取 {args.url} 失败：{e}")
        print("提示：先启动服务 mvn spring-boot:run -Dspring-boot.run.profiles=local")
        return 1

    all_paths = spec.get("paths", {})
    mate_paths = {p: ops for p, ops in all_paths.items()
                  if p.startswith(MATE_PREFIXES)}

    if not mate_paths:
        print("::error::未匹配到任何 mate 接口，检查 MATE_PREFIXES")
        return 1

    # 收集所有被引用的 schema（含递归依赖）
    refs: set = set()
    for ops in mate_paths.values():
        collect_refs(ops, refs)

    all_schemas = spec.get("components", {}).get("schemas", {})
    # 递归补齐依赖
    resolved = set()
    queue = list(refs)
    while queue:
        name = queue.pop()
        if name in resolved or name not in all_schemas:
            continue
        resolved.add(name)
        sub: set = set()
        collect_refs(all_schemas[name], sub)
        queue.extend(sub - resolved)

    out = {
        "openapi": spec.get("openapi", "3.0.1"),
        "info": {
            "title": "觅搭 API 契约（字段级，运行时导出）",
            "version": "1.0.0",
            "description": (
                "由 yudao-server 的 /v3/api-docs 导出，只保留 mate 业务接口。"
                "前端用 openapi-typescript 生成本文件即可获得完整字段类型。"
                "路由级影子契约见 contract/openapi.yaml（CI 漂移检测用）。"
            ),
        },
        "servers": [{"url": "http://127.0.0.1:48080", "description": "本地联调"}],
        "tags": spec.get("tags", []),
        "paths": mate_paths,
        "components": {"schemas": {k: all_schemas[k] for k in sorted(resolved)
                                   if k in all_schemas}},
    }

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")

    ops = sum(len(v) for v in mate_paths.values())
    print(f"已生成 {OUT}")
    print(f"path: {len(mate_paths)}   operation: {ops}   schema: {len(out['components']['schemas'])}")

    # 与源码影子契约比对数量
    shadow = ROOT / "contract/openapi.yaml"
    if shadow.exists():
        try:
            import yaml
            y = yaml.safe_load(shadow.read_text(encoding="utf-8"))
            y_ops = sum(len(v) for v in y.get("paths", {}).values())
            y_paths = len(y.get("paths", {}))
            print(f"源码影子契约：path {y_paths}  operation {y_ops}")
            if (y_paths, y_ops) != (len(mate_paths), ops):
                print("::warning::运行时与源码生成的接口数量不一致，检查是否有条件装配的接口")
            else:
                print("[OK] 运行时与源码生成的接口数量一致")
        except ImportError:
            pass
    return 0


if __name__ == "__main__":
    sys.exit(main())