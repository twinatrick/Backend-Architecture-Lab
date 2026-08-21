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
        self.assertEqual(changed, {1, 21})

    def test_extract_changed_lines_empty_or_none(self) -> None:
        """測試空 Patch 或 None 輸入回傳空集合。"""
        self.assertEqual(extract_changed_lines(None), set())
        self.assertEqual(extract_changed_lines(""), set())
        self.assertEqual(extract_changed_lines("   "), set())

    def test_extract_changed_lines_added_status(self) -> None:
        """測試新增檔案狀態提取全檔行號。"""
        changed = extract_changed_lines("", status="added", total_lines=5)
        self.assertEqual(changed, {1, 2, 3, 4, 5})

    def test_extract_changed_lines_without_hunk_header_with_plus(self) -> None:
        """測試無標準 Hunk 標頭但有加號的新增片段正確解析行號。"""
        patch = "+added line without header"
        self.assertEqual(extract_changed_lines(patch), {1})

    def test_extract_changed_lines_plain_text_without_plus(self) -> None:
        """測試無 Hunk 且無加號之普通文字回傳空集合。"""
        patch = "plain context line without plus"
        self.assertEqual(extract_changed_lines(patch), set())


if __name__ == "__main__":
    unittest.main()
