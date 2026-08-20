import json

KEYWORDS_BY_SCOPE = {
    "ci": (
        "GitHub Actions", "workflow", "permissions", "Secrets",
        "GITHUB_TOKEN", "pull_request", "shell", "artifact", "cache",
        "supply-chain",
    ),
    "security-api": ("BOLA", "權限", "OpenAPI", "Controller", "IAM", "Security", "API"),
    "business": ("SOLID", "DRY", "KISS", "YAGNI", "Service", "架構"),
    "data": ("Repository", "Entity", "DataAccess", "資料庫", "EntityManager", "Entity"),
    "integration": ("Feign", "跨服務", "外部", "Microservice", "Client"),
    "python": ("Python", "Ruff", "Exception", "Type Hint", "snake_case"),
    "other": ("CI", "規範", "品質"),
}


def build_batch_prompt(
    scope: str,
    index: int,
    paths: list[str],
    diff: str,
    contract_text: str,
    relevant_rules: str,
) -> str:
    """組裝特定批次的 Review LLM 提示詞。"""
    json_template = (
        f'{{"batch":"{scope}-{index}",'
        f'"files_reviewed":{json.dumps(paths, ensure_ascii=False)},'
        '"findings":[{"severity":"CRITICAL|HIGH|MEDIUM|LOW",'
        '"confidence":"HIGH|MEDIUM|LOW","location":"file:line",'
        '"rule":"繁體中文規範依據","problem":"繁體中文問題",'
        '"evidence":"繁體中文證據","risk":"繁體中文風險",'
        '"recommendation":"繁體中文修正建議"}],'
        '"passed_checks":["繁體中文"],"coverage":"COMPLETE"}'
    )

    return f'''你是此 repository 的 Senior Code Reviewer，負責「{scope}」批次。
所有自然語言輸出必須使用繁體中文（zh-TW），禁止簡體中文。

開發規範.md 是唯一專案規則來源。AI_REVIEW.md 只定義 Review 執行與 Gate 原則。

【長度與格式約束】
各欄位描述務必簡潔扼要，單一 Finding 不得贅述；若無違規，findings 輸出空陣列 []。確保回應在 1000 Tokens 內結束。

【Review Contract】
{contract_text[:1500]}

【相關規範】
{relevant_rules}

【本批次檔案】（files_reviewed 欄位必須完整包含下列所有路徑字串，不可修改或遺漏）
{chr(10).join(paths)}

【PR Diff】
```diff
{diff}
```

只審查本批次。必須有程式碼或 workflow 證據才能提出 Finding。
不得提出與本 PR 無關的既有技術債或純風格建議。
CI 批次特別檢查最小權限、Secret trust boundary、untrusted input、Action pinning、
artifact/cache、fail-open 與 Review bypass。

只輸出合法 JSON，不得輸出 markdown：
{json_template}

不得輸出 blocking 或 decision；最終 Gate 完全由 deterministic policy 決定。'''


def filter_relevant_rules(rules_text: str, scope: str) -> str:
    """依據批次 scope 從開發規範文本中篩選相關段落。"""
    if not rules_text:
        return ""
    relevant_rules_list = []
    rule_lines = rules_text.splitlines()
    scope_keywords = KEYWORDS_BY_SCOPE.get(scope, KEYWORDS_BY_SCOPE["other"])
    for line_index, line_content in enumerate(rule_lines):
        if any(kw.lower() in line_content.lower() for kw in scope_keywords):
            start_pos = max(0, line_index - 2)
            end_pos = min(len(rule_lines), line_index + 14)
            relevant_rules_list.extend(rule_lines[start_pos:end_pos])
    return "\n".join(dict.fromkeys(relevant_rules_list))[:2000] or rules_text[:1500]
