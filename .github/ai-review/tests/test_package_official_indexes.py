"""官方索引與多筆版本證據的安全邊界回歸測試。"""

import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest
import requests

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

from package_version_inspector import PackageVersionInspector
from review_snapshot import InspectionStatus, ReviewSnapshot
from rule_registry import RuleRegistry

INDEX = "https://download.pytorch.org/whl/cu126"
PINS = "torch==2.13.0+cu126\ntorchaudio==2.11.1"
SOURCE = f"--extra-index-url {INDEX}\n{PINS}"
WHEEL = "torch-2.13.0%2Bcu126-cp311-cp311-win_amd64.whl"


def inspect(getter, source=SOURCE, evidence=PINS):
    snapshot = ReviewSnapshot(
        [{"filename": "requirements.txt", "full_content": source}], head_sha="head",
    )
    return PackageVersionInspector(getter).inspect(
        {"location": "requirements.txt:2", "evidence": evidence}, snapshot,
    )


def responses():
    return [
        MagicMock(status_code=200, text=f'<a href="{INDEX}/{WHEEL}">wheel</a>'),
        MagicMock(status_code=200, json=lambda: {
            "info": {"name": "torchaudio", "version": "2.11.1"},
            "urls": [{"filename": "torchaudio.whl", "yanked": False}],
        }),
    ]


def test_all_evidence_versions_need_official_proof():
    getter = MagicMock(side_effect=responses())
    result = inspect(getter)
    assert result.status == InspectionStatus.COMPLIANT
    assert WHEEL.replace("%2B", "+") in result.verified_snippet
    assert "torchaudio" in result.verified_snippet
    assert getter.call_count == 2
    getter.assert_any_call(f"{INDEX}/torch/", timeout=8, allow_redirects=False)
    getter.assert_any_call("https://pypi.org/pypi/torchaudio/2.11.1/json",
                           timeout=8, allow_redirects=False)


def test_official_cdn_link_is_evidence_without_download():
    replies = responses()
    replies[0].text = (f'<a href="https://download-r2.pytorch.org/whl/cu126/{WHEEL}'
                       '#sha256=example">wheel</a>')
    getter = MagicMock(side_effect=replies)
    assert inspect(getter).status == InspectionStatus.COMPLIANT
    assert getter.call_count == 2


@pytest.mark.parametrize("option", [
    "--extra-index-url https://private.example/whl",
    "--extra-index-url https://download.pytorch.org.evil/whl/cu126",
    "--extra-index-url https://download.pytorch.org/whl/..",
    "--index-url https://download.pytorch.org/whl/cu126",
    "-r other.txt", "package @ https://evil.example/file",
    "--extra-index-url ${INDEX}",
])
def test_untrusted_sources_never_trigger_network(option):
    getter = MagicMock()
    assert inspect(getter, f"{option}\n{PINS}").status == InspectionStatus.INCONCLUSIVE
    getter.assert_not_called()


@pytest.mark.parametrize("href", [
    "https://evil.example/" + WHEEL,
    "https://download-r2.pytorch.org.evil/whl/cu126/" + WHEEL,
    "https://download.pytorch.org/whl/cu121/" + WHEEL,
    INDEX + "/" + WHEEL.replace("2.13.0", "2.13.1"),
    INDEX + "/" + WHEEL.replace("torch-", "other-"),
    INDEX + "/" + WHEEL.replace("%2Bcu126", ""),
    INDEX + "/" + WHEEL.replace(".whl", ".tar.gz"),
])
def test_unrelated_or_external_wheels_do_not_prove_existence(href):
    getter = MagicMock(return_value=MagicMock(status_code=200, text=f'<a href="{href}">x</a>'))
    assert inspect(getter).status == InspectionStatus.INCONCLUSIVE


@pytest.mark.parametrize("response", [
    MagicMock(status_code=302), MagicMock(status_code=404),
    MagicMock(status_code=429), MagicMock(status_code=500),
    MagicMock(status_code=200, text="not an index"),
])
def test_wheel_query_failure_is_inconclusive(response):
    assert inspect(MagicMock(return_value=response)).status == InspectionStatus.INCONCLUSIVE


def test_second_unproven_version_keeps_entire_claim_pending():
    getter = MagicMock(side_effect=[responses()[0], MagicMock(status_code=500)])
    assert inspect(getter).status == InspectionStatus.INCONCLUSIVE


def test_pypi_404_falls_back_without_following_wheel_links():
    getter = MagicMock(side_effect=[MagicMock(status_code=404), MagicMock(
        status_code=200,
        text=f'<a data-yanked="reason" href="{INDEX}/torch-2.13.0-cp311-cp311-win_amd64.whl">x</a>',
    )])
    result = inspect(getter, f"--extra-index-url={INDEX}/\ntorch==2.13.0", "torch==2.13.0")
    assert result.status == InspectionStatus.COMPLIANT
    assert '"yanked": true' in result.verified_snippet
    assert getter.call_count == 2


def test_missing_or_unparseable_evidence_never_partially_clears_claim():
    for evidence in (PINS + "\nmissing==1", PINS + "\nnot a pin"):
        getter = MagicMock()
        assert inspect(getter, evidence=evidence).status == InspectionStatus.INCONCLUSIVE
        getter.assert_not_called()


def test_timeout_never_proves_nonexistence():
    result = inspect(MagicMock(side_effect=requests.Timeout()))
    assert result.status == InspectionStatus.INCONCLUSIVE


@pytest.mark.parametrize("problem", ["指定了不存在的套件版本", "此版本尚未發布", "無此版本"])
def test_version_allegation_routes_to_inspector(problem):
    assert isinstance(RuleRegistry().get_inspector("供應鏈", problem), PackageVersionInspector)
