# Commit messages

Follow Conventional Commits 1.0.0 when writing commit messages. This guide adapts the [official summary](https://www.conventionalcommits.org/en/v1.0.0/#summary) into a local reference.

## Format

```text
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

The type identifies the change; an optional scope in parentheses identifies the affected area. Keep the description concise. Separate the body and footers with blank lines when present.

## Types and breaking changes

- `fix`: corrects a bug; corresponds to a SemVer PATCH release.
- `feat`: adds a feature; corresponds to a SemVer MINOR release.
- A breaking API change corresponds to a SemVer MAJOR release. Mark it with `!` immediately before the colon, or a `BREAKING CHANGE:` footer explaining the change. Either marker works with any type.
- Other types are allowed, including `build`, `chore`, `ci`, `docs`, `style`, `refactor`, `perf`, and `test`. These are examples rather than a required list; they carry no implicit SemVer effect unless the commit introduces a breaking change.
- Additional footers may use Git-trailer-style metadata, such as `Refs: #35`.

## Repository examples

```text
fix(paper): prevent navigation items from moving through hoppers
feat(common): add private waypoint quotas
docs: document commit message conventions
feat(protocol)!: require collection scope in waypoint updates

BREAKING CHANGE: waypoint updates now require an explicit collection scope.
```
