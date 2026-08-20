import sys
from pathlib import Path

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import batching


def test_build_batches_categorization_and_chunking():
    files = [
        {"filename": ".github/workflows/ci.yml", "patch": "diff -- ci"},
        {"filename": "backend-auth/src/main/java/AuthController.java", "patch": "diff -- auth"},
        {"filename": "backend-service/src/main/java/OrderService.java", "patch": "diff -- service"},
        {"filename": "backend-data/src/main/java/UserEntity.java", "patch": "diff -- entity"},
        {"filename": "backend-feign/src/main/java/UserClient.java", "patch": "diff -- client"},
        {"filename": "backend-ai-py/main.py", "patch": "diff -- python"},
        {"filename": "README.md", "patch": "diff -- doc"},
    ]
    batches = batching.build_batches(files, max_chars=10000)
    scopes = [scope for scope, _ in batches]
    assert "ci" in scopes
    assert "security-api" in scopes
    assert "business" in scopes
    assert "data" in scopes
    assert "integration" in scopes
    assert "python" in scopes
    assert "other" in scopes

    flattened = [filename for _, paths in batches for filename in paths]
    assert sorted(flattened) == sorted([f["filename"] for f in files])


def test_build_batches_splits_large_batch():
    files = [
        {"filename": f"backend-service/Service{i}.java", "patch": "+" * 8000}
        for i in range(4)
    ]
    batches = batching.build_batches(files, max_chars=15000)
    assert len(batches) >= 2
    flattened = [filename for _, paths in batches for filename in paths]
    assert sorted(flattened) == sorted([f["filename"] for f in files])
    assert len(flattened) == 4


def test_build_batches_github_ai_review_python_scope():
    files = [
        {"filename": ".github/ai-review/review.py", "patch": "diff -- py"},
        {"filename": ".github/ai-review/engine.py", "patch": "diff -- py"},
        {"filename": ".github/workflows/ci.yml", "patch": "diff -- ci"},
    ]
    batches = batching.build_batches(files, max_chars=10000)
    scope_map = {path: scope for scope, paths in batches for path in paths}
    assert scope_map[".github/ai-review/review.py"] == "python"
    assert scope_map[".github/ai-review/engine.py"] == "python"
    assert scope_map[".github/workflows/ci.yml"] == "ci"


def test_build_batches_handles_missing_patch():
    files = [
        {"filename": "deleted.txt", "patch": None, "status": "removed"},
        {"filename": "binary.png", "patch": None, "status": "added"},
    ]
    batches = batching.build_batches(files, max_chars=5000)
    flattened = [filename for _, paths in batches for filename in paths]
    assert sorted(flattened) == sorted(["deleted.txt", "binary.png"])
