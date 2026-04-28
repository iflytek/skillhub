#!/usr/bin/env python3
"""
repair_prd_json.py
------------------
使用 json-repair 修复并验证 prd.json 文件。
在每次生成 prd.json 之后运行，防止 LLM 输出中的
未转义引号、多余逗号等问题导致后续解析失败。

用法：
    python3 .claude/skills/ralph/scripts/repair_prd_json.py [prd_json_path]

参数：
    prd_json_path  可选，默认为 scripts/ralph/prd.json
"""

import json
import sys
from pathlib import Path

try:
    import json_repair
except ImportError:
    print("正在安装 json-repair...", flush=True)
    import subprocess
    result = subprocess.run(
        [sys.executable, "-m", "pip", "install", "json-repair==0.*",
         "--break-system-packages", "-q"],
        capture_output=True,
    )
    if result.returncode != 0:
        # 不带 --break-system-packages 再试一次（Linux/venv 环境）
        subprocess.run(
            [sys.executable, "-m", "pip", "install", "json-repair==0.*", "-q"],
            check=True,
        )
    import json_repair


def repair(prd_path: str) -> None:
    path = Path(prd_path)

    if not path.exists():
        print(f"❌ 文件不存在：{path}", file=sys.stderr)
        sys.exit(1)

    raw = path.read_text(encoding="utf-8")

    # json-repair 在不同版本中的签名不完全一致，这里做兼容处理。
    try:
        repaired = json_repair.repair_json(raw, ensure_ascii=False, indent=2)
    except TypeError:
        repaired = json_repair.repair_json(raw)
        repaired = json.dumps(json.loads(repaired), ensure_ascii=False, indent=2)

    # 二次验证：确保修复结果可被标准 json 模块正常解析
    try:
        json.loads(repaired)
    except json.JSONDecodeError as e:
        print(f"❌ 修复后仍无法解析：{e}", file=sys.stderr)
        sys.exit(1)

    path.write_text(repaired, encoding="utf-8")
    print(f"✅ prd.json 修复并验证成功：{path}")


if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else "scripts/ralph/prd.json"
    repair(target)
