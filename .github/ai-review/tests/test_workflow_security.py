import re
from pathlib import Path

import yaml

WORKFLOWS = Path(__file__).resolve().parents[2] / "workflows"


def load_workflow(name):
    # BaseLoader 不將 YAML 1.1 的 on 誤讀成布林值；契約以字串明確比較。
    return yaml.load((WORKFLOWS / name).read_text(encoding="utf-8"), Loader=yaml.BaseLoader)


def test_recorder_requires_new_pr_command_and_explicit_enablement():
    workflow = load_workflow("ai-review-verdict.yml")
    assert workflow["on"] == {"issue_comment": {"types": ["created"]}}
    condition = workflow["jobs"]["record"]["if"]
    assert "github.event.issue.pull_request" in condition
    assert "startsWith(github.event.comment.body, '/ai-verdict ')" in condition
    assert "vars.AI_REVIEW_VERDICT_RECORDER_ENABLED == 'true'" in condition
    assert workflow["jobs"]["record"]["environment"] == "ai-review-verdict-ledger"
    assert workflow["concurrency"]["cancel-in-progress"] == "false"
    assert set(workflow["permissions"].values()) == {"read"}


def test_both_secret_workflows_checkout_pinned_trusted_source_without_credentials():
    for filename in ("ai-review-verdict.yml", "ai-review-trusted.yml"):
        workflow = load_workflow(filename)
        for job in workflow["jobs"].values():
            steps = job["steps"]
            checkout = [step for step in steps if step.get("uses", "").startswith(
                "actions/checkout@",
            )]
            assert len(checkout) == 1
            assert re.fullmatch(r"actions/checkout@[0-9a-f]{40}", checkout[0]["uses"])
            assert checkout[0]["with"]["ref"] == "${{ github.event.repository.default_branch }}"
            assert checkout[0]["with"].get("persist-credentials") == "false"
            assert not any("github.event.comment.body" in step.get("run", "") for step in steps)


def test_reader_has_no_ledger_write_secret_and_recorder_keeps_credentials_separate():
    reader = load_workflow("ai-review-trusted.yml")["jobs"]["review"]["steps"]
    assert "AI_REVIEW_LEDGER_WRITE_TOKEN" not in str(reader)
    recorder = load_workflow("ai-review-verdict.yml")["jobs"]["record"]["steps"]
    env = next(step["env"] for step in recorder if "AI_REVIEW_LEDGER_WRITE_TOKEN" in step.get(
        "env", {},
    ))
    assert env["GH_TOKEN"] == "${{ secrets.AI_REVIEW_LEDGER_READ_TOKEN }}"
    assert env["AI_REVIEW_LEDGER_WRITE_TOKEN"] == "${{ secrets.AI_REVIEW_LEDGER_WRITE_TOKEN }}"


def test_both_workflows_install_trusted_requirements_before_running_python():
    for filename in ("ai-review-verdict.yml", "ai-review-trusted.yml"):
        steps = next(iter(load_workflow(filename)["jobs"].values()))["steps"]
        install = next(index for index, step in enumerate(steps)
                       if "-r .github/ai-review/requirements.txt" in step.get("run", ""))
        executions = [index for index, step in enumerate(steps)
                      if step.get("run", "").startswith("python3 ")
                      and ".github/ai-review/" in step["run"].splitlines()[0]]
        assert executions and all(index > install for index in executions)


def test_runtime_requirements_pin_yaml_distribution():
    requirements = (WORKFLOWS.parent / "ai-review/requirements.txt").read_text(encoding="utf-8")
    assert re.search(r"^PyYAML==\d+\.\d+\.\d+$", requirements, re.MULTILINE)


def test_ci_import_smoke_uses_isolated_runtime_only_environment_without_secrets():
    job = load_workflow("ci.yml")["jobs"]["ai-review-runtime"]
    assert job["permissions"] == {"contents": "read"}
    assert "secrets." not in str(job)
    checkout = job["steps"][0]
    assert re.fullmatch(r"actions/checkout@[0-9a-f]{40}", checkout["uses"])
    assert checkout["with"]["persist-credentials"] == "false"
    run = next(step["run"] for step in job["steps"] if "run" in step)
    assert 'python -m venv "$RUNNER_TEMP/ai-review-runtime"' in run
    assert "--system-site-packages" not in run
    assert run.count("-m pip install") == 1
    assert "-r .github/ai-review/requirements.txt" in run
    assert '"$RUNNER_TEMP/ai-review-runtime/bin/python" -I -' in run
    assert "import review" in run and "import record_verdict" in run
    assert run.index("-m pip install") < run.index("import review")
