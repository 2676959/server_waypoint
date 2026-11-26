"""Case routing preserves explicit unsupported-family results."""
def run_case(case, root):
    if ":minimal-critical:" in case.id or case.variant.startswith("minimal-"):
        from scenarios.minimal import run_case as execute
        return execute(case, root)
    if case.feature in ("storage", "commands"):
        from scenarios.storage_runner import run_case as execute
    elif case.feature in ("sync", "upload-edit", "sharing-icons"):
        from scenarios.sync_upload import run_case as execute
    elif case.feature in ("navigation", "map-terrain", "protection"):
        from scenarios.navigation_runner import run_case as execute
    else:
        from scenarios.inventory import run_case as execute
    return execute(case, root)
