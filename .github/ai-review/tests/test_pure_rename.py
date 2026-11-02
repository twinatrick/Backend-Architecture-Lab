import base64
import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

from snapshot_static_checks import run_snapshot_static_checks
from source_budget import SourceReadBudget
from static_checks import run_static_checks


def rename_entry(**overrides):
    entry = {
        "filename": "config/quality/qodana.yaml", "status": "renamed",
        "previous_filename": "qodana.yaml", "additions": 0, "deletions": 0, "changes": 0,
    }
    entry.update(overrides)
    return entry


def source_getter(base="version: 1.0\n", head="version: 1.0\n"):
    def fetch(url, params=None):
        source = base if params["ref"] == "base-fixed" else head
        if source is None:
            return {}
        return {"encoding": "base64", "content": base64.b64encode(source.encode()).decode()}

    return MagicMock(side_effect=fetch)


@pytest.mark.parametrize("previous,path", [
    ("qodana.yaml", "config/quality/qodana.yaml"),
    ("Dockerfile", "docker/backend/Dockerfile"),
    ("init-dbs.sql", "docker/postgres/init-dbs.sql"),
    ("prometheus.yml", "docker/prometheus/prometheus.yml"),
])
def test_verified_rename_has_no_missing_patch_and_records_fixed_evidence(previous, path):
    getter = source_getter()
    audit = []
    findings = run_snapshot_static_checks(
        [rename_entry(previous_filename=previous, filename=path)],
        "owner/repo", "head-fixed", getter, base_sha="base-fixed", audit=audit,
    )
    assert findings == []
    assert getter.call_args_list[0].kwargs == {"params": {"ref": "head-fixed"}}
    assert getter.call_args_list[1].args[0].endswith("/contents/" + previous)
    assert getter.call_args_list[1].kwargs == {"params": {"ref": "base-fixed"}}
    assert audit[0]["check"] == "identical_content_rename"
    assert audit[0]["base_sha"] == "base-fixed"
    assert audit[0]["head_sha"] == "head-fixed"


@pytest.mark.parametrize("overrides", [
    {"status": "modified"}, {"previous_filename": ""},
    {"previous_filename": "config/quality/qodana.yaml"},
    {"additions": 1}, {"deletions": 1}, {"changes": 1},
    {"changes": None}, {"changes": False},
])
def test_unproven_rename_stays_blocked(overrides):
    findings = run_snapshot_static_checks(
        [rename_entry(**overrides)], "owner/repo", "head-fixed", source_getter(),
        base_sha="base-fixed",
    )
    assert any(item["rule"] == "REVIEW_INCOMPLETE" for item in findings)


@pytest.mark.parametrize("base", [None, "version: 2.0\n", "version: 1.0\r\n"])
def test_missing_or_changed_base_including_newlines_stays_blocked(base):
    findings = run_snapshot_static_checks(
        [rename_entry()], "owner/repo", "head-fixed", source_getter(base=base),
        base_sha="base-fixed",
    )
    assert any(item["rule"] == "REVIEW_INCOMPLETE" for item in findings)


def test_candidate_flag_cannot_bypass_missing_patch():
    findings = run_static_checks([
        rename_entry(full_content="version: 1.0\n", verified_rename=True),
    ])
    assert findings[0]["rule"] == "REVIEW_INCOMPLETE"


def test_verified_rename_still_checks_security_at_destination(monkeypatch):
    captured = []

    def inspect(path, content, *, changed_lines):
        captured.append((path, content, changed_lines))
        return []

    monkeypatch.setattr("static_checks.check_secrets", inspect)
    run_snapshot_static_checks(
        [rename_entry()], "owner/repo", "head-fixed", source_getter(), base_sha="base-fixed",
    )
    assert captured == [("config/quality/qodana.yaml", "version: 1.0\n", {1})]


@pytest.mark.parametrize("base_sha", ["", "head-fixed"])
def test_missing_or_same_revision_cannot_prove_rename(base_sha):
    findings = run_snapshot_static_checks(
        [rename_entry()], "owner/repo", "head-fixed", source_getter(), base_sha=base_sha,
    )
    assert any(item["rule"] == "REVIEW_INCOMPLETE" for item in findings)


def test_rename_base_read_obeys_source_budget():
    getter = source_getter()
    findings = run_snapshot_static_checks(
        [rename_entry()], "owner/repo", "head-fixed", getter, base_sha="base-fixed",
        source_budget=SourceReadBudget(max_total_bytes=len("version: 1.0\n")),
    )
    assert any(item["rule"] == "REVIEW_INCOMPLETE" for item in findings)
    assert getter.call_count == 1


def test_verified_rename_still_dispatches_workflow_checks(monkeypatch):
    captured = []

    def inspect(path, content, *, changed_lines):
        captured.append((path, changed_lines))
        return []

    monkeypatch.setattr("static_checks.check_workflow_file", inspect)
    run_snapshot_static_checks(
        [rename_entry(filename=".github/workflows/build.yml")],
        "owner/repo", "head-fixed", source_getter(), base_sha="base-fixed",
    )
    assert captured == [(".github/workflows/build.yml", {1})]
