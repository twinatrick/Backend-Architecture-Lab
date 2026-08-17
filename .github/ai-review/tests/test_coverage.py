def validate_coverage(expected, reviewed):
    return sorted(expected) == sorted(reviewed) and len(reviewed) == len(set(reviewed))


def test_exact_coverage_passes():
    assert validate_coverage(["a.java", "b.java"], ["a.java", "b.java"])


def test_missing_file_fails():
    assert not validate_coverage(["a.java", "b.java"], ["a.java"])


def test_extra_file_fails():
    assert not validate_coverage(["a.java"], ["a.java", "b.java"])


def test_duplicate_file_fails():
    assert not validate_coverage(["a.java", "b.java"], ["a.java", "a.java", "b.java"])
