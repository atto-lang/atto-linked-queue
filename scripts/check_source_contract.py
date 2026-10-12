#!/usr/bin/env python3
"""Conservative source-contract check, NOT an Oreslang compiler or formatter."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
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
for method in MUTATORS:
    expression = rf"\bpub\s+{re.escape(method)}\s*\(mut\s+self\)\s*\("
    if not re.search(expression, source):
        errors.append(f"src/int_linked_queue.ores: {method} missing explicit mut self")
for field in ("previous", "following", "values", "occupied"):
    expression = rf"\blet\s+mut\s+Array<int>\s+{field}\s*="
    if not re.search(expression, source):
        errors.append(f"src/int_linked_queue.ores: {field} lacks mutable referent binding")

if errors:
    print("\n".join(errors), file=sys.stderr)
    raise SystemExit(1)
print("Source contract: static style, receiver and interop checks passed.")
print("NOT a compilation, runtime, borrow-checker or concurrency test.")
