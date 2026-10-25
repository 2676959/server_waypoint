"""Case routing preserves explicit unsupported-family results."""
def run_case(case, root):
    if case.feature in ("storage", "commands"):
        from scenarios.storage_runner import run_case as execute
    else:
        from scenarios.inventory import run_case as execute
    return execute(case, root)
