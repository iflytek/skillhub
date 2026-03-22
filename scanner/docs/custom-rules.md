# 自定义静态分析规则

## 概述

Cisco skill-scanner 的静态分析引擎包含两种规则类型：

- **Regex 规则**（`signatures.yaml`）：基于正则表达式的模式匹配，按行扫描
- **YARA 规则**（`*.yara`）：基于 YARA 引擎的多模式匹配，支持跨行和组合条件

两种规则都打包在 `cisco-ai-skill-scanner` 的 Python 包内部，**Scanner HTTP API 不提供运行时加载外部规则的接口**。要注入自定义规则，需要在 Docker 构建或启动阶段覆盖包内文件。

## 包内规则路径

```
/usr/local/lib/python3.11/site-packages/skill_scanner/
├── data/
│   ├── rules/
│   │   └── signatures.yaml          # Regex 规则定义
│   └── yara_rules/
│       ├── code_execution_generic.yara
│       ├── command_injection_generic.yara
│       ├── credential_harvesting_generic.yara
│       ├── prompt_injection_generic.yara
│       ├── ... （共 13 个 .yara 文件）
│       └── tool_chaining_abuse_generic.yara
└── core/
    └── rules/
        ├── patterns.py               # RuleLoader - 加载 signatures.yaml
        └── yara_scanner.py           # YaraScanner - 加载 *.yara 文件
```

**加载逻辑**：
- `RuleLoader` 读取 `data/rules/signatures.yaml`，逐条编译正则表达式
- `YaraScanner` 读取 `data/yara_rules/` 目录下所有 `.yara` 文件，编译为 YARA 规则集

两个加载器都支持通过构造函数传入自定义路径，但 HTTP API 层没有暴露此参数。

---

## 注入自定义规则的方式

### 方案 A：Docker 卷挂载（开发环境推荐）

在 `docker-compose.yml` 中将本地规则目录挂载到容器内，覆盖包内文件：

```yaml
# docker-compose.yml
services:
  skill-scanner:
    build: ./scanner
    ports:
      - "8000:8000"
    volumes:
      # 追加自定义 Regex 规则（覆盖原有 signatures.yaml）
      - ./scanner/rules/signatures.yaml:/usr/local/lib/python3.11/site-packages/skill_scanner/data/rules/signatures.yaml:ro
      # 追加自定义 YARA 规则（覆盖整个 yara_rules 目录）
      - ./scanner/rules/yara/:/usr/local/lib/python3.11/site-packages/skill_scanner/data/yara_rules/:ro
```

**优点**：改规则后重启容器即可生效，不需要重新构建镜像

**缺点**：升级 scanner 版本时，官方新增的规则不会自动包含进来，需要手动合并

### 方案 B：Dockerfile COPY（生产环境推荐）

在 Dockerfile 构建阶段把自定义规则 COPY 进镜像：

```dockerfile
FROM python:3.11-alpine

WORKDIR /app

RUN apk add --no-cache --virtual .build-deps gcc musl-dev libffi-dev && \
    pip install --no-cache-dir cisco-ai-skill-scanner && \
    apk del .build-deps && \
    addgroup -S app && \
    adduser -S app -G app && \
    mkdir -p /tmp/skillhub-scans && \
    chown app:app /tmp/skillhub-scans

# 覆盖 Regex 规则
COPY rules/signatures.yaml /usr/local/lib/python3.11/site-packages/skill_scanner/data/rules/signatures.yaml

# 覆盖 YARA 规则目录
COPY rules/yara/ /usr/local/lib/python3.11/site-packages/skill_scanner/data/yara_rules/

USER app

EXPOSE 8000

CMD ["skill-scanner-api", "--host", "0.0.0.0", "--port", "8000"]
```

**优点**：规则随镜像版本管理，可追溯、可回滚

**缺点**：每次改规则都需要重新构建镜像

### 方案 C：追加而非覆盖（保留官方规则 + 自定义扩展）

如果希望保留官方规则并追加自定义规则：

**Regex 规则**：将官方 `signatures.yaml` 的内容复制出来，在末尾追加自定义规则后整体覆盖。

**YARA 规则**：官方的每个 `.yara` 文件是独立的，只需把自定义 `.yara` 文件放入同一目录即可。YARA 加载器会自动扫描目录下所有 `.yara` 文件。

推荐的目录结构：

```
scanner/
├── Dockerfile
├── rules/
│   ├── signatures.yaml          # 完整的 Regex 规则（官方 + 自定义）
│   └── yara/
│       ├── code_execution_generic.yara          # 官方规则（保留）
│       ├── command_injection_generic.yara        # 官方规则（保留）
│       ├── credential_harvesting_generic.yara    # 官方规则（保留）
│       ├── ...                                   # 其他官方规则
│       └── skillhub_custom.yara                  # ← 自定义 YARA 规则
└── README.md
```

---

## Regex 规则定义方法（signatures.yaml）

### 格式

```yaml
- id: RULE_UNIQUE_ID           # 唯一标识符，大写下划线命名
  category: <threat_category>  # 威胁分类（见下方枚举）
  severity: <severity_level>   # 严重级别（见下方枚举）
  patterns:                    # 正则表达式列表（匹配任一即触发）
    - "regex_pattern_1"
    - "regex_pattern_2"
  exclude_patterns:            # 排除模式（可选，匹配则跳过）
    - "safe_pattern"
  file_types:                  # 适用的文件类型（见下方枚举）
    - python
    - bash
  description: "规则描述"      # 检测到时显示的说明
  remediation: "修复建议"      # 建议的修复方式
```

### 可用的 category 值

| category | 说明 |
|----------|------|
| `prompt_injection` | Prompt 注入和指令覆盖 |
| `command_injection` | 命令和代码注入 |
| `data_exfiltration` | 数据泄露和隐私违规 |
| `unauthorized_tool_use` | 未授权工具和权限滥用 |
| `obfuscation` | 代码混淆和恶意软件指标 |
| `hardcoded_secrets` | 硬编码密钥和凭证泄露 |
| `social_engineering` | 社会工程和误导性元数据 |
| `resource_abuse` | 资源滥用和拒绝服务 |
| `policy_violation` | 策略违规 |

### 可用的 severity 值

| severity | 说明 |
|----------|------|
| `CRITICAL` | 严重 — 明确的恶意行为 |
| `HIGH` | 高危 — 高风险安全问题 |
| `MEDIUM` | 中危 — 需要关注的可疑行为 |
| `LOW` | 低危 — 轻微问题或建议 |
| `INFO` | 信息 — 仅供参考 |

### 可用的 file_types 值

| file_types | 匹配的文件扩展名 |
|------------|-----------------|
| `python` | `.py` |
| `bash` | `.sh`, `.bash`, `.zsh` |
| `markdown` | `.md` |
| `manifest` | `SKILL.md`（仅扫描 frontmatter） |
| `binary` | 二进制文件 |

### 示例：自定义 Regex 规则

```yaml
# ============================================================================
# 自定义规则：SkillHub 特定检测
# ============================================================================

# 检测使用 SkillHub 内部 API 的可疑行为
- id: SKILLHUB_INTERNAL_API_ACCESS
  category: data_exfiltration
  severity: HIGH
  patterns:
    - "skillhub\\.internal"
    - "/api/v1/admin"
    - "X-Internal-Token"
  file_types: [python, bash]
  description: "Skill attempts to access SkillHub internal APIs"
  remediation: "Skills should not access internal management APIs"

# 检测试图修改其他技能包的行为
- id: SKILLHUB_SKILL_TAMPERING
  category: unauthorized_tool_use
  severity: CRITICAL
  patterns:
    - "skillhub[_-]storage"
    - "/tmp/skillhub-scans"
    - "skill_versions.*UPDATE"
  file_types: [python, bash]
  description: "Skill attempts to tamper with SkillHub storage or other skills"
  remediation: "Remove code that accesses SkillHub internal storage"

# 检测过大的依赖安装
- id: SKILLHUB_EXCESSIVE_DEPS
  category: resource_abuse
  severity: MEDIUM
  patterns:
    - "pip install .{200,}"
    - "requirements\\.txt.*\\n.*torch"
    - "pip install.*tensorflow"
  exclude_patterns:
    - "# optional"
    - "# dev only"
  file_types: [python, bash]
  description: "Skill installs very large dependencies that may abuse resources"
  remediation: "Use lightweight alternatives or document why large dependencies are needed"
```

### 正则表达式语法说明

- 使用 Python `re` 模块语法
- `(?i)` — 不区分大小写
- `\\b` — 单词边界
- `(?<!...)` — 反向否定前瞻，如 `(?<!re\\.)\\bcompile` 匹配 `compile()` 但排除 `re.compile()`
- `[^)]*` — 匹配括号内的任意内容
- 每条 pattern 独立匹配，命中任意一条即触发该规则

---

## YARA 规则定义方法

### 格式

```yara
rule rule_name {

    meta:
        author = "YourTeam"
        description = "规则描述"
        classification = "harmful"     // harmful | suspicious | info
        threat_type = "THREAT TYPE"    // 大写，用于分类展示

    strings:
        // 定义要匹配的字符串模式
        $pattern_name = /正则表达式/i   // 正则（i=不区分大小写）
        $literal_str = "固定字符串"     // 精确匹配
        $hex_pattern = { 48 65 6C 6C }  // 十六进制匹配

        // 排除模式
        $safe_pattern = /安全模式/

    condition:
        // 布尔逻辑组合
        not $safe_pattern and
        (
            $pattern_name or
            ($literal_str and $hex_pattern)
        )
}
```

### meta 字段说明

| 字段 | 必填 | 说明 |
|------|------|------|
| `author` | 是 | 规则作者 |
| `description` | 是 | 规则描述，检测到时显示 |
| `classification` | 是 | `harmful`（有害）、`suspicious`（可疑）、`info`（信息） |
| `threat_type` | 是 | 威胁类型标签（大写），如 `CODE EXECUTION`、`CREDENTIAL HARVESTING` |

### strings 模式类型

```yara
strings:
    // 1. 正则表达式（最常用）
    $regex = /pattern/i                 // i = 不区分大小写
    $regex2 = /multi\nline/s            // s = 跨行匹配

    // 2. 精确字符串
    $text = "exact match"               // 区分大小写
    $nocase = "match" nocase            // 不区分大小写
    $wide = "match" wide                // 宽字符（UTF-16）

    // 3. 十六进制模式
    $hex = { E8 ?? ?? ?? FF }           // ?? = 通配符
    $hex2 = { E8 [2-4] FF }            // [2-4] = 2到4字节通配
```

### condition 逻辑运算

```yara
condition:
    // 布尔运算
    $a and $b                           // 同时匹配
    $a or $b                            // 匹配任一
    not $a                              // 不匹配
    ($a or $b) and not $c               // 组合

    // 计数
    #a > 3                              // $a 出现超过 3 次
    any of ($pattern*)                  // 任一 $pattern* 匹配
    all of ($required*)                 // 所有 $required* 都匹配
    2 of ($a, $b, $c)                   // 三个中匹配任意两个

    // 文件大小
    filesize < 1MB                      // 文件小于 1MB
```

### 示例：自定义 YARA 规则

将以下内容保存为 `scanner/rules/yara/skillhub_custom.yara`：

```yara
//////////////////////////////////////////
// SkillHub 自定义检测规则
// 检测针对 SkillHub 平台的特定威胁
//////////////////////////////////////////

rule skillhub_namespace_abuse {

    meta:
        author = "SkillHub Security"
        description = "Detects attempts to manipulate SkillHub namespaces or escalate privileges"
        classification = "harmful"
        threat_type = "PRIVILEGE ESCALATION"

    strings:
        // 尝试访问其他命名空间
        $ns_traversal = /namespace[_\-]?id\s*=\s*['\"][^'\"]+['\"]/i
        $ns_override = /X-Namespace-Override/i

        // 尝试伪造身份
        $mock_user = /X-Mock-User-Id/i
        $admin_escalation = /role\s*=\s*['\"](admin|super_admin)['"]/i

        // 排除测试代码
        $test_file = /def\s+test_/
        $test_import = /import\s+pytest/

    condition:
        not $test_file and
        not $test_import and
        (
            $ns_traversal or
            $ns_override or
            $mock_user or
            $admin_escalation
        )
}

rule skillhub_scan_evasion {

    meta:
        author = "SkillHub Security"
        description = "Detects attempts to evade security scanning"
        classification = "harmful"
        threat_type = "SCAN EVASION"

    strings:
        // 检测文件在扫描后执行的延迟加载
        $delayed_import = /importlib\.import_module\s*\(\s*[a-z_]+\s*\)/i
        $dynamic_exec = /getattr\s*\(\s*__import__/i

        // 检测条件性恶意代码（仅在非扫描环境执行）
        $env_check_scanner = /os\.environ\.get\s*\(\s*['"]SCANNER/i
        $env_check_sandbox = /os\.environ\.get\s*\(\s*['"]SANDBOX/i

        // 排除合法用途
        $legitimate_plugin = /plugin_loader|extension_manager/i

    condition:
        not $legitimate_plugin and
        (
            ($delayed_import and $env_check_scanner) or
            ($dynamic_exec and $env_check_sandbox) or
            ($delayed_import and $dynamic_exec)
        )
}
```

---

## 测试自定义规则

### 验证 Regex 规则语法

```bash
# 在容器内验证 signatures.yaml 能否被正确解析
docker exec skillhub-skill-scanner-1 python3 -c "
import yaml
with open('/usr/local/lib/python3.11/site-packages/skill_scanner/data/rules/signatures.yaml') as f:
    rules = yaml.safe_load(f)
print(f'Loaded {len(rules)} rules')
for r in rules:
    print(f\"  {r['id']} [{r['severity']}] {r['category']}\")
"
```

### 验证 YARA 规则语法

```bash
# 在容器内验证所有 .yara 文件能否被编译
docker exec skillhub-skill-scanner-1 python3 -c "
import yara
from pathlib import Path
rules_dir = Path('/usr/local/lib/python3.11/site-packages/skill_scanner/data/yara_rules')
for f in sorted(rules_dir.glob('*.yara')):
    try:
        yara.compile(filepath=str(f))
        print(f'  OK: {f.name}')
    except yara.SyntaxError as e:
        print(f'  FAIL: {f.name} -> {e}')
"
```

### 端到端测试

```bash
# 创建包含可疑代码的测试技能包
mkdir -p /tmp/test-custom-rule
cat > /tmp/test-custom-rule/SKILL.md << 'EOF'
---
name: test-custom
description: A test skill for custom rule validation
version: 1.0.0
---
This is a test.
EOF

cat > /tmp/test-custom-rule/main.py << 'EOF'
import os
# 这段代码应触发自定义规则
mock_header = "X-Mock-User-Id: admin"
EOF

cd /tmp/test-custom-rule && zip -r /tmp/test-custom.zip .

# 提交扫描
curl -s -X POST http://localhost:8000/scan-upload \
  -F "file=@/tmp/test-custom.zip" | python3 -m json.tool
```

---

## 注意事项

1. **版本升级**：升级 `cisco-ai-skill-scanner` 时，官方规则会被覆盖。使用方案 A（卷挂载）时需手动合并新规则；使用方案 B（Dockerfile COPY）时需在 Dockerfile 中重新 COPY。

2. **规则 ID 唯一性**：Regex 规则的 `id` 字段必须全局唯一。建议自定义规则使用 `SKILLHUB_` 前缀避免与官方规则冲突。

3. **YARA 规则命名**：YARA 文件名作为 namespace，`rule` 名称必须全局唯一。建议自定义规则文件使用 `skillhub_` 前缀。

4. **性能影响**：正则表达式过于复杂或 YARA 规则过多会增加扫描时间。建议定期评估规则数量和扫描耗时。

5. **误报管理**：新增规则后应用测试技能包验证，关注 `exclude_patterns`（Regex）和 `condition` 中的排除逻辑（YARA），避免误报。

---

## 相关文档

- [配置说明](./configuration.md)
- [故障影响分析](./failure-impact-analysis.md)
- [运维监控指南](./monitoring-guide.md)
