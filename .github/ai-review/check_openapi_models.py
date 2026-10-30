"""公開 Controller API 模型依賴圖掃描器。

動態解析對外 Controller 端點之輸入與輸出契約模型，並遞迴展開巢狀欄位型別，
建立公開 API 契約模型白名單，徹底避免非 Controller 物件（如內部 Event、Snapshot 等）被誤判。
"""

import re
from pathlib import Path

from java_code_analyzer import strip_comments_and_strings

BUILTIN_FRAMEWORK_TYPES = frozenset({
    "String", "Integer", "int", "Long", "long", "Double", "double", "Float", "float",
    "Boolean", "boolean", "Byte", "byte", "Short", "short", "Character", "char",
    "Void", "void", "Object", "UUID", "Date", "LocalDate", "LocalDateTime", "LocalTime",
    "Instant", "BigDecimal", "BigInteger", "Map", "HashMap", "List", "ArrayList", "Set",
    "HashSet", "Collection", "PageResult", "ResponseType", "ResponseEntity", "MultipartFile",
    "HttpServletRequest", "HttpServletResponse", "Principal", "Authentication", "Pageable",
    "Sort", "byte[]", "T", "ID", "Flux", "Mono", "ServerSentEvent", "ServerWebExchange",
    "Session", "Model", "RedirectAttributes", "BindingResult", "HttpStatus",
    "ApiBody", "ApiPath", "ApiQuery", "ApiControllerTag", "ApiOperationOk",
    "ApiOperationCreated", "ApiOperationNoContent", "ApiOperationBadRequest",
    "ApiOperationAuth", "ApiOperationForbidden", "ApiOperationNotFound",
    "ApiOperationError", "RequirePermission", "Valid", "Validated", "RequestBody",
    "PathVariable", "RequestParam", "RequestHeader", "RestController", "RequestMapping"
})

_PUBLIC_API_MODELS_CACHE: dict[Path, set[str]] | None = None

ENDPOINT_ANNOTATIONS = (
    "@GetMapping", "@PostMapping", "@PutMapping",
    "@DeleteMapping", "@PatchMapping", "@RequestMapping"
)

MODEL_DIR_KEYWORDS = ("/Vo/", "/vo/", "/Dto/", "/dto/")


def is_internal_controller(path_str: str, content: str = "") -> bool:
    """判斷是否為微服務間內部 Feign Controller。"""
    normalized = path_str.replace("\\", "/").lower()
    if any(keyword in normalized for keyword in ("/inner", "/internal", "internalcontroller")):
        return True
    if content and re.search(
        r'@RequestMapping\s*\(\s*(?:(?:value|path)\s*=\s*)?["\'][^"\']*/inner', content
    ):
        return True
    return False


def scan_class_paths(repo_root: Path) -> dict[str, Path]:
    """掃描專案內所有 Java 類別路徑映射。"""
    mapping: dict[str, Path] = {}
    for java_path in repo_root.glob("**/src/main/java/**/*.java"):
        class_name = java_path.stem
        mapping[class_name] = java_path
    return mapping


def find_public_controllers(repo_root: Path) -> list[tuple[Path, str]]:
    """尋找所有對外公開 Controller 及其內容。"""
    controllers: list[tuple[Path, str]] = []
    for controller_path in repo_root.glob("**/src/main/java/**/Controller/**/*.java"):
        if is_internal_controller(str(controller_path)):
            continue
        try:
            content = controller_path.read_text(encoding="utf-8", errors="strict")
        except OSError:
            continue
        annotation_code, complete = strip_comments_and_strings(content, preserve_literals=True)
        if complete and is_internal_controller(str(controller_path), annotation_code):
            continue
        controllers.append((controller_path, content))
    return controllers


def extract_seed_models(
    controllers: list[tuple[Path, str]],
    class_to_path: dict[str, Path]
) -> set[str]:
    """自公開 Controller 端點提取直接引用的輸入與輸出種子模型。"""
    seed_models: set[str] = set()
    for _, content in controllers:
        stripped, complete = strip_comments_and_strings(content)
        if complete:
            content = stripped
        lines = content.splitlines()
        for i, line in enumerate(lines):
            trimmed = line.strip()
            if any(ann in trimmed for ann in ENDPOINT_ANNOTATIONS):
                window_lines: list[str] = []
                paren_depth = 0
                for line_idx in range(i, min(i + 12, len(lines))):
                    current_line = lines[line_idx].strip()
                    window_lines.append(current_line)
                    paren_depth += current_line.count("(") - current_line.count(")")
                    if paren_depth == 0 and ("{" in current_line or ";" in current_line):
                        break
                method_text = " ".join(window_lines)
                tokens = re.findall(r"\b[A-Z][A-Za-z0-9_]+\b", method_text)
                for token in tokens:
                    if token in class_to_path and token not in BUILTIN_FRAMEWORK_TYPES:
                        token_path = str(class_to_path[token]).replace("\\", "/")
                        if any(keyword in token_path for keyword in MODEL_DIR_KEYWORDS):
                            seed_models.add(token)
    return seed_models


def expand_nested_models(
    seed_models: set[str],
    class_to_path: dict[str, Path]
) -> set[str]:
    """遞迴展開種子模型引用的所有自訂巢狀欄位物件。"""
    public_models: set[str] = set(seed_models)
    queue: list[str] = list(seed_models)

    while queue:
        current_name = queue.pop(0)
        model_path = class_to_path.get(current_name)
        if not model_path or not model_path.exists():
            continue
        try:
            code = model_path.read_text(encoding="utf-8", errors="strict")
        except OSError:
            continue
        stripped, complete = strip_comments_and_strings(code)
        if complete:
            code = stripped
        tokens = re.findall(r"\b[A-Z][A-Za-z0-9_]+\b", code)
        for token in tokens:
            if token in class_to_path and token not in BUILTIN_FRAMEWORK_TYPES:
                if token not in public_models:
                    token_path = str(class_to_path[token]).replace("\\", "/")
                    if any(keyword in token_path for keyword in MODEL_DIR_KEYWORDS):
                        public_models.add(token)
                        queue.append(token)
    return public_models


def get_public_api_model_set(repo_root: Path | None = None) -> set[str]:
    """取得對外暴露於公開 Controller 之 API 契約模型白名單集合（含巢狀欄位遞迴解析）。"""
    global _PUBLIC_API_MODELS_CACHE
    if repo_root is None:
        repo_root = Path(__file__).resolve().parent.parent.parent
    repo_root = repo_root.resolve()
    if _PUBLIC_API_MODELS_CACHE is None:
        _PUBLIC_API_MODELS_CACHE = {}
    if repo_root in _PUBLIC_API_MODELS_CACHE:
        return set(_PUBLIC_API_MODELS_CACHE[repo_root])

    class_to_path = scan_class_paths(repo_root)
    controllers = find_public_controllers(repo_root)
    seed_models = extract_seed_models(controllers, class_to_path)
    _PUBLIC_API_MODELS_CACHE[repo_root] = expand_nested_models(seed_models, class_to_path)
    return set(_PUBLIC_API_MODELS_CACHE[repo_root])
