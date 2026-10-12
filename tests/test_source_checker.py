#!/usr/bin/env python3
"""Tests only the static source-contract checker, not Oreslang execution."""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[1]
CHECKER = PROJECT / "scripts" / "check_source_contract.py"
SOURCE = PROJECT / "src" / "int_linked_queue.ores"

class SourceCheckerRegression(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "src").mkdir()
        (self.root / "src" / SOURCE.name).write_text(
            SOURCE.read_text(encoding="utf-8"), encoding="utf-8")

    def run_checker(self):
        return subprocess.run([sys.executable, str(CHECKER), "--root",
                              str(self.root)], capture_output=True,
                              text=True, check=False)

    def edit(self, before, after):
        file = self.root / "src" / SOURCE.name
        original = file.read_text(encoding="utf-8")
        self.assertIn(before, original)
        file.write_text(original.replace(before, after, 1), encoding="utf-8")

    def test_valid_source_shape(self):
        result = self.run_checker()
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_comment_cannot_spoof_mutable_receiver(self):
        self.edit("pub enqueue(mut self)(", "// pub enqueue(mut self)(\n  pub enqueue(")
        result = self.run_checker()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("enqueue", result.stderr)

    def test_observer_cannot_be_mutable(self):
        self.edit("pub contains(int key)", "pub contains(mut self)(int key)")
        result = self.run_checker()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("contains", result.stderr)

    def test_duplicate_observer_rejected(self):
        self.edit("pub size() -> int", "pub size() -> int { return 0; }\n  pub size() -> int")
        result = self.run_checker()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("size", result.stderr)

    def test_mutable_referent_required(self):
        self.edit("let mut Array<int> previous", "let Array<int> previous")
        result = self.run_checker()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("previous", result.stderr)

    def test_legacy_conditional_rejected(self):
        folder = self.root / "tests"
        folder.mkdir()
        (folder / "legacy.ores").write_text(
            "pub fnc main() -> int {\n  if true; do return 1; fi\n}\n",
            encoding="utf-8")
        result = self.run_checker()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("legacy conditional", result.stderr)

if __name__ == "__main__":
    unittest.main()
