#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
跑 mate 模块单测并生成 JaCoCo 覆盖率报告。

为什么不用标准的 jacoco prepare-agent：
本项目的 surefire 用 JarManifestForkConfiguration 启动 fork 进程，pom 里写的
<argLine>（无论 ${argLine}、@{} 还是显式 -javaagent）都传不进去，只能在命令行用
-DargLine 注入。另外 destfile 如果放在含中文的路径下，agent 写不出文件，所以统一
落到系统临时目录（纯 ASCII），再单独调 jacoco:report 指定数据文件。

用法：
    python scripts/coverage.py                 # 跑全部测试类
    python scripts/coverage.py --open          # 跑完打开 HTML 报告
环境变量：
    MVN         mvn 可执行文件路径（默认从 PATH 找）
    JAVA_HOME   需要 JDK 21
"""

import csv
import os
import shutil
import subprocess
import sys
import tempfile
import webbrowser

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODULE_DIR = os.path.join(ROOT, "ruoyi-vue-pro")
MODULE = "yudao-module-mate"
JACOCO_VERSION = "0.8.12"

# 关注的核心类（打印覆盖率时优先展示）
FOCUS = [
    "TransferServiceImpl", "MemberOrderServiceImpl", "SplitOrderServiceImpl", "ReconServiceImpl",
    "ImMessageServiceImpl", "ImCardServiceImpl", "ImSessionServiceImpl",
    "MomentPostServiceImpl", "MomentInteractionServiceImpl", "MomentAuditServiceImpl",
    "GoodsServiceImpl", "DemandCardValidator",
]


def find_mvn():
    mvn = os.environ.get("MVN")
    if mvn and os.path.exists(mvn):
        return mvn
    found = shutil.which("mvn")
    if found:
        return found
    # Windows 上常见位置
    for cand in (
        r"C:\Users\%s\.m2\wrapper\dists" % os.environ.get("USERNAME", ""),
        r"C:\Program Files\apache-maven",
    ):
        if os.path.isdir(cand):
            for root, _dirs, files in os.walk(cand):
                if "mvn.cmd" in files or "mvn" in files:
                    return os.path.join(root, "mvn.cmd" if "mvn.cmd" in files else "mvn")
    sys.exit("找不到 mvn，请设置环境变量 MVN 指向 mvn 可执行文件")


def find_agent_jar():
    repo = os.path.join(os.path.expanduser("~"), ".m2", "repository", "org", "jacoco",
                        "org.jacoco.agent", JACOCO_VERSION,
                        "org.jacoco.agent-%s-runtime.jar" % JACOCO_VERSION)
    if not os.path.exists(repo):
        sys.exit("找不到 jacoco agent：%s\n先执行一次 mvn test 让插件下载依赖" % repo)
    return repo


def test_classes():
    base = os.path.join(MODULE_DIR, MODULE, "src", "test", "java")
    names = []
    for root, _dirs, files in os.walk(base):
        for f in files:
            if f.endswith("Test.java"):
                names.append(f[:-5])
    return sorted(names)


def run(cmd, cwd):
    print("$ " + " ".join(cmd))
    proc = subprocess.run(cmd, cwd=cwd, text=True, encoding="utf-8", errors="replace")
    return proc.returncode


def summarize(csv_path):
    with open(csv_path, encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    if not rows:
        return

    def pct(a, b):
        return round(a * 100.0 / b, 1) if b else 0.0

    line_c = sum(int(r["LINE_COVERED"]) for r in rows)
    line_m = sum(int(r["LINE_MISSED"]) for r in rows)
    br_c = sum(int(r["BRANCH_COVERED"]) for r in rows)
    br_m = sum(int(r["BRANCH_MISSED"]) for r in rows)

    print("\n===== 汇总（%d 个类）=====" % len(rows))
    print("行覆盖   %.1f%%" % pct(line_c, line_c + line_m))
    print("分支覆盖 %.1f%%" % pct(br_c, br_c + br_m))

    print("\n===== 核心类 =====")
    print("%-32s %8s %8s" % ("类", "行", "分支"))
    for r in rows:
        if r["CLASS"] in FOCUS:
            lc, lm = int(r["LINE_COVERED"]), int(r["LINE_MISSED"])
            bc, bm = int(r["BRANCH_COVERED"]), int(r["BRANCH_MISSED"])
            print("%-32s %7.1f%% %7.1f%%" % (r["CLASS"], pct(lc, lc + lm), pct(bc, bc + bm)))


def main():
    mvn = find_mvn()
    agent = find_agent_jar()
    classes = test_classes()
    if not classes:
        sys.exit("没找到测试类")

    exec_file = os.path.join(tempfile.gettempdir(), "mida-jacoco.exec")
    if os.path.exists(exec_file):
        os.remove(exec_file)

    arg_line = "-javaagent:%s=destfile=%s" % (agent.replace("\\", "/"), exec_file.replace("\\", "/"))
    code = run([
        mvn, "-o", "-B", "-pl", MODULE, "-am", "test",
        "-Dtest=" + ",".join(classes),
        "-Dsurefire.failIfNoSpecifiedTests=false",
        "-DargLine=" + arg_line,
    ], MODULE_DIR)
    if code != 0:
        sys.exit("测试失败，退出码 %d" % code)
    if not os.path.exists(exec_file):
        sys.exit("没有生成覆盖率数据文件，检查 surefire 是否 fork")

    code = run([
        mvn, "-o", "-B", "-pl", MODULE, "jacoco:report",
        "-Djacoco.dataFile=" + exec_file,
    ], MODULE_DIR)
    if code != 0:
        sys.exit("生成报告失败，退出码 %d" % code)

    site = os.path.join(MODULE_DIR, MODULE, "target", "site", "jacoco")
    summarize(os.path.join(site, "jacoco.csv"))
    print("\nHTML 报告：%s" % os.path.join(site, "index.html"))
    if "--open" in sys.argv:
        webbrowser.open("file:///" + os.path.join(site, "index.html").replace("\\", "/"))


if __name__ == "__main__":
    main()
