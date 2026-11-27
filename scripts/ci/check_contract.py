#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
契约漂移检测。

规则：contract/openapi.yaml 必须与「从控制器源码重新生成」的结果一致。
源码改了注解但没重新生成契约 → 红。

为什么需要：
  前端靠 openapi.yaml 生成 TS 类型。如果后端改了 @RequireAuthLevel 或新增接口
  却没更新契约，前端会拿到过期类型，编译通过但运行时 403/404——这是最难查的一类问题。

用法：
    python scripts/ci/check_contract.py           # 只检查，不写盘
    python scripts/ci/check_contract.py --fix     # 重新生成并覆盖契约文件

实现要点：
  生成器会把结果写回 contract/openapi.yaml。检测模式下必须保证：
  1. 无论结果如何，工作区文件内容恢复原样（否则 CI 判定为"有未提交改动"或误报）；
  2. 用字节比较，不用 git diff（CI 浅克隆时 git 状态不可靠）。
"""
import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTRACT = ROOT / "contract/openapi.yaml"
GENERATOR = ROOT / "scripts/gen_contract.py"


def generate() -> bytes:
    """运行生成器，返回生成后的契约字节内容。"""
    import subprocess
    r = subprocess.run([sys.executable, str(GENERATOR)],
                       cwd=str(ROOT), capture_output=True, text=True)
    if r.returncode != 0:
        print("::error::契约生成失败")
        print(r.stdout)
        print(r.stderr)
        raise SystemExit(1)
    return CONTRACT.read_bytes()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fix", action="store_true", help="重新生成并覆盖契约文件")
    args = ap.parse_args()

    if not GENERATOR.exists():
        print(f"::error::找不到契约生成器 {GENERATOR}")
        return 1
    if not CONTRACT.exists():
        print(f"::error::找不到契约文件 {CONTRACT}")
        print("提示：先运行 python scripts/gen_contract.py 生成")
        return 1

    committed = CONTRACT.read_bytes()

    try:
        generated = generate()
    finally:
        # 生成器会覆盖文件。检测模式下必须还原成提交时的内容，
        # 保证本条命令对工作区是只读的。
        if not args.fix:
            CONTRACT.write_bytes(committed)

    if committed == generated:
        print("契约与源码一致，无漂移。")
        return 0

    if args.fix:
        print("已重新生成并覆盖 contract/openapi.yaml。")
        return 0

    print("::error::契约漂移：控制器源码与 contract/openapi.yaml 不一致")
    print()
    _print_diff(committed.decode("utf-8", "replace"),
                generated.decode("utf-8", "replace"))
    print()
    print("修复方式：")
    print("  python scripts/ci/check_contract.py --fix")
    print("  然后把更新后的 contract/openapi.yaml 一起提交进同一次 PR")
    return 1


def _print_diff(old: str, new: str, limit: int = 80):
    import difflib
    old_lines = old.splitlines(keepends=True)
    new_lines = new.splitlines(keepends=True)
    diff = list(difflib.unified_diff(old_lines, new_lines,
                                     fromfile="contract/openapi.yaml (已提交)",
                                     tofile="contract/openapi.yaml (源码生成)",
                                     n=2))
    if not diff:
        print("(字节级差异但行内容相同，通常是换行符 CRLF/LF 不一致)")
        return
    for line in diff[:limit]:
        print(line.rstrip("\n"))
    if len(diff) > limit:
        print(f"... 还有 {len(diff) - limit} 行未显示")


if __name__ == "__main__":
    sys.exit(main())