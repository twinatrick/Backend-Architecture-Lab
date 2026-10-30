import sys
from pathlib import Path

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

from java_call_model import parse_java_calls, resolved_calls, target_signature
from java_code_analyzer import strip_comments_and_strings

TARGET = '''package sample;
import java.util.List;
public interface Repository {
    @Query("""
        select bogus { repository.removeCustom(fake); } // not Java
        """)
    void removeCustom(List<String> ids);
}
'''
CALLER = '''package sample;
import java.util.List;
public class Caller {
    private final Repository repository;
    public void remove(List<String> input) {
        List<String> ids = input.stream().toList();
        repository.removeCustom(ids);
    }
}
'''


def calls(source: str) -> list[tuple[int, str]]:
    target = parse_java_calls(TARGET)
    signature = target_signature(target, "removeCustom")
    assert signature is not None
    return resolved_calls(parse_java_calls(source), target, "removeCustom", signature[2])


def test_text_block_preserves_offsets_and_cannot_inject_calls_or_braces():
    model = parse_java_calls(TARGET)
    assert model.complete
    assert len(model.code) == len(TARGET)
    assert [index for index, char in enumerate(model.code) if char == "\n"] == [
        index for index, char in enumerate(TARGET) if char == "\n"
    ]
    assert "bogus" not in model.code
    assert model.code.count("removeCustom") == 1
    assert calls(CALLER) == [(7, "repository.removeCustom(ids)")]


@pytest.mark.parametrize("block", [
    '"""not a legal opener"""', '"""\nunterminated',
    '"""\n' + '\\"""' + ' still unclosed',
])
def test_invalid_or_unterminated_text_block_never_proves_symbols(block):
    assert not parse_java_calls("interface Repo { @Query(" + block + ") void go(); }").complete


def test_escaped_delimiter_comments_and_crlf_are_lexically_isolated():
    source = ('interface Repo { // """ in comment\r\n @Query("""\r\n'
              + '\\"""' + ' { fake(); }\r\n""") void go(); }')
    model = parse_java_calls(source)
    assert model.complete
    assert len(model.code) == len(source)
    assert "fake" not in model.code
    assert target_signature(model, "go") is not None
    # 未宣告支援 text block 的其他規則仍維持原有能力界線。
    assert not strip_comments_and_strings(source)[1]
    assert not strip_comments_and_strings(source, True, allow_text_blocks=True)[1]


@pytest.mark.parametrize("declaration", [
    "List<String> ids = input.stream().toList();",
    "final List<String> ids = input;",
])
def test_explicit_initialized_direct_local_type_is_a_valid_signature_witness(declaration):
    assert calls(CALLER.replace("List<String> ids = input.stream().toList();", declaration))


@pytest.mark.parametrize("replacement", [
    "var ids = input;", "List<Integer> ids = convert(input);",
    "List<String> ids; if (input != null) { ids = input; }",
    "if (input != null) { List<String> ids = input; }",
    "List<String> ids = ids;", "List<String> ids = input; ids = other;",
    "List<String> ids = input; Runnable task = () -> use(ids);",
    "try { use(input); } finally { use(input); } List<String> ids = input;",
    "List<String> ids = input; { List<String> ids = other; }",
    "List<String> ids = input; ids += other;",
])
def test_ambiguous_local_dataflow_never_proves_a_call(replacement):
    assert not calls(CALLER.replace("List<String> ids = input.stream().toList();", replacement))


def test_text_block_fake_local_declaration_cannot_supply_argument_type():
    caller = CALLER.replace("List<String> ids = input.stream().toList();",
                            'String sql = """\nList<String> ids = input;\n""";')
    assert not calls(caller)


@pytest.mark.parametrize("caller", [
    CALLER.replace("class Caller", "class Caller<String>"),
    CALLER.replace("public void remove", "public <String> void remove"),
    CALLER.replace("import java.util.List;", "import java.util.List; import custom.String;"),
])
def test_generic_or_imported_type_shadowing_is_not_java_lang_string(caller):
    assert not calls(caller)


@pytest.mark.parametrize("local_type,expected", [("String", True), ("Integer", False)])
def test_jdk_list_to_collection_keeps_generic_arguments_invariant(local_type, expected):
    target = parse_java_calls(TARGET.replace("java.util.List", "java.util.Collection")
                              .replace("List<String> ids", "Collection<String> ids"))
    signature = target_signature(target, "removeCustom")
    assert signature is not None
    caller = parse_java_calls(CALLER.replace("List<String> ids", f"List<{local_type}> ids"))
    assert bool(resolved_calls(caller, target, "removeCustom", signature[2])) is expected
