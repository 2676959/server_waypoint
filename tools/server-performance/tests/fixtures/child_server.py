"""Lifecycle simulator only; never runtime performance evidence."""
import sys

print("simulator ready", flush=True)
for line in sys.stdin:
    if line.strip() == "stop":
        break
