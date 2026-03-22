//////////////////////////////////////////
// Skill-Vetter RED FLAGS — YARA 规则
// 来源: clawhub.ai/spclaudehome/skill-vetter
// 用法: 放入 yara_rules/ 目录，自动加载
//////////////////////////////////////////

rule vetter_agent_memory_theft {

    meta:
        author = "SkillHub (derived from skill-vetter)"
        description = "Detects skills that read agent memory, identity, or personality files to steal context or impersonate the agent"
        classification = "harmful"
        threat_type = "AGENT MEMORY THEFT"

    strings:
        // Agent memory / identity 文件
        $memory_md    = "MEMORY.md" nocase
        $user_md      = "USER.md" nocase
        $soul_md      = "SOUL.md" nocase
        $identity_md  = "IDENTITY.md" nocase

        // Claude Code 特有的配置/记忆路径
        $claude_memory   = ".claude/memory" nocase
        $claude_settings = ".claude/settings" nocase
        $claude_config   = "claude_desktop_config.json" nocase

        // 文件访问动作
        $open_call  = /\b(open|read|cat|head|tail)\s*\(/
        $path_read  = /Path\s*\([^)]+\)\.(read_text|read_bytes)/

        // 排除：文档引用
        $doc_ref = /(README|CHANGELOG|CONTRIBUTING|LICENSE)/i

    condition:
        not $doc_ref and
        (
            // 任何 agent 文件名 + 文件读取动作
            (
                ($memory_md or $user_md or $soul_md or $identity_md) and
                ($open_call or $path_read)
            )
            or
            // Claude 配置路径（无论有没有 open 调用都危险）
            $claude_memory or
            $claude_settings or
            $claude_config
        )
}

rule vetter_ip_exfiltration {

    meta:
        author = "SkillHub (derived from skill-vetter)"
        description = "Detects network calls to raw IP addresses instead of domain names, which may bypass DNS logging and content filtering"
        classification = "harmful"
        threat_type = "IP-BASED EXFILTRATION"

    strings:
        // HTTP 请求到 IP 地址
        $http_ip = /https?:\/\/\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}/

        // Socket 连接到 IP
        $socket_ip = /connect\s*\(\s*\(?\s*['\"]\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}/

        // curl/wget 到 IP
        $curl_ip = /\b(curl|wget)\s+[^\n]*https?:\/\/\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}/

        // 排除：私有网段和本地地址
        $private_10     = /https?:\/\/10\.\d{1,3}\.\d{1,3}\.\d{1,3}/
        $private_172    = /https?:\/\/172\.(1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3}/
        $private_192    = /https?:\/\/192\.168\.\d{1,3}\.\d{1,3}/
        $loopback       = /https?:\/\/127\.0\.0\.1/
        $any_addr       = /https?:\/\/0\.0\.0\.0/
        $doc_comment    = /^(\s*#|\s*\/\/|\s*\*)/

    condition:
        not $private_10 and
        not $private_172 and
        not $private_192 and
        not $loopback and
        not $any_addr and
        not $doc_comment and
        (
            $http_ip or
            $socket_ip or
            $curl_ip
        )
}

rule vetter_browser_data_theft {

    meta:
        author = "SkillHub (derived from skill-vetter)"
        description = "Detects skills that access browser cookies, sessions, saved passwords, or profile data"
        classification = "harmful"
        threat_type = "BROWSER DATA THEFT"

    strings:
        // 浏览器数据路径
        $chrome_path   = /Google\/Chrome\/(Default|Profile)/ nocase
        $firefox_path  = /\.mozilla\/firefox\/[^\s]*profiles/ nocase
        $brave_path    = "BraveSoftware" nocase
        $chromium_path = /Chromium\/(Default|Profile)/ nocase
        $edge_path     = "Microsoft/Edge" nocase

        // macOS 路径
        $mac_chrome = "Library/Application Support/Google/Chrome" nocase

        // Cookie / session 数据库文件
        $cookies_db     = "Cookies" nocase
        $login_data     = "Login Data" nocase
        $web_data       = "Web Data" nocase
        $local_storage  = "Local Storage" nocase
        $session_storage = "Session Storage" nocase

        // sqlite3 打开浏览器 DB
        $sqlite_cookies = /sqlite3[^\n]*(Cookies|Login Data|Web Data)/i

        // 排除
        $set_cookie = /Set-Cookie/i
        $cookie_policy = /cookie[_\s]?policy/i
        $documentation = /(```|README|CHANGELOG)/i

    condition:
        not $set_cookie and
        not $cookie_policy and
        not $documentation and
        (
            // 浏览器路径访问
            $chrome_path or
            $firefox_path or
            $brave_path or
            $chromium_path or
            $edge_path or
            $mac_chrome or

            // 浏览器 DB 文件 + sqlite
            $sqlite_cookies or

            // 浏览器数据库文件名 + 浏览器路径（需要同时出现）
            (
                ($cookies_db or $login_data or $web_data) and
                ($chrome_path or $firefox_path or $mac_chrome or $chromium_path)
            )
        )
}
