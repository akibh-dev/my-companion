#!/usr/bin/env python3
"""Summarize exact-match and character errors, not audible pronunciation quality."""

import argparse
import csv
import statistics
import unicodedata


def distance(reference, actual):
    previous = list(range(len(actual) + 1))
    for row, left in enumerate(reference, 1):
        current = [row]
        for column, right in enumerate(actual, 1):
            current.append(min(current[-1] + 1, previous[column] + 1, previous[column - 1] + (left != right)))
        previous = current
    return previous[-1]


def normalize(text):
    return unicodedata.normalize("NFC", text)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report")
    arguments = parser.parse_args()
    with open(arguments.report, encoding="utf-8") as source:
        rows = list(csv.DictReader(source, delimiter="\t"))
    for category in sorted({row["category"] for row in rows}):
        group = [row for row in rows if row["category"] == category]
        exact = sum(normalize(row["expected"]) == normalize(row["actual"]) for row in group)
        errors = sum(distance(normalize(row["expected"]), normalize(row["actual"])) for row in group)
        characters = sum(len(normalize(row["expected"])) for row in group)
        print(f"{category}: {exact}/{len(group)} exact, CER={errors / max(1, characters):.1%}")
    if rows:
        for key in ["load_ms", "inference_ms"]:
            values = [int(row[key]) for row in rows]
            print(f"{key}: median={statistics.median(values):g}, max={max(values)}")
    print("Starter diagnostics only: not a held-out acceptance benchmark or pronunciation assessment.")


if __name__ == "__main__":
    main()
