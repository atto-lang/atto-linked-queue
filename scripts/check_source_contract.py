#!/usr/bin/env python3
"""Conservative source-contract check, NOT an Oreslang compiler or formatter."""
from pathlib import Path
import argparse
import re
import sys

parser = argparse.ArgumentParser(description="Conservative Oreslang queue source contract")
parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
ROOT = parser.parse_args().root.resolve()
MUTATORS = (
    "initialize",
    "enqueue",
    "add_to_front",
    "insert_before",
    "insert_after",
    "remove",
    "dequeue",
    "remove_last",
    "clear",
)
errors = []

for path in sorted(ROOT.rglob("*.ores")):
    rel = path.relative_to(ROOT)
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    if text and not text.endswith("\n"):
        errors.append(f"{rel}: missing final newline")
    for line_no, line in enumerate(lines, 1):
        stripped = line.lstrip()
        if not stripped or stripped.startswith("//"):
            continue
        if "\t" in line[: len(line) - len(stripped)]:
            errors.append(f"{rel}:{line_no}: tab indentation")
        if re.match(r"(if|elif)\s", stripped):
            if re.search(r";\s*(then|do)(?=\s|$)", stripped):
                errors.append(f"{rel}:{line_no}: legacy conditional separator")
            if re.search(r"\b(then|do)\b.*\b(fi|return)\b", stripped):
                errors.append(f"{rel}:{line_no}: compact conditional body")
        if re.search(r"\bimport\s+.*?from\s+[\"']java:", stripped):
            errors.append(f"{rel}:{line_no}: forbidden Java interop")
    if rel.parts[0] == "tests":
        if re.search(r"\blet\s+IntLinkedQueue\s+\w+\s*=", text):
            # Intentional negative test of read-only bindings is excepted.
            if rel.name != "readonly_binding.ores":
                errors.append(f"{rel}: queue test needs a writable val owner")

source = (ROOT / "src" / "int_linked_queue.ores").read_text(encoding="utf-8")
# Signatures inside comments do not count as declarations.
# Still only a source-shape checker, NOT a lexer/parser or borrow checker.
declarations_source = "\n".join(line.split("//", 1)[0] for line in source.splitlines())
declarations = re.findall(
    r"(?m)^\s*pub\s+([A-Za-z_]\w*)\s*(?:\(\s*mut\s+self\s*\))?\s*\(",
    declarations_source,
)
READERS = ("size", "is_empty", "contains", "get", "first", "last",
           "ordered_keys", "validate")
for method in MUTATORS:
    expression = rf"(?m)^\s*pub\s+{re.escape(method)}\s*\(\s*mut\s+self\s*\)\s*\("
    if declarations.count(method) != 1 or not re.search(expression, declarations_source):
        errors.append(f"src/int_linked_queue.ores: {method} must appear once with mut self")
for method in READERS:
    expression = rf"(?m)^\s*pub\s+{re.escape(method)}\s*\("
    if declarations.count(method) != 1 or not re.search(expression, declarations_source):
        errors.append(f"src/int_linked_queue.ores: {method} must appear once without mut self")
for field in ("previous", "following", "values", "occupied"):
    expression = rf"\blet\s+mut\s+Array<int>\s+{field}\s*="
    if not re.search(expression, declarations_source):
        errors.append(f"src/int_linked_queue.ores: {field} lacks mutable referent binding")

if errors:
    print("\n".join(errors), file=sys.stderr)
    raise SystemExit(1)
print("Source contract: static style, receiver and interop checks passed.")
print("NOT a compilation, runtime, borrow-checker or concurrency test.")
