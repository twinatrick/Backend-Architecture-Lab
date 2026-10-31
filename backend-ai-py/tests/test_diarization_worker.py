import importlib.util
import json
import sys
from pathlib import Path
from types import ModuleType, SimpleNamespace
from typing import Any
from unittest.mock import MagicMock, patch

import pytest


@pytest.fixture
def worker() -> Any:
    """隔離大型模型依賴，避免影響其他測試的模組載入。"""
    dependencies = {
        name: MagicMock()
        for name in ("soundfile", "torch", "numpy", "huggingface_hub", "pyannote.audio")
    }
    plda_module = ModuleType("pyannote.audio.core.plda")
    plda_module.PLDA = type("PLDA", (), {})
    dependencies["pyannote.audio.core.plda"] = plda_module
    source = Path(__file__).parents[1] / "services" / "diarization_worker.py"
    spec = importlib.util.spec_from_file_location("diarization_worker_under_test", source)
    module = importlib.util.module_from_spec(spec)
    with patch.dict(sys.modules, dependencies):
        spec.loader.exec_module(module)
    return module


def test_legacy_model_keeps_clustering_and_skips_new_plda(
    worker: ModuleType, tmp_path: Path
) -> None:
    config = tmp_path / "config.yaml"
    config.write_text(
        "pipeline:\n  name: SpeakerDiarization\n  params:\n"
        "    clustering: AgglomerativeClustering\n"
        "    segmentation: pyannote/segmentation-3.0\n",
        encoding="utf-8",
    )
    worker.hf_hub_download.return_value = str(config)

    pipeline = worker.load_pipeline("pyannote/speaker-diarization-3.1", "test-token")

    worker.hf_hub_download.assert_called_once_with(
        "pyannote/speaker-diarization-3.1", "config.yaml", token="test-token"
    )
    checkpoint = worker.Pipeline.from_pretrained.call_args.args[0]
    parameters = checkpoint["pipeline"]["params"]
    assert isinstance(parameters.pop("plda"), worker.DisabledPLDA)
    assert parameters == {
        "clustering": "AgglomerativeClustering",
        "segmentation": "pyannote/segmentation-3.0",
        "legacy": True,
    }
    assert pipeline is worker.Pipeline.from_pretrained.return_value
    assert worker.Pipeline.from_pretrained.call_args.kwargs == {"token": "test-token"}


def test_disabled_plda_rejects_accidental_use(worker: ModuleType) -> None:
    plda = worker.DisabledPLDA()

    with pytest.raises(RuntimeError, match="PLDA"):
        plda(None)
    with pytest.raises(RuntimeError, match="PLDA"):
        _ = plda.phi


def test_legacy_model_rejects_plda_based_clustering(
    worker: ModuleType, tmp_path: Path
) -> None:
    config = tmp_path / "config.yaml"
    config.write_text("pipeline:\n  params:\n    clustering: VBxClustering\n", encoding="utf-8")
    worker.hf_hub_download.return_value = str(config)

    with pytest.raises(RuntimeError, match="AgglomerativeClustering"):
        worker.load_pipeline("pyannote/speaker-diarization-3.1", "test-token")
    worker.Pipeline.from_pretrained.assert_not_called()


def test_custom_model_uses_native_token_api(worker: ModuleType) -> None:
    worker.load_pipeline("organization/custom-model", "test-token")

    worker.Pipeline.from_pretrained.assert_called_once_with(
        "organization/custom-model", token="test-token"
    )
    worker.hf_hub_download.assert_not_called()


def test_missing_model_fails_before_audio_processing(worker: ModuleType) -> None:
    worker.Pipeline.from_pretrained.return_value = None

    with pytest.raises(RuntimeError, match="HF_TOKEN"):
        worker.load_pipeline("organization/custom-model", "")


@pytest.mark.parametrize("wrapped_output", [False, True])
@pytest.mark.parametrize("cuda_available", [False, True])
def test_main_preserves_json_and_handles_both_output_formats(
    worker: ModuleType,
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    wrapped_output: bool,
    cuda_available: bool,
) -> None:
    output = tmp_path / "turns.json"
    monkeypatch.setattr(sys, "argv", ["worker", "input.wav", str(output), "cuda"])
    monkeypatch.setenv("DIARIZATION_MODEL", "organization/custom-model")
    monkeypatch.setenv("HF_TOKEN", "test-token")
    annotation = SimpleNamespace(
        itertracks=MagicMock(
            return_value=[(SimpleNamespace(start=1.25, end=2.5), None, "語者甲")]
        )
    )
    pipeline = worker.Pipeline.from_pretrained.return_value
    pipeline.return_value = (
        SimpleNamespace(speaker_diarization=annotation) if wrapped_output else annotation
    )
    samples = MagicMock()
    worker.soundfile.read.return_value = (samples, 16000)
    worker.torch.cuda.is_available.return_value = cuda_available

    worker.main()

    worker.soundfile.read.assert_called_once_with(
        "input.wav", dtype="float32", always_2d=True
    )
    worker.torch.from_numpy.assert_called_once_with(samples.T.copy.return_value)
    pipeline.assert_called_once_with(
        {"waveform": worker.torch.from_numpy.return_value, "sample_rate": 16000}
    )
    if cuda_available:
        pipeline.to.assert_called_once_with(worker.torch.device.return_value)
    else:
        pipeline.to.assert_not_called()
    assert json.loads(output.read_text(encoding="utf-8")) == [
        {"start": 1.25, "end": 2.5, "speaker": "語者甲"}
    ]
    annotation.itertracks.assert_called_once_with(yield_label=True)


def test_usage_without_arguments(worker: ModuleType, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(sys, "argv", ["worker"])

    with pytest.raises(SystemExit) as error:
        worker.main()

    assert error.value.code == 1
    worker.Pipeline.from_pretrained.assert_not_called()
