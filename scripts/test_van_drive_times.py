# /// script
# requires-python = ">=3.11"
# dependencies = ["shapely>=2.0"]
# ///
"""
Checks scripts/van_drive_times.py without Docker: keys match :domain's, and adding a site leaves every other
site's block byte for byte as it was (append-only, ADR-0008).

    uv run scripts/test_van_drive_times.py
"""

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import van_drive_times as v  # noqa: E402


class VanDriveTimesTest(unittest.TestCase):
    def test_keys_match_domain(self):
        # The same text and point as VanSitesTest and VanPageTest in Kotlin.
        self.assertEqual(
            "39.78313,-105.05980|on-ramp to eb i-70 from harlan st",
            v.site_key(39.78313, -105.0598, "ON-RAMP TO EB I-70 FROM HARLAN ST.    Fri   7:00 AM - 5:00 PM"),
        )
        self.assertEqual("39.12346,-105.00000|x", v.site_key(39.123455, -105.0, "X  Mon  7:00 AM - 5:00 PM"))

    def test_adding_a_site_keeps_every_other_block(self):
        spans = [(1, 2), (0, 3)]
        head = v.MAGIC + bytes([v.VERSION]) + bytes(16) + (2).to_bytes(4, "big") + (3).to_bytes(4, "big")
        head += b"".join(a.to_bytes(4, "big") + b.to_bytes(4, "big") for a, b in spans)
        old = {"a|harlan": bytes([10, 11, 20, 21, 22]), "b|pecos": bytes([30, 255, 40, 41, 42])}
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "t.bin"
            v.write_table(path, head, old)
            before = path.read_bytes()
            read_head, read_blocks = v.read_table(path)
            self.assertEqual(head, read_head)
            self.assertEqual(old, read_blocks)
            # A re-routed copy of an old site never replaces it; a new one goes on the end.
            merged = v.merge(read_blocks, {"a|harlan": bytes([1, 1, 1, 1, 1]), "c|new": bytes([5, 6, 7, 8, 9])})
            v.write_table(path, read_head, merged)
            after = path.read_bytes()
            _, blocks = v.read_table(path)
        self.assertEqual(list(blocks), ["a|harlan", "b|pecos", "c|new"])
        self.assertEqual(old["a|harlan"], blocks["a|harlan"])
        self.assertEqual(old["b|pecos"], blocks["b|pecos"])
        # The old file, but for its site count, is the start of the new one.
        count_at = len(head)
        self.assertEqual(before[:count_at], after[:count_at])
        self.assertEqual(before[count_at + 4:], after[count_at + 4:len(before)])


if __name__ == "__main__":
    unittest.main()
