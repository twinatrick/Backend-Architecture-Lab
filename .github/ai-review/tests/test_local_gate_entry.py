import subprocess
import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import check_local


@pytest.mark.parametrize("failure", [OSError("git unavailable"),
                                    subprocess.CompletedProcess([], 128, "", "not a repo")])
def test_git_failure_is_not_treated_as_clean_staging(monkeypatch, failure):
    command = MagicMock()
    if isinstance(failure, Exception):
        command.side_effect = failure
    else:
        command.return_value = failure
    monkeypatch.setattr(check_local.subprocess, "run", command)
    with pytest.raises(RuntimeError):
        check_local.run_git_cmd(["git", "diff", "--cached"])


def test_empty_staged_blob_does_not_fall_back_to_working_tree(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    (tmp_path / "A.py").write_text("UNSTAGED = True", encoding="utf-8")
    monkeypatch.setattr(check_local, "run_git_cmd", MagicMock(side_effect=[
        "M\0A.py\0", "", "",
    ]))
    assert check_local.get_staged_files()[0]["full_content"] == ""


def test_staged_rename_and_spaces_are_read_from_new_index_path(monkeypatch):
    command = MagicMock(side_effect=[
        "R100\0old.py\0folder/new file.py\0D\0deleted.py\0", "NEW = True", "patch",
    ])
    monkeypatch.setattr(check_local, "run_git_cmd", command)
    files = check_local.get_staged_files()
    assert len(files) == 1
    assert files[0]["filename"] == "folder/new file.py"
    assert files[0]["full_content"] == "NEW = True"
    assert command.call_args_list[1].args[0] == ["git", "show", ":folder/new file.py"]


def test_critical_is_blocking_not_a_warning():
    assert check_local.print_report([{"severity": "CRITICAL", "location": "A.py:1"}]) == (1, 0)


@pytest.mark.parametrize("whole_repo,severity,expected", [
    (False, "HIGH", 1), (False, "MEDIUM", 1), (False, "LOW", 0), (True, "HIGH", 1),
])
def test_cli_returns_exit_status_from_current_findings(monkeypatch, whole_repo, severity, expected):
    monkeypatch.setattr(sys, "argv", ["check_local.py"] + (["--all"] if whole_repo else []))
    files = [{"filename": "A.py", "full_content": "VALUE = 1"}]
    monkeypatch.setattr(check_local, "get_staged_files", lambda: files)
    monkeypatch.setattr(check_local, "get_all_repo_files", lambda: files)
    monkeypatch.setattr(check_local.static_checks, "run_static_checks", MagicMock(
        return_value=[{"severity": severity}],
    ))
    assert check_local.main() == expected


def test_cli_no_staged_changes_skips_checks(monkeypatch):
    monkeypatch.setattr(sys, "argv", ["check_local.py"])
    monkeypatch.setattr(check_local, "get_staged_files", lambda: [])
    scanner = MagicMock()
    monkeypatch.setattr(check_local.static_checks, "run_static_checks", scanner)
    assert check_local.main() == 0
    scanner.assert_not_called()


def test_git_success_preserves_content_whitespace(monkeypatch):
    monkeypatch.setattr(check_local.subprocess, "run", MagicMock(return_value=
                        subprocess.CompletedProcess([], 0, "\n  VALUE = 1\n", "")))
    assert check_local.run_git_cmd(["git", "show", ":A.py"]) == "\n  VALUE = 1\n"


def test_all_files_reads_only_supported_tracked_files(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    (tmp_path / "name with spaces.py").write_text("VALUE = 1", encoding="utf-8")
    (tmp_path / "README.md").write_text("not scanned", encoding="utf-8")
    monkeypatch.setattr(check_local, "run_git_cmd", MagicMock(return_value=
                        "name with spaces.py\0README.md\0deleted.py\0"))
    files = check_local.get_all_repo_files()
    assert [item["filename"] for item in files] == ["name with spaces.py"]
    assert files[0]["full_content"] == "VALUE = 1"


@pytest.mark.parametrize("whole_repo", [False, True])
def test_cli_failed_collection_returns_nonzero_without_scanning(monkeypatch, whole_repo, capsys):
    monkeypatch.setattr(sys, "argv", ["check_local.py"] + (["--all"] if whole_repo else []))
    collector = "get_all_repo_files" if whole_repo else "get_staged_files"
    monkeypatch.setattr(check_local, collector, MagicMock(side_effect=RuntimeError("failure")))
    scanner = MagicMock()
    monkeypatch.setattr(check_local.static_checks, "run_static_checks", scanner)
    assert check_local.main() == 1
    scanner.assert_not_called()
    assert "RuntimeError" in capsys.readouterr().err


def test_malformed_staged_record_cannot_silently_skip_file(monkeypatch):
    monkeypatch.setattr(check_local, "run_git_cmd", MagicMock(return_value="M\0"))
    with pytest.raises(RuntimeError):
        check_local.get_staged_files()
