#!/usr/bin/env python
"""MyBatis Mapper XML 良构性校验。

为什么需要单独跑这一步：
  `mvn compile` 只编 Java、不解析 mapper XML，XML 写坏了照样 BUILD SUCCESS，
  直到容器启动时 mybatis 解析报 SAXParseException 才炸（现象是应用 Exited (1)）。
  最典型的坑是 XML 注释里出现 `--`：MyBatis 的 XML 是良构 XML，
  注释内不允许出现双连字符，而这个错误编译期完全看不出来。

用法：
    python scripts/check-mapper-xml.py
"""

import sys
import xml.dom.minidom
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAPPER_DIR = ROOT / "booknest_backend" / "booknest-server" / "src" / "main" / "resources" / "mapper"


def main() -> int:
    if not MAPPER_DIR.is_dir():
        print(f"未找到 mapper 目录：{MAPPER_DIR}")
        return 2

    files = sorted(MAPPER_DIR.glob("*.xml"))
    if not files:
        print(f"mapper 目录下没有 XML：{MAPPER_DIR}")
        return 2

    failed = 0
    for path in files:
        raw = path.read_bytes()
        text = raw.decode("utf-8")
        try:
            xml.dom.minidom.parseString(text)
        except Exception as exc:
            failed += 1
            print(f"\n[X] {path.name}")
            print(f"    {exc}")

            # 额外提示最常见的成因：注释里混入了 --
            for lineno, line in enumerate(text.splitlines(), start=1):
                if "--" in line and not line.lstrip().startswith("<!--") and not line.lstrip().startswith("-->"):
                    print(f"    第 {lineno} 行疑似含双连字符：{line.strip()[:100]}")

    if failed:
        print(f"\n共 {len(files)} 个 mapper XML，{failed} 个解析失败")
        return 1

    print(f"OK: {len(files)} 个 mapper XML 全部良构")
    return 0


if __name__ == "__main__":
    sys.exit(main())
