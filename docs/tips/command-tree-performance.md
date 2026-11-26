# Command tree performance

Treat the registered Brigadier tree as a performance-sensitive data structure.
Minecraft can copy and index the player-visible tree during command-list refreshes,
including login and permission updates, even when nobody runs the command. A
large tree can therefore cause stalls with an empty waypoint catalog.

## Design rules

- Use literals for actions and option names. When enum values would duplicate
  substantial suffix subtrees, use one native argument with bounded suggestions
  and explicit validation. Small terminal literal choices are still reasonable.
- Read selected values from the parsed command context. Avoid registering a copy
  of every remaining option subtree for each captured mode, direction, or view.
- Prefer one documented option order. Add alternate orders only for concrete
  interaction needs, such as generated pagination links; arbitrary permutations
  multiply branches. Count every registration scope, including private, remote,
  dimension, and list scopes, when estimating the total cost.
- Use argument types the vanilla client can serialize and understand, such as
  `StringArgumentType.word()` and bounded integer arguments. Suggestions only
  assist input: validate allowed values and combinations before conversion or
  side effects. Report invalid input with a syntax error at the argument cursor.
- Preserve permission checks, exact identity routing, quoting, and generated
  command links. For suggestion providers that read arguments through redirects,
  use `context.getLastChild()` and retain the supplied suggestion builder's
  replacement offsets; see [Brigadier suggestions](brigadier-suggestions.md).

For example, represent the value choices once:

```text
sort <mode> order <direction> limit <n> view <view>
```

Offer `name`, `color`, and other valid modes through completion, then resolve the
selection at execution. Do not attach a full direction/limit/view subtree under
every mode literal. Word arguments defer enum rejection to server execution, so
the client may initially highlight an invalid enum value as a valid string.

## Verify size and behavior

1. Count nodes by object identity in both the registered tree and the
   player-visible copy. Add regression budgets appropriate to the command scope;
   do not rely on structural equality to count distinct objects. Test empty-data
   registration too, because tree size can be independent of waypoint count.
2. Test valid and invalid option combinations, bounds and error cursors, reserved
   identities, generated links, permission denial/revocation, and direct and
   nested redirect completion and execution. Update native command-inventory
   tooling when argument names change.
3. When a change significantly affects tree shape, compare native command copying,
   packet construction/indexing, and encoded size on the target Minecraft version.
   Separately verify packet encode/decode and redirect reconstruction. Keep the
   same fixture and environment, retain raw baseline/candidate samples, and label
   isolated measurements separately from live player behavior.

Server-side node reuse is not enough by itself. Minecraft 26.3's
`Commands.fillUsableCommands` copies children again for every path, so a compact
shared server graph can still expand in the player-visible copy. Its copy and
packet-index maps also invoke structural node hashing/equality. Check the target
version's implementation before relying on sharing or redirects as an optimization.
Shrink unnecessary grammar expansion before considering changes to Minecraft's
global lookup maps.

## Local references

- [ListCommandOptions](../../common/src/main/java/_959/server_waypoint/command/ListCommandOptions.java): native value arguments, completion, and execution validation.
- [ListCommandOptionsTest](../../common/src/test/java/_959/server_waypoint/command/ListCommandOptionsTest.java): size budgets and parsing/completion regressions.
- [Native 26.3 probe](../../tools/server-performance/command-tree/README.md): actual copy and packet algorithms without starting a server.
- [Issue #35 validation](../features/server-performance/validation/2026-10-27-command-tree.md): historical measurements and their proof boundaries.
