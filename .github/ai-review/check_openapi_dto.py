import os
import re
from typing import Any

from check_rules import (
    RULE_OPENAPI_DTO_CONTRACT,
    RULE_RESTFUL_ENDPOINT_NAMING,
    make_finding,
)

UUID_REGEX = re.compile(
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)

BANNED_FIELD_NAMES = frozenset({
    "string",
    "large",
    "small",
    "key",
    "roleArr",
    "deleteFunction",
    "saveMainFunction",
    "saveFunctionNewChild",
})

BANNED_ROUTE_VERBS = frozenset({
    "/add",
    "/update",
    "/delete",
    "/create",
    "/saveUser",
    "/updateUser",
    "/deleteUser",
})

PLACEHOLDER_REGEX = re.compile(r"^(?:TODO|todo|test|xxx|placeholder|\s*)$", re.IGNORECASE)


def is_dto_or_vo_file(path: str, filename: str) -> bool:
    normalized_path = path.replace("\\", "/")
    if "src/test/java/" in normalized_path:
        return False
    if "/Vo/" in normalized_path or "/dto/" in normalized_path or "/Dto/" in normalized_path:
        return True
    dto_suffixes = (
        "Vo.java", "VO.java", "Dto.java", "DTO.java",
        "Request.java", "Query.java", "Filter.java"
    )
    return bool(filename.endswith(dto_suffixes))


def check_controller_openapi_contract(
    path: str,
    content: str,
    lines: list[str],
    changed_lines: set[int] | None = None,
) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []

    for idx, raw_line in enumerate(lines, start=1):
        if changed_lines is not None and idx not in changed_lines:
            continue
        line = raw_line.lstrip("+- ").strip()

        # 1. 檢查 RESTful 路由命名（攔截 RPC 式動詞路徑）
        route_pattern = (
            r'@(?:GetMapping|PostMapping|PutMapping|DeleteMapping|'
            r'PatchMapping|RequestMapping)\s*\(\s*(?:(?:value|path)\s*=\s*)?["\']([^"\']+)["\']'
        )
        route_match = re.search(route_pattern, line)
        if route_match:
            route_path = route_match.group(1)
            for banned_verb in BANNED_ROUTE_VERBS:
                if banned_verb in route_path:
                    findings.append(make_finding(
                        path, idx, "HIGH", "COMPLIANCE",
                        RULE_RESTFUL_ENDPOINT_NAMING,
                        f"Controller 端點路由包含非標準 RPC 動詞 '{banned_verb}': {route_path}",
                        raw_line.strip(),
                        "違反《開發規範.md》§3.3 RESTful 路由命名標準，混亂的 RPC 動詞降低 API 一致性",
                        "改為標準 RESTful 動詞（POST 新增、PUT /{id} 修改、DELETE /{id} 刪除）",
                    ))
                    break

        # 2. 檢查 Request Body 參數（強制使用專屬 @ApiBody 複合註解）
        if "@RequestBody" in line and not line.startswith("//"):
            weak_map_pattern = r"@RequestBody\s+(?:@Valid\s+)?(?:Map<|HashMap<|JSONObject|JsonNode)"
            if re.search(weak_map_pattern, line):
                findings.append(make_finding(
                    path, idx, "HIGH", "COMPLIANCE",
                    RULE_OPENAPI_DTO_CONTRACT,
                    "Controller @RequestBody 嚴禁使用 Map 或弱型別 JSON 物件",
                    raw_line.strip(),
                    "弱型別輸入破壞編譯期型別校驗且無法在 Swagger 呈現確定性資料契約",
                    "定義明確標註 @ApiDto 的強型別 Request DTO 類別並改用 @ApiBody",
                ))
            else:
                findings.append(make_finding(
                    path, idx, "HIGH", "COMPLIANCE",
                    RULE_OPENAPI_DTO_CONTRACT,
                    "Controller Request Body 請改用專案專屬複合註解 @ApiBody 取代原生 @RequestBody",
                    raw_line.strip(),
                    "專案已包裝 @ApiBody 整合 @RequestBody、@Valid 與 OpenAPI 契約，嚴禁散落撰寫原生註解",
                    "將 @Valid @RequestBody 或 @RequestBody 改為單一 @ApiBody(description = \"...\")",
                ))

        # 3. 檢查 @PathVariable（強制使用專屬 @ApiPath 複合註解）
        if "@PathVariable" in line and not line.startswith("//"):
            findings.append(make_finding(
                path, idx, "HIGH", "COMPLIANCE",
                RULE_OPENAPI_DTO_CONTRACT,
                "Controller 路徑參數請改用專案專屬複合註解 @ApiPath 取代原生 @PathVariable",
                raw_line.strip(),
                "專案已包裝 @ApiPath 整合路徑變數解析與 OpenAPI 契約，維持 Controller 代碼簡潔與統一標準",
                "將 @PathVariable 改為單一 @ApiPath(description = \"...\", format = \"uuid\")",
            ))

        # 4. 檢查 @RequestParam（強制使用專屬 @ApiQuery 複合註解）
        if "@RequestParam" in line and not line.startswith("//"):
            findings.append(make_finding(
                path, idx, "HIGH", "COMPLIANCE",
                RULE_OPENAPI_DTO_CONTRACT,
                "Controller 查詢參數請改用專案專屬複合註解 @ApiQuery 取代原生 @RequestParam",
                raw_line.strip(),
                "專案已包裝 @ApiQuery 整合查詢參數、預設值與 OpenAPI 契約，維持 Controller 代碼簡潔與統一標準",
                "將 @RequestParam 改為單一 @ApiQuery(description = \"...\", defaultValue = \"...\")",
            ))

    return findings


def check_dto_field_and_schema_contract(
    path: str,
    content: str,
    lines: list[str],
    changed_lines: set[int] | None = None,
) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    filename = os.path.basename(path)

    if not is_dto_or_vo_file(path, filename):
        return findings

    # 檢查是否為 class / record 宣告
    class_match = re.search(r"\bpublic\s+(?:class|record)\s+([A-Za-z0-9_]+)\b", content)
    if not class_match:
        return findings

    class_name = class_match.group(1)

    # 1. 檢查類別是否有 @ApiDto
    has_api_dto = "@ApiDto" in content
    if not has_api_dto:
        # 抓取類別宣告行
        class_line_idx = 1
        for idx, line in enumerate(lines, start=1):
            if re.search(rf"\bpublic\s+(?:class|record)\s+{class_name}\b", line):
                class_line_idx = idx
                break
        if changed_lines is None or class_line_idx in changed_lines:
            findings.append(make_finding(
                path, class_line_idx, "HIGH", "COMPLIANCE",
                RULE_OPENAPI_DTO_CONTRACT,
                f"DTO/VO 類別 '{class_name}' 未標註專案標準 @ApiDto(description = \"...\") 註解",
                lines[class_line_idx - 1].strip() if class_line_idx <= len(lines) else class_name,
                "未聲明 @ApiDto 導致 API 契約缺乏領域模型層級說明，破壞 Swagger 規格完整性",
                "在類別上方加上 @ApiDto(description = \"...\") 提供完整中文描述",
            ))
    else:
        # 檢查 @ApiDto description 是否空值或佔位符
        dto_desc_regex = r'@ApiDto\s*\(\s*(?:description\s*=\s*)?["\']([^"\']*)["\']'
        dto_desc_match = re.search(dto_desc_regex, content)
        if dto_desc_match:
            desc_val = dto_desc_match.group(1).strip()
            if PLACEHOLDER_REGEX.match(desc_val):
                for idx, line in enumerate(lines, start=1):
                    if "@ApiDto" in line:
                        if changed_lines is None or idx in changed_lines:
                            findings.append(make_finding(
                                path, idx, "HIGH", "COMPLIANCE",
                                RULE_OPENAPI_DTO_CONTRACT,
                                f"@ApiDto 的 description 描述不可為空或佔位符: '{desc_val}'",
                                line.strip(),
                                "無效的描述破壞對外 API 文檔品質",
                                "填入具體的業務領域中文描述",
                            ))
                        break

    # 2. 檢查欄位標註 @ApiField 與命名規範
    # 搜尋所有 private 欄位：private [Type] [fieldName];
    field_pattern = re.compile(
        r"^\s*private\s+(?!static|final|transient)"
        r"([A-Za-z0-9_<>,.? ]+?)\s+([a-zA-Z0-9_]+)\s*(?:=.*?)?;\s*$"
    )

    for idx, raw_line in enumerate(lines, start=1):
        if changed_lines is not None and idx not in changed_lines:
            continue
        line = raw_line.lstrip("+- ")
        field_match = field_pattern.match(line)
        if not field_match:
            continue

        field_type = field_match.group(1).strip()
        field_name = field_match.group(2).strip()

        # 攔截禁制命名黑名單
        if field_name in BANNED_FIELD_NAMES:
            findings.append(make_finding(
                path, idx, "HIGH", "COMPLIANCE",
                RULE_OPENAPI_DTO_CONTRACT,
                f"欄位名稱 '{field_name}' 屬於禁制之模糊或舊有命名",
                raw_line.strip(),
                "模糊命名（如 string, large, small, key 等）破壞通用語言與資料契約清晰度",
                "依據《開發規範.md》重構為語意精確的命名（如 stringValue, greaterThan, lessThan 等）",
            ))

        # 檢查欄位前 4 行內是否具備 @ApiField
        window_start = max(0, idx - 5)
        window_lines = lines[window_start:idx]
        window_text = " ".join(window_lines)

        if "@ApiField" not in window_text:
            findings.append(make_finding(
                path, idx, "HIGH", "COMPLIANCE",
                RULE_OPENAPI_DTO_CONTRACT,
                f"DTO 欄位 '{field_name}' 未標註專案標準 @ApiField 註解",
                raw_line.strip(),
                "DTO 內宣告之欄位必須 100% 標註 @ApiField，以確保 Swagger 契約具備完整欄位說明與範例",
                f"在欄位上方加上 @ApiField(description = \"...\", example = \"...\")",
            ))
        else:
            # 檢查 @ApiField 屬性（抓取最靠近當前欄位的 @ApiField 標註）
            field_annot_matches = list(re.finditer(
                r"@ApiField\s*\(\s*([^)]*)\)", window_text
            ))
            annot_body = field_annot_matches[-1].group(1) if field_annot_matches else ""

            # 檢查 description
            desc_match = re.search(r'(?:description\s*=\s*)?["\']([^"\']*)["\']', annot_body)
            if desc_match:
                desc_str = desc_match.group(1).strip()
                if PLACEHOLDER_REGEX.match(desc_str):
                    findings.append(make_finding(
                        path, idx, "HIGH", "COMPLIANCE",
                        RULE_OPENAPI_DTO_CONTRACT,
                        f"欄位 '{field_name}' 的 @ApiField description 不得為空或佔位符",
                        raw_line.strip(),
                        "無效的欄位描述降低 API 契約可靠性",
                        "填入正確的欄位中文業務說明",
                    ))

            # 檢查 UUID 欄位格式（排除第三方通訊軟體特定實體中的外部字串 ID 如 guildId, channelId, webhookId 等）
            is_platform_external_id = (
                field_name in ("guildId", "channelId", "webhookId", "lineUserId", "discordUserId")
                or (("Discord" in class_name or "Line" in class_name) and field_name == "userId")
            )
            is_uuid_naming = (
                not is_platform_external_id
                and (
                    field_name == "id"
                    or field_name.endswith("Id")
                    or field_name in ("createdBy", "updatedBy")
                    or field_type == "UUID"
                )
            )
            if is_uuid_naming:
                if 'format = "uuid"' not in annot_body and "format = 'uuid'" not in annot_body:
                    findings.append(make_finding(
                        path, idx, "HIGH", "COMPLIANCE",
                        RULE_OPENAPI_DTO_CONTRACT,
                        f"UUID 識別碼欄位 '{field_name}' 之 @ApiField 必須顯式指定 format = \"uuid\"",
                        raw_line.strip(),
                        "未宣告 format = \"uuid\" 將導致前端代碼生成器生成為一般 string 弱型別",
                        "在 @ApiField 中補充 format = \"uuid\" 與合法的 UUID 範例值",
                    ))
                # 檢查 example 是否符合 UUID 格式
                example_match = re.search(r'example\s*=\s*["\']([^"\']+)["\']', annot_body)
                if example_match:
                    example_val = example_match.group(1).strip()
                    if not UUID_REGEX.match(example_val):
                        findings.append(make_finding(
                            path, idx, "HIGH", "COMPLIANCE",
                            RULE_OPENAPI_DTO_CONTRACT,
                            f"UUID 欄位 '{field_name}' 之範例值 '{example_val}' 不符合標準 UUID 格式",
                            raw_line.strip(),
                            "非法 UUID 範例值將導致 API 測試與前端 Mock 產生校驗錯誤",
                            "使用合法 UUID 格式（如 \"00000000-0000-0000-0000-000000000001\"）",
                        ))

    return findings
