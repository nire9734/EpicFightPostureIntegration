import contextlib
import gzip
import io
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from compare_compatibility_reports import (
    compare, forge_verified_backends, main, neo_verified_counts, read_coverage, read_forge_log,
    read_forge_summary,
)


class ParityReportTest(unittest.TestCase):
    def test_parses_forge_acceptance_and_neo_inventory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            forge = root / "forge.log"
            neo = root / "neo.tsv"
            forge.write_text(
                "[DEBUG] EFPI Gecko humanoid accepted example:knight via shared pipeline\n"
                "[DEBUG] EFPI Lionfish humanoid accepted example:mage: confidence=0.5\n"
                "[DEBUG] EFPI ModelPart humanoid accepted example:guard: confidence=0.63\n",
                encoding="utf-8")
            neo.write_text(
                "# EFPI NeoForge renderer coverage v1\nentity_id\tresult\n"
                "example:knight\tverified:geckolib\n"
                "example:mage\trejected:backend_or_registration\n"
                "example:guard\tverified:modelpart\n",
                encoding="utf-8")
            accepted, _ = read_forge_log(forge)
            self.assertEqual(3, len(accepted))
            diff = compare(accepted, read_coverage(neo))
            self.assertEqual(
                [("example:mage", "lionfish", "rejected:backend_or_registration")], diff)

    def test_missing_in_neo_is_not_claimed_as_unsupported(self):
        self.assertEqual(
            [("example:knight", "gecko", "not_in_neoforge_inventory")],
            compare({"example:knight": "gecko"}, {}))

    def test_only_latest_completed_forge_scan_is_compared(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "forge.log"
            path.write_text(
                "EFPI Gecko humanoid accepted example:removed via shared pipeline\n"
                "EFPI compatibility: 2 target(s) {geckolib=1, vanilla=1}\n"
                "EFPI Lionfish humanoid accepted example:kept: confidence=0.8\n"
                "EFPI Gecko humanoid rejected example:removed: no bind model\n"
                "EFPI compatibility: 1 target(s) {lionfish=1}\n"
                # A third scan has started but has not completed yet.
                "EFPI ModelPart humanoid accepted example:incomplete\n",
                encoding="utf-8",
            )
            self.assertEqual({"example:kept": "lionfish"}, read_forge_log(path)[0])
            self.assertEqual({"example:removed": "gecko"}, read_forge_log(path)[1])
            self.assertEqual({"lionfish": 1}, read_forge_summary(path))

    def test_aggregate_counts_are_backend_specific(self):
        self.assertEqual(
            {"vanilla": 1, "geckolib": 2, "spider": 1},
            neo_verified_counts({
                "minecraft:bogged": "verified:vanilla",
                "example:mage": "verified:geckolib",
                "example:sage": "verified:geckolib",
                "example:arachnid": "verified:spider",
                "example:missing": "rejected:backend_or_registration",
            }),
        )

    def test_archived_gzip_forge_log_is_supported(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "2026-10-22-7.log.gz"
            with gzip.open(path, "wt", encoding="utf-8") as stream:
                stream.write("EFPI Gecko humanoid accepted example:mage via shared pipeline\n")
                stream.write("EFPI compatibility: 1 target(s) {geckolib=1}\n")
            self.assertEqual({"example:mage": "geckolib"}, read_forge_log(path)[0])
            self.assertEqual({"geckolib": 1}, read_forge_summary(path))

    def test_info_only_forge_log_does_not_claim_zero_mismatches(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            forge = root / "forge.log"
            neo = root / "neo.tsv"
            forge.write_text("EFPI compatibility: 2 target(s) {geckolib=1, vanilla=1}\n",
                             encoding="utf-8")
            neo.write_text("entity_id\tresult\nexample:mage\tverified:geckolib\n",
                           encoding="utf-8")
            output = io.StringIO()
            with patch("sys.argv", ["compare", "--forge-log", str(forge),
                                    "--neoforge-tsv", str(neo)]):
                with contextlib.redirect_stdout(output):
                    main()
            self.assertIn("ID-level parity is UNKNOWN", output.getvalue())
            self.assertNotIn("missing equivalent Neo verification: 0", output.getvalue())

    def test_forge_tsv_compares_verified_registrations(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            forge = root / "forge-renderer-coverage.tsv"
            neo = root / "neoforge-renderer-coverage.tsv"
            forge.write_text(
                "# EFPI Forge renderer coverage v1\n"
                "entity_id\tresult\n"
                "example:mage\tverified:geckolib\n"
                "example:guard\tverified:modelpart\n"
                "example:knight\tverified:vanilla\n"
                "example:spider\tverified:spider\n",
                encoding="utf-8")
            neo.write_text(
                "# EFPI NeoForge renderer coverage v1\n"
                "entity_id\tresult\n"
                "example:mage\tverified:geckolib\n"
                "example:guard\trejected:backend_or_registration\n",
                encoding="utf-8")
            self.assertEqual({"example:mage": "geckolib", "example:guard": "modelpart",
                              "example:knight": "vanilla", "example:spider": "spider"},
                             forge_verified_backends(read_coverage(forge)))
            output = io.StringIO()
            with patch("sys.argv", ["compare", "--forge-tsv", str(forge),
                                    "--neoforge-tsv", str(neo)]):
                with contextlib.redirect_stdout(output):
                    main()
            self.assertIn("Forge verified renderer IDs: 4", output.getvalue())
            self.assertIn("Forge-verified IDs missing equivalent Neo verification: 3", output.getvalue())
            self.assertIn("example:guard\tforge_verified:modelpart", output.getvalue())


if __name__ == "__main__":
    unittest.main()
