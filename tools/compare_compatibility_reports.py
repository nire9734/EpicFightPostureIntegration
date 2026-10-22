#!/usr/bin/env python3
"""Compare Forge 3.0.2 renderer IDs with NeoForge's renderer inventory.

Forge's DEBUG 'accepted' indicates successful model preparation, NOT
necessarily final patch/renderer registration. This script never interprets
absence from either log as proof that an entity is unsupported.
The Forge renderer-coverage TSV, when available, instead reports final
verified renderer registrations and is the preferred comparison source.
"""
import argparse
import csv
import gzip
import re
from collections import Counter
from pathlib import Path

ACCEPTED = re.compile(
    r"EFPI (Gecko|Lionfish|ModelPart) humanoid accepted "
    r"([a-z0-9_.-]+:[a-z0-9_./-]+)", re.IGNORECASE
)
REJECTED = re.compile(
    r"EFPI (Gecko|Lionfish|ModelPart) humanoid (?:rejected|skipped) "
    r"([a-z0-9_.-]+:[a-z0-9_./-]+)", re.IGNORECASE
)
SCAN_COMPLETED = re.compile(r"EFPI compatibility:\s*\d+\s+target\(s\)")
FORGE_SUMMARY = re.compile(r"EFPI compatibility:\s*\d+\s+target\(s\)\s*\{([^}]*)\}")
BACKEND_COUNT = re.compile(r"([a-z]+)=(\d+)", re.IGNORECASE)


def forge_lines(path: Path):
    # Minecraft archives old logs as .log.gz. Keep the archived and current
    # formats equivalent without requiring a manual decompression step.
    opener = gzip.open if path.suffix.lower() == ".gz" else open
    with opener(path, "rt", encoding="utf-8", errors="replace") as stream:
        yield from stream


def read_forge_log(path: Path):
    prepared = {}
    rejected = {}
    last_completed = None
    for line in forge_lines(path):
        hit = ACCEPTED.search(line)
        if hit:
            prepared[hit.group(2)] = {"gecko": "geckolib", "lionfish": "lionfish", "modelpart": "modelpart"}[hit.group(1).lower()]
        hit = REJECTED.search(line)
        if hit:
            rejected[hit.group(2)] = hit.group(1).lower()
        # Forge prints this summary at the END of each renderer registration
        # scan. An earlier acceptance must not survive a later world/reload
        # scan that no longer prepared the same mob.
        if SCAN_COMPLETED.search(line):
            last_completed = prepared, rejected
            prepared, rejected = {}, {}
    # Ignore a possibly incomplete trailing scan when a completed one exists.
    return last_completed if last_completed is not None else (prepared, rejected)


def read_forge_summary(path: Path):
    last = None
    for line in forge_lines(path):
        hit = FORGE_SUMMARY.search(line)
        if hit:
            last = {backend: int(count)
                    for backend, count in BACKEND_COUNT.findall(hit.group(1))}
    return last


def neo_verified_counts(coverage):
    return dict(Counter(status.removeprefix("verified:")
                        for status in coverage.values()
                        if status.startswith("verified:")))


def read_coverage(path: Path):
    results = {}
    with path.open(encoding="utf-8", newline="") as stream:
        filtered = (line for line in stream if not line.startswith("#"))
        for row in csv.DictReader(filtered, delimiter="\t"):
            identifier = row.get("entity_id", "").strip()
            if identifier:
                results[identifier] = row.get("result", "").strip()
    return results


def forge_verified_backends(coverage):
    supported = {"vanilla", "geckolib", "lionfish", "modelpart", "spider"}
    return {entity_id: status.removeprefix("verified:")
            for entity_id, status in coverage.items()
            if status.startswith("verified:")
            and status.removeprefix("verified:") in supported}


def compare(prepared, neo):
    return [
        (identifier, backend, neo.get(identifier, "not_in_neoforge_inventory"))
        for identifier, backend in sorted(prepared.items())
        if neo.get(identifier) != f"verified:{backend}"
    ]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    forge_source = parser.add_mutually_exclusive_group(required=True)
    forge_source.add_argument("--forge-log", type=Path,
                              help="Forge 3.0.2 latest.log/debug.log, optionally .log.gz")
    forge_source.add_argument("--forge-tsv", type=Path,
                              help="Forge config/.../cache/forge-renderer-coverage.tsv (preferred)")
    parser.add_argument("--neoforge-tsv", type=Path, required=True,
                        help="NeoForge 1.21.1 config/.../cache/neoforge-renderer-coverage.tsv")
    parser.add_argument("--output", type=Path,
                        help="Optional TSV of candidates requiring review")
    args = parser.parse_args()
    if args.forge_tsv:
        forge_coverage = read_coverage(args.forge_tsv)
        prepared = forge_verified_backends(forge_coverage)
        rejected = {entity_id: state for entity_id, state in forge_coverage.items()
                    if state.startswith("rejected:")}
        forge_counts = neo_verified_counts(forge_coverage)
        source_label = "verified"
    else:
        prepared, rejected = read_forge_log(args.forge_log)
        forge_counts = read_forge_summary(args.forge_log)
        source_label = "prepared"
    neo = read_coverage(args.neoforge_tsv)
    differences = compare(prepared, neo)
    forge_optional_count = sum((forge_counts or {}).get(k, 0)
                               for k in ("geckolib", "lionfish", "modelpart"))
    id_detail_available = bool(args.forge_tsv or prepared or rejected)
    if args.forge_tsv:
        print(f"Forge verified renderer IDs: {len(prepared)}")
        print(f"Forge TSV rejected IDs: {len(rejected)}")
    else:
        print(f"Forge prepared Gecko/Lionfish/ModelPart IDs: {len(prepared)}")
        print(f"Forge logged rejected/skipped IDs: {len(rejected)}")
    print(f"NeoForge inventoried IDs: {len(neo)}")
    if not id_detail_available and forge_optional_count:
        print("WARNING: Forge has optional-backend registrations, but no"
              " per-entity DEBUG acceptance/rejection entries were found.")
        print("ID-level parity is UNKNOWN for this log. Enable DEBUG for EFPI"
              " on Forge, then rerun with that log; zero differences here"
              " would not establish parity.")
    else:
        print(f"Forge-{source_label} IDs missing equivalent Neo verification: {len(differences)}")
    if forge_counts is not None:
        neo_counts = neo_verified_counts(neo)
        print("\nLatest completed Forge / Neo verified renderer counts (not an in-game test):")
        for backend in sorted(forge_counts.keys() | neo_counts.keys()):
            print(f"  {backend}: Forge={forge_counts.get(backend, 0)}, "
                  f"NeoForge={neo_counts.get(backend, 0)}")
    for entity_id, backend, neo_state in differences:
        print(f"{entity_id}\tforge_{source_label}:{backend}\tneo:{neo_state}")
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with args.output.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream, delimiter="\t")
            writer.writerow(("entity_id", "forge_prepared_backend", "neo_verification"))
            writer.writerows(differences)
        print(f"Wrote candidate review list: {args.output}")

    print("\nNote: Forge DEBUG acceptance is a preparation result, not proof of")
    print("successful in-game animation. Differing mod/MC versions may also")
    print("legitimately produce different entity inventories.")


if __name__ == "__main__":
    main()
