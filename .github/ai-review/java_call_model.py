"""有限 Java 呼叫解析；未支援的語法只限制證明能力，不猜測符號歸屬。"""

from dataclasses import dataclass
import re

from check_rules import JPA_BUILTIN_METHODS
from java_code_analyzer import METHOD_HEADER_RE, strip_comments_and_strings

INHERITED_METHODS = JPA_BUILTIN_METHODS | {
    "saveAllAndFlush", "deleteAllByIdInBatch", "findBy", "findOne", "exists",
}


@dataclass(frozen=True)
class JavaCallModel:
    code: str
    package: str
    name: str
    imports: dict[str, str]
    depth: tuple[int, ...]
    complete: bool

    @property
    def qualified_name(self) -> str:
        return f"{self.package}.{self.name}" if self.package else self.name


def parse_java_calls(content: str) -> JavaCallModel:
    code, complete = strip_comments_and_strings(content, allow_text_blocks=True)
    if "\\u" in content:
        complete = False  # Java Unicode 轉義先於詞法處理，有限模型不假設原始字面可直接解析。
    package_match = re.search(r"\bpackage\s+([\w.]+)\s*;", code)
    types = list(re.finditer(r"\b(class|interface|record|enum)\s+(\w+)", code))
    imports: dict[str, str] = {}
    for match in re.finditer(r"\bimport\s+(?!static\b)([\w.]+)\s*;", code):
        qualified = match.group(1)
        simple = qualified.rsplit(".", 1)[-1]
        if simple in imports and imports[simple] != qualified:
            complete = False
        imports[simple] = qualified
    depth: list[int] = []
    level = 0
    for char in code:
        depth.append(level)
        level += (char == "{") - (char == "}")
        if level < 0:
            complete = False
    complete = complete and level == 0 and len(types) == 1
    return JavaCallModel(
        code, package_match.group(1) if package_match else "",
        types[0].group(2) if types else "", imports, tuple(depth), complete,
    )


def split_parameters(text: str) -> list[str] | None:
    """只分割平衡括號／泛型；無法完整辨識時拒絕使用參數個數作證。"""
    pieces: list[str] = []
    stack: list[str] = []
    start = 0
    pairs = {")": "(", "]": "[", "}": "{", ">": "<"}
    for index, char in enumerate(text):
        if char in "([{<":
            stack.append(char)
        elif char in ")]}>":
            if not stack or stack.pop() != pairs[char]:
                return None
        elif char == "," and not stack:
            pieces.append(text[start:index].strip())
            start = index + 1
    if stack:
        return None
    pieces.append(text[start:].strip())
    return pieces if text.strip() else []


def canonical_type(model: JavaCallModel, declaration: str) -> str | None:
    declaration = re.sub(r"@\w+(?:\([^)]*\))?\s*|\bfinal\s+", "", declaration).strip()
    match = re.fullmatch(r"([\w.]+(?:<[^<>]+>)?)\s+\w+", declaration)
    if not match:
        return None
    text = match.group(1)
    if "?" in text or "..." in text:
        return None
    def qualify(item: re.Match) -> str:
        name = item.group(0)
        if name in ("int", "long", "boolean", "double", "float", "byte", "short", "char"):
            return name
        if "." in name:
            return name
        if name in model.imports:
            return model.imports[name]
        if name in ("String", "Integer", "Long", "Boolean", "Object"):
            return "java.lang." + name
        return model.imports.get(name, model.package + "." + name)
    return re.sub(r"[A-Za-z_][\w.]*", qualify, text).replace(" ", "")


def target_signature(model: JavaCallModel, method: str) -> tuple[int, str, tuple[str, ...]] | None:
    """限單一介面中的唯一具名宣告；多載、覆寫及未知繼承契約不作唯一解析。"""
    if (not model.complete or method in INHERITED_METHODS
            or "@Override" in model.code
            or re.search(r"\bimport\s+[\w.]+\.\*|\binterface\s+\w+\s*<", model.code)
            or not re.search(r"\binterface\s+" + model.name + r"\b", model.code)):
        return None
    parents = re.search(r"\binterface\s+\w+[^\{]*\bextends\s+([^\{]+)", model.code)
    if parents:
        expected = {"JpaRepository": "org.springframework.data.jpa.repository.JpaRepository",
                    "CrudRepository": "org.springframework.data.repository.CrudRepository",
                    "JpaSpecificationExecutor":
                        "org.springframework.data.jpa.repository.JpaSpecificationExecutor",
                    "PagingAndSortingRepository":
                        "org.springframework.data.repository.PagingAndSortingRepository"}
        parent_types = split_parameters(parents.group(1))
        if parent_types is None:
            return None
        for item in parent_types:
            match = re.fullmatch(r"\s*(\w+)\s*<[^<>]+>\s*", item)
            if (not match or match.group(1) not in expected
                    or model.imports.get(match.group(1)) != expected[match.group(1)]):
                return None
    code = re.sub(r"@\w+(?:\([^)]*\))?", lambda item: " " * len(item.group(0)), model.code)
    candidates = list(re.finditer(rf"\b{re.escape(method)}\s*\(([^()]*)\)\s*;", code))
    if len(re.findall(rf"\b{re.escape(method)}\s*\(", code)) != 1:
        return None
    if len(candidates) != 1:
        return None
    match = candidates[0]
    previous_boundary = max(code.rfind(";", 0, match.start()), code.rfind("{", 0, match.start()))
    if re.search(r"(?:^|\s)<\w+(?:\s+extends\s+[^>]+)?>", code[previous_boundary:match.start()]):
        return None
    if model.depth[match.start()] != 1:
        return None
    params = split_parameters(match.group(1))
    if params is None or any("..." in param for param in params):
        return None
    types = [canonical_type(model, param) for param in params]
    if any(item is None for item in types):
        return None
    return len(params), match.group(0), tuple(item for item in types if item is not None)


def arguments_match(
    model: JavaCallModel, offset: int, args: list[str], expected: tuple[str, ...],
) -> bool:
    """限方法參數或直接區塊明確宣告型別；不推導初始化運算式的型別。"""
    headers = [item for item in METHOD_HEADER_RE.finditer(model.code)
               if item.end() <= offset and model.depth[item.start()] == 1]
    if not headers:
        return False
    header = headers[-1]
    if re.search(r"\b(?:public|protected|private|default)\s+"
                 r"(?:(?:static|final|synchronized|abstract)\s+)*<", header.group(0)):
        return False
    if any(model.depth[index] == 2 and model.code[index] == "}"
           for index in range(header.end(), offset)):
        return False
    parameters = split_parameters(header.group(3))
    if parameters is None:
        return False
    by_name = {param.split()[-1]: canonical_type(model, param) for param in parameters}
    for arg, required in zip(args, expected):
        if not re.fullmatch(r"\w+", arg):
            return False
        if arg not in by_name:
            if not local_argument_matches(model, header.end(), offset, arg, required):
                return False
            continue
        if not argument_type_matches(by_name[arg], required):
            return False
        # 引數名稱在方法內重宣告、賦值或 lambda 捕獲時，有限資料流不作保證。
        prefix = model.code[header.end():offset]
        if re.search(rf"\b{arg}\s*=(?!=)|\b\w+\s+{arg}\b|\b{arg}\s*->", prefix):
            return False
    return True


def argument_type_matches(actual: str | None, required: str) -> bool:
    """僅納入 JDK 明確的 List 到 Collection 契約；泛型實參必須完全相同。"""
    if actual == required:
        return True
    if actual is None:
        return False
    match = re.fullmatch(r"java\.util\.List<([^<>?]+)>", actual)
    return bool(match and required == f"java.util.Collection<{match.group(1)}>")


def local_argument_matches(
    model: JavaCallModel, body_start: int, offset: int, name: str, required: str,
) -> bool:
    """只接受方法直接區塊已初始化的顯式型別，不猜 var／分支賦值／閉包作用域。"""
    prefix = model.code[body_start:offset]
    if re.search(r"\b(?:try|catch|finally|class|record)\b|->", prefix):
        return False
    declarations = list(re.finditer(
        rf"(?<![\w.])(?:final\s+)?([\w.]+(?:<[^<>;{{}}]+>)?)\s+{name}\s*=", prefix,
    ))
    if len(declarations) != 1:
        return False
    declaration = declarations[0]
    absolute = body_start + declaration.start()
    if model.depth[absolute] != 2:
        return False
    boundary = prefix[:declaration.start()].rstrip()
    if boundary and boundary[-1] not in ";{}":
        return False
    if not argument_type_matches(canonical_type(model, f"{declaration.group(1)} {name}"),
                                 required):
        return False
    end = prefix.find(";", declaration.end())
    if end < 0 or not prefix[declaration.end():end].strip():
        return False
    initializer = prefix[declaration.end():end]
    # 自我引用、運算子或區塊使確定初始化關係不明確；不追蹤複雜資料流。
    if re.search(rf"\b{name}\b|[{{}}]", initializer):
        return False
    remainder = prefix[end + 1:]
    if re.search(rf"\b{name}\s*(?:[+*/%&|^\-]?=(?!=)|\+\+|--)|\b\w+\s+{name}\b",
                 remainder):
        return False
    return True


def resolved_calls(
    model: JavaCallModel, target: JavaCallModel, method: str, types: tuple[str, ...],
) -> list[tuple[int, str]]:
    """只接受無遮蔽的明確欄位接收者，不把同名方法／多載參數個數當型別解析。"""
    if not model.complete or not target.complete:
        return []
    # 子類別、匿名類別及萬用匯入會使此有限模型無法排除名稱遮蔽。
    if re.search(r"\bextends\b|\bimport\s+[\w.]+\.\*|\bnew\b[^;{}]*\{"
                 r"|\b(?:class|interface|record)\s+\w+\s*<", model.code):
        return []
    fields = list(re.finditer(
        r"\b(?:private|protected|public)\s+(?:final\s+)?(\w+)\s+(\w+)\s*;", model.code,
    ))
    found: list[tuple[int, str]] = []
    for field in fields:
        simple, receiver = field.groups()
        qualified = model.imports.get(simple, f"{model.package}.{simple}")
        if qualified != target.qualified_name or model.depth[field.start()] != 1:
            continue
        # 任一同名參數／局部變數／賦值皆拒絕，避免把別的接收者誤當欄位。
        remainder = model.code[:field.start()] + " " * len(field.group(0))
        remainder += model.code[field.end():]
        declarations = re.findall(rf"\b([\w<>\[\].?]+)\s+{receiver}\b", remainder)
        non_types = {"return", "throw", "yield", "case", "new", "else"}
        if (any(item not in non_types for item in declarations)
                or re.search(rf"\b{receiver}\s*=(?!=)", remainder)):
            continue
        pattern = re.compile(rf"(?<![\w.])(?:this\s*\.\s*)?{receiver}\s*\.\s*{method}\s*\(")
        for call in pattern.finditer(model.code):
            start, level, end = call.end(), 1, call.end()
            while end < len(model.code) and level:
                level += (model.code[end] == "(") - (model.code[end] == ")")
                end += 1
            args = split_parameters(model.code[start:end - 1]) if level == 0 else None
            if args is None or len(args) != len(types):
                continue
            if not arguments_match(model, call.start(), args, types):
                continue
            line = model.code.count("\n", 0, call.start()) + 1
            found.append((line, model.code[call.start():end].strip()))
    return found
