# -*- coding: utf-8 -*-
"""契约静态校验：数据库文档-后端乙.md 的字段清单 vs sql/mate/*.sql 的实际 DDL。

不依赖数据库，纯文本比对，用于在跑集成测试之前先抓出"少列/多列/类型不符/可空不一致"。
用法：
    python scripts/verify_schema.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOC = Path(r"D:/qq/缓存/数据库文档-后端乙.md")
SQL_DIR = ROOT / "ruoyi-vue-pro" / "sql" / "mate"

# 文档口径 -> 甲的 BaseDO 口径
RENAME = {"created_at": "create_time", "updated_at": "update_time"}
AUDIT_COLS = {"creator", "create_time", "updater", "update_time", "deleted"}

# 已知且允许的类型差异（文档口径 -> 本项目口径），不算问题
KNOWN_TYPE_DEVIATION = {
    # 甲侧 BaseDO 的 @TableLogic 用 Boolean 映射，框架内所有表的 deleted 都是 bit(1)
    ("deleted", "TINYINT"): "BIT(1)",
}

# 甲侧（M1/M2/M6）已有的表，不在乙侧契约范围内
OTHER_OWNER_TABLES = {
    "auth_record", "mate_demand", "mate_like", "mate_match", "mate_signup",
    "risk_blacklist", "risk_event", "risk_report", "sys_user",
}

TYPE_ALIAS = {"INT": "INT", "INTEGER": "INT", "TINYINT": "TINYINT", "BIGINT": "BIGINT"}


def norm_type(t: str) -> str:
    t = t.strip().upper().replace(" ", "")
    t = re.sub(r"UNSIGNED", "", t)
    return TYPE_ALIAS.get(t, t)


def parse_doc(path: Path):
    """从 markdown 的字段表格里抽取 {表名: {列: (类型, 可空)}}"""
    text = path.read_text(encoding="utf-8")
    tables = {}
    cur = None
    for line in text.splitlines():
        head = re.match(r"^###\s+([a-z0-9_]+)", line.strip())
        if head:
            cur = head.group(1)
            tables.setdefault(cur, {})
            continue
        if cur is None or not line.strip().startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 3:
            continue
        name, typ, nullable = cells[0], cells[1], cells[2]
        if not re.match(r"^[a-z][a-z0-9_]*$", name):
            continue  # 表头/分隔行
        if not re.match(r"^(BIGINT|INT|TINYINT|SMALLINT|VARCHAR|CHAR|DECIMAL|DATETIME|DATE|TIME|TEXT|JSON|BIT)",
                        typ.upper()):
            continue
        tables[cur][RENAME.get(name, name)] = (norm_type(typ), nullable)
    return {k: v for k, v in tables.items() if v}


def parse_sql(sql_dir: Path):
    ddl = {}
    for f in sorted(sql_dir.glob("*.sql")):
        text = f.read_text(encoding="utf-8")
        for m in re.finditer(r"CREATE TABLE (?:IF NOT EXISTS )?`?(\w+)`?\s*\((.*?)\n\)\s*ENGINE",
                             text, re.S):
            table, body = m.group(1), m.group(2)
            cols = {}
            for line in body.splitlines():
                cm = re.match(r"^\s*`([a-z0-9_]+)`\s+([A-Za-z]+(?:\([^)]*\))?)(.*)$", line)
                if not cm:
                    continue
                name, typ, rest = cm.group(1), cm.group(2), cm.group(3).upper()
                cols[name] = (norm_type(typ), "否" if "NOT NULL" in rest else "是")
            ddl[table] = cols
    return ddl


def main():
    doc = parse_doc(DOC)
    ddl = parse_sql(SQL_DIR)
    problems = 0
    adapted = []
    print(f"文档表数={len(doc)}  DDL表数={len(ddl)}\n")
    for table in sorted(doc):
        if table not in ddl:
            print(f"[缺表] {table}")
            problems += 1
            continue
        d, s = doc[table], ddl[table]
        miss = [c for c in d if c not in s]
        extra = [c for c in s if c not in d and c not in AUDIT_COLS]
        type_bad = []
        for c in d:
            if c not in s or d[c][0] == s[c][0]:
                continue
            if KNOWN_TYPE_DEVIATION.get((c, d[c][0])) == s[c][0]:
                adapted.append(f"{table}.{c}: 文档 {d[c][0]} -> 实际 {s[c][0]}")
            else:
                type_bad.append((c, d[c][0], s[c][0]))
        null_bad = [(c, d[c][1], s[c][1]) for c in d if c in s and d[c][0] == s[c][0] and d[c][1] != s[c][1]]
        if miss or extra or type_bad or null_bad:
            problems += 1
            print(f"[差异] {table}  文档{len(d)}列 / DDL{len(s)}列")
            for c in miss:
                print(f"    少列 {c} {d[c]}")
            for c in extra:
                print(f"    多列 {c} {s[c]}")
            for c, a, b in type_bad:
                print(f"    类型 {c}: 文档={a} DDL={b}")
            for c, a, b in null_bad:
                print(f"    可空 {c}: 文档={a} DDL={b}")
    for table in sorted(set(ddl) - set(doc)):
        if table in OTHER_OWNER_TABLES:
            continue
        print(f"[文档未定义] {table}（DDL 多出的表，需确认是否必要）")
        problems += 1
    if adapted:
        print("\n[已知口径适配]（框架 BaseDO 决定，非缺陷）")
        for a in adapted:
            print(f"    {a}")
    print(f"\n结论：{'23 张契约表字段全部对齐' if problems == 0 else f'{problems} 张表存在差异'}")
    return 0 if problems == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
