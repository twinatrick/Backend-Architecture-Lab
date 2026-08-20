"""測試 Unified Diff 變更行解析模組。

驗證從 Patch Hunks 提取新增與修改行號的正確性。
"""

import unittest

from diff_parser import extract_changed_lines


class TestDiffParser(unittest.TestCase):
    """測試 extract_changed_lines 函數。"""

    def test_extract_changed_lines_single_hunk(self) -> None:
        """測試單一 Hunk 的新增與變更行提取。"""
        patch = (
            "@@ -10,4 +10,6 @@\n"
            " context line\n"
            "+added line 1\n"
            "+added line 2\n"
            " context line\n"
        )
        changed = extract_changed_lines(patch)
        self.assertIsNotNone(changed)
        self.assertEqual(changed, {11, 12})

    def test_extract_changed_lines_multiple_hunks(self) -> None:
        """測試多個 Hunk 的新增與變更行提取。"""
        patch = (
            "@@ -1,3 +1,3 @@\n"
            "-old line 1\n"
            "+new line 1\n"
            " context line\n"
            "@@ -20,3 +20,4 @@\n"
            " context line\n"
            "+new line 21\n"
            " context line\n"
        )
        changed = extract_changed_lines(patch)
        self.assertIsNotNone(changed)
        self.assertEqual(changed, {1, 21})

    def test_extract_changed_lines_empty_or_none(self) -> None:
        """測試空 Patch 或 None 輸入回傳 None。"""
        self.assertIsNone(extract_changed_lines(None))
        self.assertIsNone(extract_changed_lines(""))
        self.assertIsNone(extract_changed_lines("   "))

    def test_extract_changed_lines_no_hunk_header(self) -> None:
        """測試無標準 Hunk 標頭的 Patch 回傳 None。"""
        patch = "+added line without header"
        self.assertIsNone(extract_changed_lines(patch))


if __name__ == "__main__":
    unittest.main()
