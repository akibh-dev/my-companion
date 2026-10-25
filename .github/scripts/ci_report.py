"""Summarise a Gradle CI run and publish it as a "CI report" check run.

Collects: failed unit tests (JUnit XML), ktlint violations, Android lint errors,
Kotlin compiler errors and the tail of the build log. The check run shows up on
the commit / PR and can be read through the REST API.
"""

import glob
import json
import os
import re
import subprocess
import xml.etree.ElementTree as ET

MAX_TEXT = 60000


def read(path):
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            return f.read()
    except OSError:
        return ""


def test_results():
    total = failed = skipped = 0
    failures = []
    for path in glob.glob("*/build/test-results/**/*.xml", recursive=True):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for case in root.iter("testcase"):
            total += 1
            if case.find("skipped") is not None:
                skipped += 1
            problem = case.find("failure")
            if problem is None:
                problem = case.find("error")
            if problem is not None:
                failed += 1
                detail = (problem.text or problem.get("message") or "").strip()
                lines = detail.splitlines()[:25]
                failures.append(
                    f"### {case.get('classname')}.{case.get('name')}\n```\n" + "\n".join(lines) + "\n```"
                )
    return total, failed, skipped, failures


def lint_errors():
    out = []
    for path in glob.glob("*/build/reports/lint-results-*.xml"):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for issue in root.iter("issue"):
            if issue.get("severity") in ("Error", "Fatal"):
                loc = issue.find("location")
                where = f"{loc.get('file')}:{loc.get('line')}" if loc is not None else "?"
                out.append(f"- **{issue.get('id')}** {where}: {issue.get('message')}")
    return out


def main():
    log = read("build.log")
    outcome = os.environ.get("GRADLE_OUTCOME", "failure")
    total, failed, skipped, failures = test_results()

    compile_errors = [l for l in log.splitlines() if re.match(r"^e: ", l)][:60]
    ktlint_lines = log.splitlines()
    for path in glob.glob("*/build/reports/ktlint/**/*.txt", recursive=True):
        ktlint_lines += read(path).splitlines()
    ktlint = sorted({l.strip() for l in ktlint_lines if re.search(r"\.kts?:\d+:\d+", l) and not l.startswith(("e: ", "w: "))})[:80]
    lint = lint_errors()
    failed_tasks = re.findall(r"Execution failed for task '([^']+)'", log)

    summary = (
        f"Gradle: **{outcome}** | tests: {total} run, {failed} failed, {skipped} skipped"
        + (f" | failed tasks: {', '.join(sorted(set(failed_tasks)))}" if failed_tasks else "")
    )

    parts = []
    if compile_errors:
        parts.append("## Compiler errors\n```\n" + "\n".join(compile_errors) + "\n```")
    if ktlint:
        parts.append("## ktlint\n```\n" + "\n".join(ktlint) + "\n```")
    if lint:
        parts.append("## Android lint errors\n" + "\n".join(lint))
    if failures:
        parts.append("## Failed tests\n" + "\n\n".join(failures[:40]))
    if outcome != "success":
        tail = "\n".join(log.splitlines()[-150:])
        parts.append("## Build log (tail)\n```\n" + tail + "\n```")

    text = "\n\n".join(parts) or "All good."
    if len(text) > MAX_TEXT:
        text = text[:MAX_TEXT] + "\n\n…truncated"

    with open(os.environ.get("GITHUB_STEP_SUMMARY", "/dev/null"), "a") as f:
        f.write(summary + "\n\n" + text + "\n")

    body = {
        "name": "CI report",
        "head_sha": os.environ["HEAD_SHA"],
        "status": "completed",
        "conclusion": "success" if outcome == "success" else "failure",
        "output": {"title": summary[:250], "summary": summary, "text": text},
    }
    repo = os.environ["GITHUB_REPOSITORY"]
    subprocess.run(
        ["gh", "api", "-X", "POST", f"repos/{repo}/check-runs", "--input", "-"],
        input=json.dumps(body).encode(),
        check=False,
    )


if __name__ == "__main__":
    main()
