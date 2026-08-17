def _is_mock(obj: object) -> bool:
    """判斷物件是否為測試環境的 Mock（用於防禦 import 被 mock 時的行為）"""
    return obj.__class__.__name__ in ("MagicMock", "NonCallableMagicMock", "Mock")
