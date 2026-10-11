import os
import re
from typing import Any

from check_dual_rail import (
    check_forbidden_getall_endpoints,
    check_missing_dual_rail_endpoints,
    check_unimplemented_stubs,
)
from check_java_quality import (
    check_cross_module,
    check_derived_delete_transaction,
    check_iam_reverse_dependency,
    check_repository_dead_code,
    check_self_feign_block,
    check_weak_typed_map,
    detect_module,
)
from check_rules import (
    BANNED_PERMISSIONS,
    RULE_CONSTRUCTOR_INJECTION,
    RULE_CONTROLLER_DATA_ISOLATION,
    RULE_CONTROLLER_DEPENDENCY,
    RULE_DEPENDENCY_INJECTION,
    RULE_ENTITY_USAGE,
    RULE_OPENAPI_ANNOTATION,
    RULE_PERMISSION_DICTIONARY,
    RULE_PROHIBIT_FQN,
    RULE_SERVICE_ENTITY_MANAGER,
    make_finding,
)


def check_java_file(
    path: str,
    content: str,
    changed_lines: set[int] | None = None,
    repo_searcher: Any | None = None,
) -> list[dict[str, Any]]:
    """針對 Java Controller、Service 與架構分層進行確定性規範檢查。"""
    findings: list[dict[str, Any]] = []
    filename = os.path.basename(path)
    lines = content.splitlines()
    is_test = "src/test/java/" in path.replace("\\", "/")
    is_controller = "Controller" in filename or "@RestController" in content
    is_service_impl = "ServiceImpl" in filename or "/Service/Impl/" in path.replace("\\", "/")
    is_data_access_impl = (
        "DataAccessImpl" in filename or "/DataAccess/Impl/" in path.replace("\\", "/")
    )
    is_service_interface = (
        ("Service" in filename or "/Service/" in path.replace("\\", "/"))
        and "interface " in content
        and not is_test
    )
    has_class_transactional = bool(re.search(r"@Transactional\b[\s\S]*?\bclass\s+", content))
    current_module = detect_module(path)
    class_match = re.search(r"\bclass\s+([A-Z]\w+)\b", content)
    class_name = class_match.group(1) if class_match else ""

    # 檢查自我 Feign（支援跨多行註解）
    findings.extend(check_self_feign_block(path, content, current_module, changed_lines))

    # Repository 死代碼逆向引用檢索
    findings.extend(
        check_repository_dead_code(
            path, content, changed_lines=changed_lines, repo_searcher=repo_searcher
        )
    )

    # 未實作存根 (Stub) 檢測
    findings.extend(check_unimplemented_stubs(path, lines, changed_lines, is_test))

    # 實體 Controller 雙軌獲取標準與禁制 getAll 端點檢測
    if is_controller and not is_test:
        findings.extend(
            check_forbidden_getall_endpoints(
                path, content, lines, changed_lines, is_test
            )
        )
        findings.extend(
            check_missing_dual_rail_endpoints(
                path, content, changed_lines=changed_lines, is_test=is_test
            )
        )

    for idx, raw_line in enumerate(lines, start=1):
        if changed_lines is not None and idx not in changed_lines:
            continue
        line = raw_line.lstrip("+- ")

        # 1. 跨模組資料隔離與依賴檢查
        cross_res = check_cross_module(path, line, idx, current_module)
        if cross_res:
            findings.append(cross_res)

        # 2. IAM 單向依賴檢查
        iam_res = check_iam_reverse_dependency(path, line, idx, current_module)
        if iam_res:
            findings.append(iam_res)

        # 3. 權限字串檢查
        for banned in BANNED_PERMISSIONS:
            if banned in line and ("@RequirePermission" in line or "hasAuthority" in line):
                findings.append(make_finding(
                    path, idx, "HIGH", "SECURITY",
                    RULE_PERMISSION_DICTIONARY,
                    f"使用了已被廢棄或禁用的權限字串: {banned}",
                    raw_line.strip(),
                    "使用非標準權限名稱將導致 RBAC 權限失效或鑑權失敗",
                    "依據《開發規範.md》§2 權限字典替換為標準權限命名",
                ))

        # 4. 生產環境 @Autowired 與手寫建構子檢查
        if not is_test:
            if re.search(r"@Autowired\b", line) and not re.search(
                r"@Autowired\s*\(\s*required\s*=\s*false\s*\)", line
            ):
                findings.append(make_finding(
                    path, idx, "HIGH", "COMPLIANCE",
                    RULE_DEPENDENCY_INJECTION,
                    "生產程式碼中嚴禁使用 @Autowired 進行欄位注入",
                    raw_line.strip(),
                    "欄位注入隱藏依賴且不利於單元測試",
                    "採用 Lombok @RequiredArgsConstructor 搭配 private final",
                ))

            # 檢查手寫多載建構子 (this(...)) 或 Controller/ServiceImpl 手寫建構子
            exemptions = (
                "Entity", "Vo", "Exception", "Wrapper", "DTO", "Dto",
                "Config", "Configuration", "Filter",
            )
            if not any(ex in filename for ex in exemptions):
                if re.search(r"^\s*this\s*\(", line):
                    findings.append(make_finding(
                        path, idx, "HIGH", "COMPLIANCE",
                        RULE_CONSTRUCTOR_INJECTION,
                        "類別內部手寫多載建構子 (this(...))，違反專案依賴注入規範",
                        raw_line.strip(),
                        "手寫多載建構子破壞依賴注入單一來源原則並增加維護複雜度",
                        "統一採用 Lombok @RequiredArgsConstructor，多載組裝移至 @Configuration",
                    ))
                elif (
                    class_name
                    and (is_controller or is_service_impl)
                    and re.search(rf"^\s*(?:public|protected)\s+{class_name}\s*\(", line)
                ):
                    target_role = "Controller" if is_controller else "ServiceImpl"
                    findings.append(make_finding(
                        path, idx, "HIGH", "COMPLIANCE",
                        RULE_CONSTRUCTOR_INJECTION,
                        f"{target_role} 嚴禁手寫建構子進行注入",
                        raw_line.strip(),
                        "違反專案全建構子注入規範",
                        "移除手寫建構子，統一標註 @RequiredArgsConstructor 搭配 private final",
                    ))

        # 5. Controller 分層與 OpenAPI 規範檢查
        if is_controller:
            if line.strip().startswith("import io.swagger.v3.oas.annotations"):
                findings.append(make_finding(
                    path, idx, "HIGH", "COMPLIANCE",
                    RULE_OPENAPI_ANNOTATION,
                    "Controller 嚴禁直接引用原生 io.swagger.v3.oas.annotations 套件",
                    raw_line.strip(),
                    "引入原生 Swagger 註解破壞專案統一之 API 規格與錯誤碼封裝",
                    "改用專案封裝之 OpenApi 註解 (@ApiControllerTag, @ApiOperationOk)",
                ))
            elif re.search(r"@(Tag|Operation|ApiResponse|Parameters|Parameter)\b", line):
                findings.append(make_finding(
                    path, idx, "MEDIUM", "COMPLIANCE",
                    RULE_OPENAPI_ANNOTATION,
                    "Controller 應使用專案封裝之 OpenApi 註解取代原生 Swagger 註解",
                    raw_line.strip(),
                    "缺少統一的 API 響應結構與錯誤碼說明",
                    "使用 @ApiControllerTag / @ApiOperationOk 等專案標準註解封裝",
                ))
            if re.search(r"private\s+final\s+.*EntityManager\b", line):
                findings.append(make_finding(
                    path, idx, "HIGH", "ARCHITECTURE",
                    RULE_CONTROLLER_DATA_ISOLATION,
                    "Controller 嚴禁直接注入 EntityManager",
                    raw_line.strip(),
                    "破壞 Controller/Service/DataAccess 分層架構",
                    "移除 EntityManager，僅透過 Service 介面操作",
                ))
            if re.search(r"private\s+final\s+.*(?:Repository|Mapper)\b", line):
                findings.append(make_finding(
                    path, idx, "HIGH", "ARCHITECTURE",
                    RULE_CONTROLLER_DEPENDENCY,
                    "Controller 嚴禁直接注入 Repository 或 Mapper",
                    raw_line.strip(),
                    "違反 MVC 分層職責與資料隔離",
                    "Controller 僅可注入 Service 介面，透過 Vo 進行互動",
                ))
            if re.search(r"public\s+(?:ResponseEntity<)?\w+Entity(?:>)?\s+\w+\(", line):
                findings.append(make_finding(
                    path, idx, "HIGH", "ARCHITECTURE",
                    RULE_ENTITY_USAGE,
                    "Controller 方法回傳型別包含 Entity",
                    raw_line.strip(),
                    "Entity 洩漏至 API 對外介面，破壞封裝",
                    "將回傳型別轉換為 Vo",
                ))

        # 6. Service Impl EntityManager 操作檢查
        if is_service_impl and re.search(r"private\s+final\s+.*EntityManager\b", line):
            findings.append(make_finding(
                path, idx, "HIGH", "ARCHITECTURE",
                RULE_SERVICE_ENTITY_MANAGER,
                "Service Impl 禁止直接注入 EntityManager",
                raw_line.strip(),
                "違反資料存取抽象化規範",
                "將資料庫操作封裝至 DataAccess 或 Repository",
            ))

        # 7. 禁寫完全限定名稱 (FQN) 檢查 (§5.12)
        trimmed_code = line.strip()
        is_import_or_pkg = trimmed_code.startswith(
            ("import ", "package ", "//", "*", "/*", "@Mapping")
        )
        if not is_import_or_pkg:
            clean_code = re.sub(r"//.*$", "", line)
            clean_code = re.sub(r'"[^"]*"', '""', clean_code)
            fqn_pattern = (
                r"\b(?:java|jakarta|javax|org|com|reactor|io|net)\."
                r"[a-z0-9_.]+\.[A-Z]\w+\b"
            )
            fqn_match = re.search(fqn_pattern, clean_code)
            if fqn_match:
                findings.append(make_finding(
                    path, idx, "HIGH", "COMPLIANCE",
                    RULE_PROHIBIT_FQN,
                    f"程式碼中禁止直接使用完全限定名稱 (FQN): {fqn_match.group(0)}",
                    raw_line.strip(),
                    "違反《開發規範.md》§5.12，降低程式碼可讀性與一致性",
                    "檔案頂部顯式宣告 import，程式碼中僅使用簡潔類別名稱",
                ))

        # 8. 衍生刪除事務邊界檢查 (deleteBy / removeBy)
        if not is_test and (is_data_access_impl or is_service_impl):
            del_finding = check_derived_delete_transaction(
                path, raw_line, line, idx, lines, has_class_transactional
            )
            if del_finding:
                findings.append(del_finding)

        # 9. 弱型別集合回傳檢查 (Map<Object, Object>, Map<?, ?>, Map<String, Object>)
        if not is_test and (is_controller or is_service_impl or is_service_interface):
            map_finding = check_weak_typed_map(path, raw_line, line, idx)
            if map_finding:
                findings.append(map_finding)

    return findings
