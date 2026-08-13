import os


def _resolve_path(path: str) -> str:
    """智慧型絕對/相對路徑解析：支援從專案根目錄或 python 模組目錄載入模型"""
    if os.path.isabs(path):
        return path
    if os.path.exists(path):
        return path
    parent_path = os.path.join("..", path)
    if os.path.exists(parent_path):
        return parent_path
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    project_root_path = os.path.join(base_dir, "..", path)
    if os.path.exists(project_root_path):
        return project_root_path
    return path
