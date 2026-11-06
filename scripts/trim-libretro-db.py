#!/usr/bin/env python3
"""Prune a CI checkout's Libretro metadata DB to NES/Famicom records.

This script edits the supplied SQLite file in place. It is designed for a
temporary GitHub Actions checkout, not for modifying the repository's canonical
upstream database. To restore a locally modified asset, use git restore.
"""

import argparse
import sqlite3
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("database", type=Path, help="Copy of the Libretro SQLite asset to prune")
    parser.add_argument(
        "--in-place",
        action="store_true",
        help="Explicitly permit changing the supplied SQLite file",
    )
    args = parser.parse_args()
    if not args.in_place:
        parser.error("Pass --in-place to acknowledge that this edits the SQLite asset")
    if not args.database.is_file():
        parser.error(f"SQLite asset not found: {args.database}")

    initial_size = args.database.stat().st_size

    with sqlite3.connect(args.database) as conn:
        if conn.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
            raise RuntimeError("Input database failed SQLite integrity check")
        columns = {
            row[1] for row in conn.execute("PRAGMA table_info(games)")
        }
        if not {"id", "system", "romName"}.issubset(columns):
            raise RuntimeError(f"Unexpected Libretro database schema: {sorted(columns)}")

        total = conn.execute("SELECT COUNT(*) FROM games").fetchone()[0]
        nes = conn.execute(
            "SELECT COUNT(*) FROM games WHERE system = ?", ("nes",)
        ).fetchone()[0]
        print(f"Libretro metadata: {total:,} records total, {nes:,} NES records")
        if total == 0 or nes == 0:
            systems = conn.execute(
                "SELECT system, COUNT(*) AS n FROM games GROUP BY system ORDER BY n DESC LIMIT 10"
            ).fetchall()
            raise RuntimeError(
                "Refusing to prune database with no NES records; "
                f"most frequent systems: {systems}"
            )

        conn.execute("DELETE FROM games WHERE system IS NULL OR system != ?", ("nes",))
        conn.commit()
        conn.execute("VACUUM")

        remaining = conn.execute("SELECT COUNT(*) FROM games").fetchone()[0]
        if remaining != nes or conn.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
            raise RuntimeError("Pruned NES metadata did not pass validation")

    final_size = args.database.stat().st_size
    print(f"NES metadata: {remaining:,} records retained")
    print(
        f"SQLite asset size: {initial_size:,} -> {final_size:,} bytes "
        f"({(initial_size-final_size)/initial_size:.1%} reduction)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
