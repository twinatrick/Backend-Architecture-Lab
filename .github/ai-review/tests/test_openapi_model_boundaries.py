import sys
from pathlib import Path

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import check_openapi_models as models


def write_java(root, path, code):
    target = root / "module/src/main/java/example" / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(code, encoding="utf-8")
    return target


@pytest.fixture(autouse=True)
def reset_cache(monkeypatch):
    monkeypatch.setattr(models, "_PUBLIC_API_MODELS_CACHE", None)


def test_graph_expands_nested_dto_cycle_but_not_unused_internal_objects(tmp_path):
    write_java(tmp_path, "Controller/PublicController.java", '''class PublicController {
    @GetMapping("/items")
    public ResponseEntity<List<ParentVo>> list() { return null; }
}''')
    write_java(tmp_path, "Vo/ParentVo.java", "class ParentVo { ChildVo child; }")
    write_java(tmp_path, "Vo/ChildVo.java", "class ChildVo { ParentVo parent; }")
    write_java(tmp_path, "Vo/UnusedVo.java", "class UnusedVo {}")
    write_java(tmp_path, "Controller/internal/Hidden.java", '''class Hidden {
    @GetMapping("/hidden")
    public UnusedVo hidden() { return null; }
}''')
    assert models.get_public_api_model_set(tmp_path) == {"ParentVo", "ChildVo"}


def test_internal_request_mapping_is_not_public_even_in_public_directory(tmp_path):
    write_java(tmp_path, "Vo/HiddenVo.java", "class HiddenVo {}")
    write_java(tmp_path, "Controller/BridgeController.java", '''@RequestMapping("/inner/items")
class BridgeController {
    @GetMapping("/items")
    public HiddenVo list() { return null; }
}''')
    assert models.get_public_api_model_set(tmp_path) == set()


def test_cache_is_scoped_to_repository_not_previous_checkout(tmp_path):
    first, second = tmp_path / "first", tmp_path / "second"
    write_java(first, "Vo/FirstVo.java", "class FirstVo {}")
    write_java(first, "Controller/OneController.java", '''class OneController {
    @GetMapping("/items")
    public FirstVo list() { return null; }
}''')
    assert models.get_public_api_model_set(first) == {"FirstVo"}
    assert models.get_public_api_model_set(second) == set()


def test_comments_and_strings_are_not_model_dependencies(tmp_path):
    write_java(tmp_path, "Vo/RealVo.java", '''class RealVo {
    // CommentVo is not a field
    String sample = "StringVo";
}''')
    write_java(tmp_path, "Vo/CommentVo.java", "class CommentVo {}")
    write_java(tmp_path, "Vo/StringVo.java", "class StringVo {}")
    write_java(tmp_path, "Controller/PublicController.java", '''class PublicController {
    // @GetMapping("/fake") public CommentVo fake() { return null; }
    @GetMapping("/items")
    public RealVo list() { return null; }
}''')
    assert models.get_public_api_model_set(tmp_path) == {"RealVo"}


@pytest.mark.parametrize("path,source", [
    ("module/Controller/internal/Hidden.java", ""),
    ("module/Controller/Public.java", '@RequestMapping(path = "/inner/items")'),
    ("module/Controller/Public.java", '@RequestMapping(value = "/inner/items")'),
])
def test_internal_controller_detection(path, source):
    assert models.is_internal_controller(path, source)


def test_missing_nested_model_paths_are_not_read(tmp_path):
    assert models.expand_nested_models({"MissingVo"}, {"MissingVo": tmp_path / "missing"}) == {
        "MissingVo",
    }


def test_cache_result_cannot_be_mutated_by_callers(tmp_path):
    result = models.get_public_api_model_set(tmp_path)
    result.add("FabricatedVo")
    assert models.get_public_api_model_set(tmp_path) == set()


def test_multiline_mapping_path_array_does_not_hide_public_signature(tmp_path):
    write_java(tmp_path, "Vo/ItemVo.java", "class ItemVo {}")
    write_java(tmp_path, "Controller/PublicController.java", '''class PublicController {
    @GetMapping(path = {
        "/first", "/second"
    })
    public ItemVo list() { return null; }
}''')
    assert models.get_public_api_model_set(tmp_path) == {"ItemVo"}
