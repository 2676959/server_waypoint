#!/usr/bin/env python3
from cli import main

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, OSError, RuntimeError, TimeoutError) as error:
        import sys
        print(f"BLOCKED: {error}", file=sys.stderr)
        raise SystemExit(2)
