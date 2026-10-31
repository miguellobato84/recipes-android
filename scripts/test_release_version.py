import unittest

from release_version import MAX_VERSION, select_version


class ReleaseVersionTest(unittest.TestCase):
    def test_first_release(self):
        self.assertEqual(select_version([], []), 1)

    def test_orders_numerically(self):
        self.assertEqual(select_version(["v9", "v10", "v2"], []), 11)

    def test_ignores_other_formats(self):
        self.assertEqual(select_version(["v0", "v01", "v1.2.3", "release-99", "v3"], []), 4)

    def test_retry_keeps_original_version_even_after_newer_release(self):
        self.assertEqual(select_version(["v1", "v2"], ["v1"]), 1)

    def test_version_code_limit(self):
        self.assertEqual(select_version([f"v{MAX_VERSION - 1}"], []), MAX_VERSION)
        with self.assertRaises(ValueError):
            select_version([f"v{MAX_VERSION}"], [])


if __name__ == "__main__":
    unittest.main()
