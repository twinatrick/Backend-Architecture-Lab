import sys
import time
from pathlib import Path
from unittest.mock import MagicMock

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

from java_call_model import parse_java_calls, resolved_calls, target_signature
from repository_call_search import SnapshotRepositorySearch
from review_snapshot import ReviewSnapshot
from source_budget import SourceReadBudget

REPO_PATH = "module/src/main/java/sample/Repository.java"
CALLER_PATH = "module/src/main/java/sample/RepositoryCaller.java"
TARGET = "package sample; public interface Repository { void removeCustom(String roleId); }"
CALLER = """package sample;
public class RepositoryCaller {
    private final Repository repository;
    public void remove(String roleId) {
        repository.removeCustom(roleId);
    }
}
"""


def make_search(sources: dict[str, str], **budget_args) -> SnapshotRepositorySearch:
    getter = MagicMock(return_value={
        "truncated": False, "tree": [{"type": "blob", "path": path} for path in sources],
    })
    snapshot = ReviewSnapshot(
        [{"filename": path, "full_content": code} for path, code in sources.items()],
        repo_name="owner/repo", head_sha="a" * 40,
        source_budget=SourceReadBudget(**budget_args),
    )
    return SnapshotRepositorySearch(snapshot, getter, time.monotonic() + 30)


def test_exact_typed_production_call_ends_claim_without_scanning_other_files():
    search = make_search({REPO_PATH: TARGET, CALLER_PATH: CALLER,
                          "other/src/main/java/Unsupported.java": '"""unsupported'})
    assert search("removeCustom", REPO_PATH) == [CALLER_PATH]
    assert not search.incomplete
    assert search.outcomes[(REPO_PATH, "removeCustom")]["status"] == "COMPLIANT"
    assert "Unsupported" not in str(search.snapshot.source_budget.contents)
    before = search.snapshot.extra_tokens_used
    assert search("removeCustom", REPO_PATH) == [CALLER_PATH]
    assert search.snapshot.extra_tokens_used == before


@pytest.mark.parametrize("caller", [
    CALLER.replace("Repository repository", "OtherRepository repository"),
    CALLER.replace("String roleId)", "Integer roleId)"),
    CALLER.replace("repository.removeCustom(roleId)", "repository.removeCustom(null)"),
    CALLER.replace("String roleId)", "String roleId, Repository repository)"),
    CALLER.replace("public class RepositoryCaller", "public class RepositoryCaller extends Base"),
    CALLER.replace("repository.removeCustom(roleId)", "repository.removeCustom(roleId, roleId)"),
    CALLER.replace("package sample;", "package other;"),
    CALLER.replace("repository.removeCustom(roleId)", "repository::removeCustom"),
    CALLER.replace("repository.removeCustom(roleId)", "/* repository.removeCustom(roleId) */"),
])
def test_ambiguous_or_wrong_receiver_and_argument_never_count_as_proven_call(caller):
    search = make_search({REPO_PATH: TARGET, CALLER_PATH: caller})
    assert search("removeCustom", REPO_PATH) == []
    assert search.outcomes[(REPO_PATH, "removeCustom")]["status"] != "COMPLIANT"


def test_unique_import_resolves_full_type_but_same_simple_name_does_not():
    caller = CALLER.replace("package sample;", "package app; import sample.Repository;")
    search = make_search({REPO_PATH: TARGET, CALLER_PATH: caller})
    assert search("removeCustom", REPO_PATH) == [CALLER_PATH]
    duplicate = "other/src/main/java/sample/Repository.java"
    search = make_search({REPO_PATH: TARGET, CALLER_PATH: caller, duplicate: TARGET})
    assert search("removeCustom", REPO_PATH) == []
    assert search.incomplete


@pytest.mark.parametrize("extra", [
    "void removeCustom(Integer roleId);", "void removeCustom(String... roleIds);",
    "default void removeCustom(Integer roleId) {}",
])
def test_overloads_never_resolve_by_arity_alone(extra):
    target = TARGET.replace("String roleId);", "String roleId); " + extra)
    assert target_signature(parse_java_calls(target), "removeCustom") is None


def test_test_only_caller_and_unknown_source_set_do_not_clear_production_claim():
    search = make_search({REPO_PATH: TARGET,
                          "module/src/test/java/sample/RepositoryCaller.java": CALLER})
    assert search("removeCustom", REPO_PATH) == []
    assert search.outcomes[(REPO_PATH, "removeCustom")]["status"] == "VIOLATION"
    search = make_search({REPO_PATH: TARGET, "custom/RepositoryCaller.java": CALLER})
    assert search("removeCustom", REPO_PATH) == []
    assert search.incomplete


@pytest.mark.parametrize("target", [
    TARGET.replace("interface Repository", "interface Repository extends External"),
    TARGET.replace("void removeCustom", "@Override void removeCustom"),
    TARGET.replace("removeCustom", "deleteById"),
    TARGET.replace("interface Repository", "interface Repository extends JpaRepository<X,Y>"),
])
def test_framework_or_unknown_contract_is_not_negative_proof(target):
    search = make_search({REPO_PATH: target})
    method = "deleteById" if "deleteById" in target else "removeCustom"
    assert search(method, REPO_PATH) == []
    assert search.incomplete


def test_annotations_and_exact_generic_argument_types():
    target = parse_java_calls(
        "package sample; import java.util.Collection; import java.util.UUID; "
        "interface Repository { void removeCustom(@Param(\"ids\") Collection<UUID> ids); }"
    )
    caller = parse_java_calls(CALLER.replace(
        "package sample;", "package sample; import java.util.Collection; import java.util.UUID;",
    ).replace("String roleId", "Collection<UUID> roleId"))
    signature = target_signature(target, "removeCustom")
    assert signature is not None
    assert resolved_calls(caller, target, "removeCustom", signature[2])


def test_returning_repository_does_not_shadow_injected_field():
    caller = CALLER.replace(
        "public void remove", "public Repository getRepository() { return repository; }\n"
        "    public void remove",
    )
    search = make_search({REPO_PATH: TARGET, CALLER_PATH: caller})
    assert search("removeCustom", REPO_PATH) == [CALLER_PATH]


def test_multiple_known_framework_parents_resolve_custom_method_but_not_framework_methods():
    target = TARGET.replace(
        "package sample;", "package sample; "
        "import org.springframework.data.jpa.repository.JpaRepository; "
        "import org.springframework.data.jpa.repository.JpaSpecificationExecutor;",
    ).replace("interface Repository", "interface Repository extends "
              "JpaRepository<Entity, String>, JpaSpecificationExecutor<Entity>")
    search = make_search({REPO_PATH: target, CALLER_PATH: CALLER})
    assert search("removeCustom", REPO_PATH) == [CALLER_PATH]
    assert target_signature(parse_java_calls(target.replace("removeCustom", "findOne")),
                            "findOne") is None


def test_unicode_escape_and_text_block_are_not_treated_as_complete_symbols():
    escaped = CALLER.replace("repository.removeCustom", r"\u0072.removeCustom")
    assert not parse_java_calls(escaped).complete
    text_block = 'interface Repo { @Query("""\nquery\n""") void remove(); }'
    assert not parse_java_calls(text_block).complete


@pytest.mark.parametrize("path", [
    "module/src/main/kotlin/Caller.kt", "module/src/main/groovy/Caller.groovy",
])
def test_unknown_production_language_prevents_negative_proof_but_not_positive(path):
    search = make_search({REPO_PATH: TARGET, path: "unsupported"})
    assert search("removeCustom", REPO_PATH) == []
    assert search.incomplete
    search = make_search({REPO_PATH: TARGET, CALLER_PATH: CALLER, path: "unsupported"})
    assert search("removeCustom", REPO_PATH) == [CALLER_PATH]


def test_read_budget_is_shared_and_not_charged_to_model_context():
    source = "class Large {\n" + " // padding\n" * 4000 + "}"
    budget = SourceReadBudget()
    fetcher = MagicMock(return_value=source)
    first = ReviewSnapshot([], repo_name="repo", head_sha="head", content_fetcher=fetcher,
                           source_budget=budget)
    second = ReviewSnapshot([], repo_name="repo", head_sha="head", content_fetcher=fetcher,
                            source_budget=budget)
    assert len(first.get_file_content("Large.java").encode()) > 32000
    assert second.get_file_content("Large.java") == source
    fetcher.assert_called_once()
    assert budget.bytes_used == len(source.encode())


def test_size_deadline_and_total_limits_preserve_cached_evidence():
    budget = SourceReadBudget(max_file_bytes=4, max_total_bytes=4)
    loader = MagicMock(return_value="abcd")
    assert budget.read("repo", "head", "A", loader) == "abcd"
    assert budget.read("repo", "head", "B", loader) == ""
    assert budget.read("repo", "head", "A", loader) == "abcd"
    loader.assert_called_once()
    budget = SourceReadBudget(max_file_bytes=2)
    assert budget.read("repo", "head", "A", loader) == ""
    assert budget.read("repo", "head", "A", loader) == ""
    assert loader.call_count == 2
    budget.deadline = time.monotonic() - 1
    assert budget.read("repo", "head", "C", loader) == ""
    assert loader.call_count == 2
