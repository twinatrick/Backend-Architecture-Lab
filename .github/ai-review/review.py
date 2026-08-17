import json
import os
import sys
from pathlib import Path
import requests
from engine import evaluate, load_policy, validate_finding

ROOT = Path(__file__).resolve().parents[2]
REPO = os.environ["REPO"]
EVENT_PATH = os.environ["EVENT_PATH"]
GH_TOKEN = os.environ.get("GH_TOKEN")
GROQ_API_KEY = os.environ.get("GROQ_API_KEY")

if not GH_TOKEN or not GROQ_API_KEY:
    raise SystemExit("Required trusted secrets are not configured.")

headers = {
    "Authorization": f"Bearer {GH_TOKEN}",
    "Accept": "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
}

def gh_get(url, **kwargs):
    response = requests.get(url, headers=headers, timeout=30, **kwargs)
    response.raise_for_status()
    return response.json()

def load_json(path):
    return json.loads(path.read_text(encoding="utf-8"))

def publish_review(pr_number, body, event="COMMENT"):
    marker = "<!-- ai-review-gate -->"
    body = body.rstrip() + "\n\n" + marker
    comments = gh_get(f"https://api.github.com/repos/{REPO}/issues/{pr_number}/comments", params={"per_page": 100})
    existing = next((c for c in comments if marker in c.get("body", "")), None)
    if existing:
        response = requests.patch(
            f"https://api.github.com/repos/{REPO}/issues/comments/{existing['id']}",
            headers=headers, json={"body": body}, timeout=30,
        )
    else:
        response = requests.post(
            f"https://api.github.com/repos/{REPO}/issues/{pr_number}/comments",
            headers=headers, json={"body": body}, timeout=30,
        )
    response.raise_for_status()

    if event in {"REQUEST_CHANGES", "APPROVE"}:
        review_response = requests.post(
            f"https://api.github.com/repos/{REPO}/pulls/{pr_number}/reviews",
            headers=headers, json={"body": body, "event": event}, timeout=30,
        )
        review_response.raise_for_status()

def provider_failure(pr_number, attempted, details):
    body = """# AI Architecture & Security Review\n\n## 審查結果\nREQUEST_CHANGES\n\n## 🔴 Review 執行失敗\n本次 AI Review 無法取得可用的 AI Provider，因此不能視為 Review 通過。\n\n**原因**：所有已配置的 Groq 模型都無法使用。\n\n**已嘗試模型**：\n""" + "\n".join(f"- `{model}`：{reason}" for model, reason in details) + """\n\n這是 fail-closed 行為：AI Review 不可用時不得產生 APPROVE。請修復 Provider / Model 設定後重新執行 CI。\n\n## 執行原則\n- 所有自然語言內容使用繁體中文。\n- AI Provider 失敗不會被當成 Review PASS。\n- GitHub PR 會留下明確的失敗說明。\n"""
    publish_review(pr_number, body, "REQUEST_CHANGES")
    raise SystemExit(f"AI provider unavailable: {attempted}")

def get_available_models():
    response = requests.get(
        "https://api.groq.com/openai/v1/models",
        headers={"Authorization": f"Bearer {GROQ_API_KEY}", "Content-Type": "application/json"},
        timeout=30,
    )
    response.raise_for_status()
    return {item.get("id") for item in response.json().get("data", [])}

def chat_completion(prompt):
    # Groq deprecated llama-3.1-8b-instant and llama-3.3-70b-versatile on 2026-08-16.
    # Prefer current production models; keep the fallback list centralized and observable.
    candidates = [
        "openai/gpt-oss-120b",
        "qwen/qwen3.6-27b",
        "openai/gpt-oss-20b",
    ]
    try:
        available = get_available_models()
        candidates = [model for model in candidates if model in available]
    except requests.RequestException as exc:
        print(f"Unable to enumerate Groq models: {exc}; trying configured candidates directly.")

    attempted = []
    details = []
    for model in candidates:
        attempted.append(model)
        try:
            response = requests.post(
                "https://api.groq.com/openai/v1/chat/completions",
                headers={"Authorization": f"Bearer {GROQ_API_KEY}", "Content-Type": "application/json"},
                json={
                    "model": model,
                    "messages": [
                        {"role": "system", "content": "你必須使用繁體中文。只輸出合法 JSON。不得捏造 Finding。"},
                        {"role": "user", "content": prompt},
                    ],
                    "temperature": 0.1,
                },
                timeout=120,
            )
            if response.ok:
                print(f"Using Groq model: {model}")
                return response.json()["choices"][0]["message"]["content"]
            reason = f"HTTP {response.status_code}: {response.text[:300]}"
            details.append((model, reason))
            print(f"Model {model} failed: {reason}")
        except requests.RequestException as exc:
            details.append((model, str(exc)))
            print(f"Model {model} request failed: {exc}")

    raise RuntimeError(json.dumps(details, ensure_ascii=False))

event = load_json(Path(EVENT_PATH))
pr_number = (event.get("pull_requests") or [{}])[0].get("number")
if not pr_number:
    raise SystemExit("No PR associated with workflow_run.")

pr = gh_get(f"https://api.github.com/repos/{REPO}/pulls/{pr_number}")
if pr.get("state") != "open":
    raise SystemExit("PR is not open; refusing to publish a new review gate.")

files = []
page = 1
while True:
    chunk = gh_get(f"https://api.github.com/repos/{REPO}/pulls/{pr_number}/files", params={"per_page": 100, "page": page})
    files.extend(chunk)
    if len(chunk) < 100:
        break
    page += 1

changed = [item["filename"] for item in files]
if not changed:
    raise SystemExit("PR contains no changed files.")

rules = (ROOT / "開發規範.md").read_text(encoding="utf-8")
contract = (ROOT / ".github/AI_REVIEW.md").read_text(encoding="utf-8")
policy = load_policy()

groups = {k: [] for k in ("ci", "security-api", "business", "data", "integration", "python", "other")}
for filename in changed:
    path = filename.lower()
    if path.startswith(".github/"):
        groups["ci"].append(filename)
    elif path.endswith(".py"):
        groups["python"].append(filename)
    elif any(x in path for x in ("controller", "security", "permission", "auth", "openapi")):
        groups["security-api"].append(filename)
    elif any(x in path for x in ("feign", "client", "integration", "external")):
        groups["integration"].append(filename)
    elif any(x in path for x in ("repository", "entity", "dao", "migration", "mapper")):
        groups["data"].append(filename)
    elif any(x in path for x in ("service", "domain", "usecase")):
        groups["business"].append(filename)
    else:
        groups["other"].append(filename)

max_chars = 24000
batches = []
for scope, paths in groups.items():
    if not paths:
        continue
    current, size = [], 0
    for filename in paths:
        item = next(x for x in files if x["filename"] == filename)
        cost = len(filename) + max(len(item.get("patch") or ""), 1000)
        if current and size + cost > max_chars:
            batches.append((scope, current))
            current, size = [], 0
        current.append(filename)
        size += cost
    if current:
        batches.append((scope, current))

expected = [filename for _, paths in batches for filename in paths]
if sorted(expected) != sorted(changed) or len(expected) != len(set(expected)):
    raise SystemExit("Coverage planning mismatch: every changed file must belong to exactly one batch.")

keywords = {
    "ci": ("GitHub Actions", "workflow", "permissions", "Secrets", "GITHUB_TOKEN", "pull_request", "shell", "artifact", "cache", "supply-chain"),
    "security-api": ("BOLA", "權限", "OpenAPI", "Controller", "IAM", "Security", "API"),
    "business": ("SOLID", "DRY", "KISS", "YAGNI", "Service", "架構"),
    "data": ("Repository", "Entity", "DataAccess", "資料庫", "EntityManager", "Entity"),
    "integration": ("Feign", "跨服務", "外部", "Microservice", "Client"),
    "python": ("Python", "Ruff", "Exception", "Type Hint", "snake_case"),
    "other": ("CI", "規範", "品質"),
}

results = []
for index, (scope, paths) in enumerate(batches, 1):
    relevant = []
    rule_lines = rules.splitlines()
    for i, line in enumerate(rule_lines):
        if any(k.lower() in line.lower() for k in keywords[scope]):
            relevant.extend(rule_lines[max(0, i - 2): min(len(rule_lines), i + 14)])
    relevant_rules = "\n".join(dict.fromkeys(relevant))[:12000] or rules[:7000]
    diff = "\n\n".join(f"diff -- {item['filename']}\n{item.get('patch') or '[GitHub did not provide a patch; review metadata only]'}" for item in files if item["filename"] in paths)[:26000]
    prompt = f'''你是此 repository 的 Senior Code Reviewer，負責「{scope}」批次。\n所有自然語言輸出必須使用繁體中文（zh-TW），禁止簡體中文。\n\n開發規範.md 是唯一專案規則來源。AI_REVIEW.md 只定義 Review 執行與 Gate 原則。\n\n【Review Contract】\n{contract[:9000]}\n\n【相關規範】\n{relevant_rules}\n\n【本批次檔案】\n{chr(10).join(paths)}\n\n【PR Diff】\n```diff\n{diff}\n```\n\n只審查本批次。必須有程式碼或 workflow 證據才能提出 Finding。不得提出與本 PR 無關的既有技術債或純風格建議。\nCI 批次特別檢查最小權限、Secret trust boundary、untrusted input、Action pinning、artifact/cache、fail-open 與 Review bypass。\n\n只輸出合法 JSON，不得輸出 markdown：\n{{"batch":"{scope}-{index}","files_reviewed":{json.dumps(paths, ensure_ascii=False)},"findings":[{{"severity":"CRITICAL|HIGH|MEDIUM|LOW","confidence":"HIGH|MEDIUM|LOW","location":"file:line","rule":"繁體中文規範依據","problem":"繁體中文問題","evidence":"繁體中文證據","risk":"繁體中文風險","recommendation":"繁體中文修正建議"}}],"passed_checks":["繁體中文"],"coverage":"COMPLETE"}}\n\n不得輸出 blocking 或 decision；最終 Gate 完全由 deterministic policy 決定。'''
    try:
        text = chat_completion(prompt).strip()
    except RuntimeError as exc:
        provider_failure(pr_number, "Groq", json.loads(str(exc)))
    if text.startswith("```"):
        text = text.split("\n", 1)[1].rsplit("```", 1)[0].strip()
    data = json.loads(text)
    if data.get("coverage") != "COMPLETE" or data.get("files_reviewed") != paths:
        raise SystemExit(f"Batch coverage validation failed: {scope}-{index}")
    for finding in data.get("findings", []):
        if not validate_finding(finding, policy):
            raise SystemExit(f"Invalid finding schema in {scope}-{index}")
    results.append(data)

reviewed = [filename for data in results for filename in data["files_reviewed"]]
findings = [finding for data in results for finding in data.get("findings", [])]
passed = [check for data in results for check in data.get("passed_checks", [])]
result = evaluate(findings, expected, reviewed, policy)
decision = result["decision"]
unique = result["findings"]
blocking = result["blocking_findings"]

report = ["# AI Code Review", "", f"## 審查結果\n{decision}", "", f"已審查 {len(changed)} 個變更檔案、{len(results)} 個批次；共 {len(unique)} 個 Finding，其中 {len(blocking)} 個阻擋項目。", ""]
if unique:
    report.append("## Findings")
    for finding in unique:
        report += ["", f"### [{finding['severity']}] {finding['problem']}", f"**位置**：`{finding['location']}`", f"**規範依據**：{finding['rule']}", f"**證據**：{finding['evidence']}", f"**風險**：{finding['risk']}", f"**修正建議**：{finding['recommendation']}", f"**信心度**：{finding['confidence']}"]
else:
    report += ["## Findings", "無。"]
report += ["", "## 已通過檢查"] + [f"- {item}" for item in sorted(set(passed))]
report += ["", "## 審查結論", "本次 Review 由分批 AI 分析，並由 deterministic engine 與 policy 統一計算阻擋條件。"]
body = "\n".join(report)
publish_review(pr_number, body, decision)

Path("review.md").write_text(body + "\n", encoding="utf-8")
Path("ai-review.json").write_text(json.dumps({"decision": decision, "findings": unique, "blocking_findings": blocking, "batches": len(results), "files_reviewed": changed}, ensure_ascii=False, indent=2), encoding="utf-8")
print(body)
if decision == "REQUEST_CHANGES":
    raise SystemExit(1)
