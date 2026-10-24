# Published 4.0.0 coverage ledger

Source revision: `d9f532f8b5a14c9be4bc47531b05b44fd1dc5773`. Static requirements only; execution evidence is pending.

Every command execution/suggestion registration, server event/mixin injection, channel registration, identified scheduler/tick call and configuration field below must be covered by its family. Parameterized command builders require runtime leaf expansion in Task 10; one source registration can create several leaves. Client-only mixins/rendering are outside server performance scope.

Client-only exclusions below are derived from the release mixin metadata and initializer declarations. Shared/client+server mixins remain mapped.

| Source | Kind / trigger | Scenario family or source-backed exclusion |
| --- | --- | --- |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:139` | command: `private final SuggestionProvider<S> WAYPOINT_NAME_SUGGESTION = new WaypointNameSuggestion();` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:140` | command: `private final SuggestionProvider<S> WAYPOINT_LIST_SUGGESTION = new WaypointListSuggestion();` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:141` | command: `private final SuggestionProvider<S> NAME_INITIALS_SUGGESTION = new NameInitialsSuggestion();` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:142` | command: `private final SuggestionProvider<S> PLAYER_YAW_SUGGESTION = new PlayerYawSuggestion();` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:143` | command: `private final SuggestionProvider<S> HEX_COLOR_CODE_SUGGESTION = new HexColorCodeSuggestion();` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:144` | command: `private final SuggestionProvider<S> ICON_SUGGESTION = this::suggestIconIds;` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:384` | command: `.executes(command)` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:400` | command: `visibilityNode.executes(command);` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:402` | command: `keywordsNode.executes(command);` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:404` | command: `descriptionNode.executes(command);` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:407` | command: `.suggests((SuggestionProvider<Object>) ICON_SUGGESTION)` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:408` | command: `.executes(command));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:412` | command: `.suggests((SuggestionProvider<Object>) ICON_SUGGESTION).executes(command)));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:415` | command: `.suggests((SuggestionProvider<Object>) ICON_SUGGESTION).executes(command)));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:427` | command: `.suggests((context, builder) -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:436` | command: `return (ArgumentBuilder<S, ?>) argument(LIST_NAME_ARG, string()).suggests((SuggestionProvider<Object>) WAYPOINT_LIST_SUGGESTION);` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:441` | command: `return (ArgumentBuilder<S, ?>) argument(WAYPOINT_NAME_ARG, string()).suggests((SuggestionProvider<Object>) WAYPOINT_NAME_SUGGESTION);` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:446` | command: `root.executes(context -> executeTargetHint(context.getSource(), "wp.hint_line.details", HelpTopics.Topic.DETAILS));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:452` | command: `.executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:471` | command: `.executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:488` | command: `root.executes(context -> executeTargetHint(context.getSource(), "wp.hint_line.edit", HelpTopics.Topic.EDIT));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:508` | command: `clear.then(LiteralArgumentBuilder.<S>literal("display-name").executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:525` | command: `propertyNode.then(RequiredArgumentBuilder.<S, String>argument(VALUE_ARG, string()).executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:560` | command: `.executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:579` | command: `.executes(context -> executePicker(context, true))` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:581` | command: `.suggests(this.HEX_COLOR_CODE_SUGGESTION)` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:582` | command: `.executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:599` | command: `.executes(context -> executePicker(context, false))` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:600` | command: `.then(RequiredArgumentBuilder.<S, Integer>argument(YAW_ARG, integer()).executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:611` | command: `visibility.then(LiteralArgumentBuilder.<S>literal("global").executes(context -> this.executeVisibilityPatch(context, true)));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:612` | command: `visibility.then(LiteralArgumentBuilder.<S>literal("local").executes(context -> this.executeVisibilityPatch(context, false)));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:618` | command: `.suggests(ICON_SUGGESTION)` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:619` | command: `.executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:633` | command: `clear.then(LiteralArgumentBuilder.<S>literal("icon").executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:690` | command: `propertyNode.then(RequiredArgumentBuilder.<S, String>argument(VALUE_ARG, string()).executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:707` | command: `return LiteralArgumentBuilder.<S>literal(property).executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:723` | command: `return RequiredArgumentBuilder.<S, String>argument(name, string()).suggests(suggestions);` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:729` | command: `.executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:736` | command: `.executes(context -> executeAddPicker((S) context.getSource(), 1))` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:739` | command: `.executes(context -> executeAddPicker((S) context.getSource(),` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:743` | command: `.suggests((SuggestionProvider<Object>) WAYPOINT_LIST_SUGGESTION)` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:744` | command: `.executes(cxt -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:755` | command: `.suggests((SuggestionProvider<Object>) WAYPOINT_NAME_SUGGESTION)` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:756` | command: `.executes(cxt -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:768` | command: `.suggests((SuggestionProvider<Object>) NAME_INITIALS_SUGGESTION)` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:770` | command: `.suggests((SuggestionProvider<Object>) HEX_COLOR_CODE_SUGGESTION)` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:772` | command: `.suggests((SuggestionProvider<Object>) PLAYER_YAW_SUGGESTION)` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:802` | command: `.suggests((SuggestionProvider<Object>) WAYPOINT_LIST_SUGGESTION)` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:804` | command: `.suggests((SuggestionProvider<Object>) WAYPOINT_NAME_SUGGESTION)` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:805` | command: `.executes(` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:819` | command: `.suggests((SuggestionProvider<Object>) NAME_INITIALS_SUGGESTION)` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:821` | command: `.suggests((SuggestionProvider<Object>) HEX_COLOR_CODE_SUGGESTION)` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:823` | command: `.suggests((SuggestionProvider<Object>) PLAYER_YAW_SUGGESTION)` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:857` | command: `.executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:865` | command: `.executes(context -> executeTargetHint((S) context.getSource(), "wp.hint_line.remove",` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:869` | command: `.executes(` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:880` | command: `.executes(` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:897` | command: `.executes(context -> executeTargetHint((S) context.getSource(), "wp.hint_line.tp",` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:914` | command: `.executes(` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:921` | command: `.executes(` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:928` | command: `.executes(` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:939` | command: `.executes(` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:960` | command: `.executes(` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:973` | command: `upload.executes(context -> executeUploadPanel(context.getSource()));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:975` | command: `source.executes(context -> executeUploadAndReturn(` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:983` | command: `server.executes(context -> executeUploadAndReturn(` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:991` | command: `local.executes(context -> executeUploadAndReturn(` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:999` | command: `delete.executes(context -> executeUploadAndReturn(` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:1058` | command: `help.executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:1065` | command: `.executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:1694` | registration: `String token = this.restoreRegistry.register(` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2045` | command: `.executes(context -> executeUploadAndReturn(` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2050` | command: `.executes(context -> executeUploadAndReturn(` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2056` | command: `.executes(context -> executeUploadAndReturn(` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2156` | command: `navigateNode.executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2164` | command: `methodNode.executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2173` | command: `disableNode.executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2179` | command: `methodNode.executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2199` | command: `listNode.suggests(this.WAYPOINT_LIST_SUGGESTION);` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2201` | command: `waypointNode.suggests(this.WAYPOINT_NAME_SUGGESTION);` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2202` | command: `waypointNode.executes(navigateTargetCommand(null));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2205` | command: `defaultNode.executes(navigateTargetWithDefaultCommand());` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2208` | command: `allNode.executes(navigateTargetWithAllCommand());` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2212` | command: `methodNode.executes(navigateTargetCommand(method));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2223` | command: `textDisplayNode.executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2229` | command: `resetNode.executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2274` | command: `zNode.suggests(singleFloatSuggestion((context, builder) -> this.getTextDisplayTransformationZSuggestion(context, zArgument)));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2275` | command: `zNode.executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2290` | command: `yNode.suggests(singleFloatSuggestion((context, builder) -> this.getTextDisplayTransformationYSuggestion(context, yArgument)));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2296` | command: `xNode.suggests(singleFloatSuggestion((context, builder) -> this.getTextDisplayTransformationXSuggestion(context, xArgument)));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2347` | command: `dimensionsNode.executes(context -> executeDimensionList(context.getSource(), 1));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2350` | command: `.executes(context -> executeDimensionList(context.getSource(), getInteger(context, PAGE_NUMBER_ARG)))));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2361` | command: `listNameNode.suggests(this.WAYPOINT_LIST_SUGGESTION);` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2561` | command: `.executes(context -> {` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java:2710` | background: `String hexCode = currentInput.toUpperCase() + "0".repeat(lengthRemain);` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/ListCommandOptions.java:36` | command: `targetNode.executes(command(WaypointSorting.SortMode.DEFAULT, false));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/ListCommandOptions.java:43` | command: `searchNode.executes(reserved.apply(SEARCH_COMMAND));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/ListCommandOptions.java:44` | command: `sortNode.executes(reserved.apply(SORT_COMMAND));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/ListCommandOptions.java:45` | command: `pageNode.executes(reserved.apply(PAGE_COMMAND));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/ListCommandOptions.java:46` | command: `limitNode.executes(reserved.apply(LIMIT_COMMAND));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/ListCommandOptions.java:47` | command: `viewNode.executes(reserved.apply(VIEW_COMMAND));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/ListCommandOptions.java:58` | command: `queryNode.executes(command(WaypointSorting.SortMode.DEFAULT, false));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/ListCommandOptions.java:73` | command: `queryNode.executes(command(sortMode, reversed, view));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/ListCommandOptions.java:85` | command: `pageNode.executes(command(sortMode, reversed, view));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/ListCommandOptions.java:98` | command: `valueNode.executes(command(sortMode, reversed, view));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/ListCommandOptions.java:110` | command: `modeNode.executes(command(sortMode, false));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/ListCommandOptions.java:129` | command: `directionNode.executes(command(sortMode, descending));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/ListCommandOptions.java:144` | command: `pageNode.executes(command(sortMode, reversed));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/ListCommandOptions.java:157` | command: `limitNode.executes(command(sortMode, reversed));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java:63` | command: `.executes(context -> picker(context.getSource(), 1));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java:66` | command: `.executes(context -> picker(context.getSource(), getInteger(context, PAGE_NUMBER_ARG)))));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java:69` | command: `server.suggests((context, builder) -> suggest(context, builder, 0));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java:71` | command: `dimension.suggests((context, builder) -> suggest(context, builder, 1));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java:73` | command: `list.suggests((context, builder) -> suggest(context, builder, 2));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java:79` | command: `detailsServer.suggests((context, builder) -> suggest(context, builder, 0));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java:80` | command: `detailsDimension.suggests((context, builder) -> suggest(context, builder, 1));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java:81` | command: `detailsList.suggests((context, builder) -> suggest(context, builder, 2));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java:82` | command: `detailsWaypoint.suggests((context, builder) -> suggest(context, builder, 3)).executes(this::details);` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java:89` | command: `tpServer.suggests((context, builder) -> suggest(context, builder, 0, true));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java:90` | command: `tpDimension.suggests((context, builder) -> suggest(context, builder, 1, true));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java:91` | command: `tpList.suggests((context, builder) -> suggest(context, builder, 2, true));` | `commands` |
| `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java:92` | command: `tpWaypoint.suggests((context, builder) -> suggest(context, builder, 3, true)).executes(this::teleport);` | `commands` |
| `common/src/main/java/_959/server_waypoint/core/network/C2SPacketHandler.java:483` | background: `List<ReceiveFailure<P>> failures = this.uploadChunkedMessages.tick();` | `sync` |
| `common/src/main/java/_959/server_waypoint/core/network/C2SPacketHandler.java:494` | background: `this.uploadCoordinator.tick().ifPresent(this::clearExpiredUploadTransport);` | `sync` |
| `common/src/main/java/_959/server_waypoint/core/network/PlatformMessageSender.java:233` | background: `List<ReceiveFailure<P>> failures = this.chunkedMessageManager().tick();` | `sync` |
| `common/src/main/java/_959/server_waypoint/core/network/codec/ChunkedMessageManager.java:429` | background: `return this.tick(System.nanoTime());` | `sync` |
| `common/src/main/java/_959/server_waypoint/crossserver/transport/BackendAgent.java:96` | background: `writer = new ScheduledThreadPoolExecutor(1, daemonThreads("server-waypoint-backend-heartbeat"));` | `idle` |
| `common/src/main/java/_959/server_waypoint/crossserver/transport/BackendAgent.java:98` | background: `writer.scheduleWithFixedDelay(remoteCatalogs::maintain, settings.heartbeatMillis(), settings.heartbeatMillis(), TimeUnit.MILLISECONDS);` | `idle` |
| `common/src/main/java/_959/server_waypoint/crossserver/transport/BackendAgent.java:100` | background: `publications = new ScheduledThreadPoolExecutor(1, daemonThreads("server-waypoint-backend-catalog"));` | `idle` |
| `common/src/main/java/_959/server_waypoint/crossserver/transport/BackendAgent.java:103` | background: `reader = Executors.newSingleThreadExecutor(daemonThreads("server-waypoint-backend-reader"));` | `idle` |
| `common/src/main/java/_959/server_waypoint/crossserver/transport/BackendAgent.java:151` | background: `maintenance = writer.scheduleWithFixedDelay(owner::maintain, 100, 100, TimeUnit.MILLISECONDS);` | `idle` |
| `common/src/main/java/_959/server_waypoint/crossserver/transport/BackendAgent.java:153` | background: `heartbeat = writer.scheduleWithFixedDelay(() -> sendHeartbeat(session), settings.heartbeatMillis(),` | `idle` |
| `common/src/main/java/_959/server_waypoint/crossserver/transport/BackendAgent.java:157` | background: `publication = publications.scheduleWithFixedDelay(() -> {` | `idle` |
| `cross-server/src/main/java/_959/server_waypoint/crossserver/transport/AsyncTransportLifecycle.java:14` | background: `control = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(66), task -> {` | `transport` |
| `cross-server/src/main/java/_959/server_waypoint/crossserver/transport/QueuedChannelSender.java:13` | background: `worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(64), task -> {` | `transport` |
| `cross-server/src/main/java/_959/server_waypoint/crossserver/transport/TcpChannel.java:55` | background: `expiry = TcpWire.TIMER.scheduleWithFixedDelay(this::expire, interval, interval, TimeUnit.MILLISECONDS);` | `transport` |
| `cross-server/src/main/java/_959/server_waypoint/crossserver/transport/TcpWire.java:14` | background: `static final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(1, task -> {` | `transport` |
| `cross-server/src/main/java/_959/server_waypoint/crossserver/transport/TcpWire.java:26` | background: `task = TIMER.schedule(() -> {` | `transport` |
| `mods/src/main/java/_959/server_waypoint/common/server/handoff/ModDestinationPlatform.java:38` | background: `server.schedule(new TickTask(server.getTickCount(), action));` | `handoff` |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricClient.java:38` | registration: `KeyMapping.Category.register(modId("mod_name"))` | Excluded: Client-only initializer/event subscriber in mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricClient.java at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricClient.java:43` | registration: `ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->` | Excluded: Client-only initializer/event subscriber in mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricClient.java at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricClient.java:44` | registration: `ClientWaypointCommand.register(dispatcher));` | Excluded: Client-only initializer/event subscriber in mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricClient.java at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricClient.java:45` | registration: `ClientTickEvents.END_CLIENT_TICK.register(client -> {` | Excluded: Client-only initializer/event subscriber in mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricClient.java at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricClient.java:51` | registration: `ClientLifecycleEvents.CLIENT_STARTED.register(client -> {` | Excluded: Client-only initializer/event subscriber in mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricClient.java at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricServer.java:89` | registration: `().register(XaerosWorldIdS2CPayload.ID, XaerosWorldIdS2CPayload.PACKET_CODEC);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricServer.java:94` | registration: `CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, registrationEnvironment) -> waypointCommand.register(dispatcher));` | `idle` |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricServer.java:95` | registration: `ServerLifecycleEvents.SERVER_STARTING.register(waypointServer::load);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricServer.java:96` | registration: `ServerLifecycleEvents.SERVER_STOPPING.register(server -> {` | `idle` |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricServer.java:100` | registration: `ServerTickEvents.END_SERVER_TICK.register(server -> {` | `idle` |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricServer.java:101` | background: `waypointServer.navigation().tick();` | `idle` |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricServer.java:105` | registration: `ServerPlayConnectionEvents.JOIN.register(` | `idle` |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricServer.java:112` | registration: `ServerPlayConnectionEvents.DISCONNECT.register(` | `idle` |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricServer.java:118` | registration: `ServerPlayerEvents.AFTER_RESPAWN.register(` | `idle` |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricServer.java:122` | registration: `ServerMessageEvents.CHAT_MESSAGE.register(handler::onChatMessage);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricServer.java:153` | registration: `().register(MessageChunkS2CPayload.ID, MessageChunkS2CPayload.PACKET_CODEC);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricServer.java:157` | registration: `().register(ServerHandshakeS2CPayload.ID, ServerHandshakeS2CPayload.PACKET_CODEC);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricServer.java:161` | registration: `().register(UploadRequestS2CPayload.ID, UploadRequestS2CPayload.PACKET_CODEC);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricServer.java:166` | registration: `().register(ClientHandshakeC2SPayload.ID, ClientHandshakeC2SPayload.PACKET_CODEC);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricServer.java:170` | registration: `().register(MessageChunkC2SPayload.ID, MessageChunkC2SPayload.PACKET_CODEC);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/fabric/ServerWaypointFabricServer.java:174` | registration: `().register(UploadChunkC2SPayload.ID, UploadChunkC2SPayload.PACKET_CODEC);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/forge/ServerWaypointForge.java:165` | background: `this.waypointServer.navigation().tick();` | `idle` |
| `mods/src/main/java/_959/server_waypoint/forge/ServerWaypointForge.java:172` | background: `this.waypointServer.navigation().tick();` | `idle` |
| `mods/src/main/java/_959/server_waypoint/forge/ServerWaypointForge.java:322` | registration: `this.waypointCommand.register(event.getDispatcher());` | `idle` |
| `mods/src/main/java/_959/server_waypoint/forge/ServerWaypointForgeClient.java:81` | registration: `/*? if >= 1.21.9 {*/ KeyMapping.Category.register(modId("mod_name")) /*?} else {*/ /*"key.category.server_waypoint.mod_name" *//*?}*/` | `idle` |
| `mods/src/main/java/_959/server_waypoint/forge/ServerWaypointForgeClient.java:83` | registration: `event.register(keyBinding);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/forge/ServerWaypointForgeClient.java:87` | registration: `ClientWaypointCommand.register(event.getDispatcher());` | `idle` |
| `mods/src/main/java/_959/server_waypoint/forge/ServerWaypointForgeClient.java:155` | registration: `registerClientPayload(MessageChunkS2CPayload.class, 0, /*? if >= 1.20.5 {*/ MessageChunkS2CPayload.PACKET_CODEC /*?} else {*/ /*MessageChunkS2CPayload::new *//*?}*/, messageChunkHandler);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/forge/ServerWaypointForgeClient.java:156` | registration: `registerClientPayload(ServerHandshakeS2CPayload.class, 1, /*? if >= 1.20.5 {*/ ServerHandshakeS2CPayload.PACKET_CODEC /*?} else {*/ /*ServerHandshakeS2CPayload::new *//*?}*/, serverHandshakeHandler);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/forge/ServerWaypointForgeClient.java:157` | registration: `registerClientPayload(UploadRequestS2CPayload.class, 2, /*? if >= 1.20.5 {*/ UploadRequestS2CPayload.PACKET_CODEC /*?} else {*/ /*UploadRequestS2CPayload::new *//*?}*/, uploadRequestHandler);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/forge/ServerWaypointForgeClient.java:160` | registration: `private static <P extends ModPayload> void registerClientPayload(` | `idle` |
| `mods/src/main/java/_959/server_waypoint/mixin/AbstractContainerMenuNavigationMixin.java:18` | event-mixin: `@Inject(method = "clicked", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/AbstractContainerMenuNavigationMixin.java:36` | event-mixin: `@Inject(method = "clicked", at = @At("TAIL")/*? if >= 26 {*/, remap = false/*?}*/)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/AbstractContainerMenuNavigationMixin.java:51` | event-mixin: `@Inject(method = "removed", at = @At("HEAD")/*? if >= 26 {*/, remap = false/*?}*/)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/AllayNavigationMixin.java:16` | event-mixin: `@Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/AllayNavigationMixin.java:27` | event-mixin: `@Inject(method = "tick", at = @At("HEAD")/*? if >= 26 {*/, remap = false/*?}*/)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/ArmorStandNavigationMixin.java:21` | event-mixin: `@Inject(` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/ArmorStandNavigationMixin.java:40` | event-mixin: `@Inject(method = "readAdditionalSaveData", at = @At("TAIL")/*? if >= 26 {*/, remap = false/*?}*/)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/CartographyTableMenuNavigationMixin.java:25` | event-mixin: `@Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/ClientPlayNetworkHandlerMixin.java:15` | event-mixin: `@Inject(method = {"sendChat", "sendCommand"}, at = @At("TAIL")/*? if >=26 {*/, remap = false/*?}*/)` | Excluded: Client-only mixin in mods/src/main/resources/server_waypoint-common.mixins.json client list, absent from common/server lists at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/mixin/ClientPlayNetworkHandlerMixin.java:20` | event-mixin: `@Inject(method = "handleLogin", at = @At(value = "TAIL")/*? if >= 26 {*/, remap = false/*?}*/)` | Excluded: Client-only mixin in mods/src/main/resources/server_waypoint-common.mixins.json client list, absent from common/server lists at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/mixin/CompassItemNavigationMixin.java:15` | event-mixin: `@Inject(method = "useOn", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/CraftingMenuNavigationMixin.java:27` | event-mixin: `@Inject(method = "slotChangedCraftingGrid", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/DecoratedPotBlockEntityNavigationMixin.java:26` | event-mixin: `@Inject(method = "setTheItem", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/DecoratedPotBlockEntityNavigationMixin.java:34` | event-mixin: `/*@Inject(method = "load", at = @At("TAIL"))` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/DecoratedPotBlockEntityNavigationMixin.java:39` | event-mixin: `/*@Inject(method = "loadAdditional", at = @At("TAIL"))` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/DecoratedPotBlockEntityNavigationMixin.java:48` | event-mixin: `/*@Inject(method = "loadAdditional", at = @At("TAIL"))` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/DecoratedPotBlockEntityNavigationMixin.java:53` | event-mixin: `@Inject(method = "loadAdditional", at = @At("TAIL"), remap = false)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/DecoratedPotBlockNavigationMixin.java:21` | event-mixin: `/*@Inject(method = "use", at = @At("HEAD"), cancellable = true)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/DecoratedPotBlockNavigationMixin.java:36` | event-mixin: `/*@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/DecoratedPotBlockNavigationMixin.java:52` | event-mixin: `/*@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/DecoratedPotBlockNavigationMixin.java:68` | event-mixin: `@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, remap = false)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/EditBoxTextShadowMixin.java:26` | event-mixin: `@ModifyExpressionValue(` | Excluded: Client-only mixin in mods/src/main/resources/server_waypoint-common.mixins.json client list, absent from common/server lists at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/mixin/EditBoxTextShadowMixin.java:36` | event-mixin: `@WrapOperation(method = /*? >= 26 {*/ "extractWidgetRenderState" /*?} else {*//* "renderWidget" *//*?}*/,` | Excluded: Client-only mixin in mods/src/main/resources/server_waypoint-common.mixins.json client list, absent from common/server lists at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/mixin/EditBoxTextShadowMixin.java:53` | event-mixin: `/*@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",` | Excluded: Client-only mixin in mods/src/main/resources/server_waypoint-common.mixins.json client list, absent from common/server lists at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/mixin/EditBoxTextShadowMixin.java:65` | event-mixin: `/*@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",` | Excluded: Client-only mixin in mods/src/main/resources/server_waypoint-common.mixins.json client list, absent from common/server lists at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/mixin/EditBoxTextShadowMixin.java:74` | event-mixin: `@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",` | Excluded: Client-only mixin in mods/src/main/resources/server_waypoint-common.mixins.json client list, absent from common/server lists at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/mixin/GameHudMixin.java:32` | event-mixin: `/*@Inject(` | Excluded: Client-only mixin in mods/src/main/resources/server_waypoint-common.mixins.json client list, absent from common/server lists at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/mixin/GameHudMixin.java:51` | event-mixin: `/*@Inject(` | Excluded: Client-only mixin in mods/src/main/resources/server_waypoint-common.mixins.json client list, absent from common/server lists at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/mixin/GameRendererMixin.java:24` | event-mixin: `@Inject(` | Excluded: Client-only mixin in mods/src/main/resources/server_waypoint-common.mixins.json client list, absent from common/server lists at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/mixin/GuiItemRenderStateMixin.java:30` | event-mixin: `@Inject(method = "<init>", at = @At("RETURN"))` | Excluded: Client-only mixin in mods/src/main/resources/server_waypoint-common.mixins.json client list, absent from common/server lists at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/mixin/GuiRendererItemAlphaMixin.java:26` | event-mixin: `@ModifyExpressionValue(method = "submitBlitFromItemAtlas",` | Excluded: Client-only mixin in mods/src/main/resources/server_waypoint-common.mixins.json client list, absent from common/server lists at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/mixin/InventoryNavigationMixin.java:28` | event-mixin: `@Inject(method = "removeFromSelected", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/ItemEntityNavigationMixin.java:14` | event-mixin: `@Inject(method = "setItem", at = @At("TAIL")/*? if >= 26 {*/, remap = false/*?}*/)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/ItemEntityNavigationMixin.java:21` | event-mixin: `@Inject(method = "tick", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/ItemEntityNavigationMixin.java:30` | event-mixin: `@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/ItemFrameNavigationMixin.java:17` | event-mixin: `@Inject(method = "interact", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)` | `protection` |
| `mods/src/main/java/_959/server_waypoint/mixin/MinecraftClientMixin.java:17` | event-mixin: `@Inject(` | Excluded: Client-only mixin in mods/src/main/resources/server_waypoint-common.mixins.json client list, absent from common/server lists at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/mixin/OversizedWaypointItemAlphaMixin.java:28` | event-mixin: `@ModifyExpressionValue(method = "blitTexture",` | Excluded: Client-only mixin in mods/src/main/resources/server_waypoint-common.mixins.json client list, absent from common/server lists at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/mixin/PlayerManagerMixin.java:18` | event-mixin: `@Inject(method = "sendLevelInfo", at = @At(value = "HEAD")/*? if >= 26 {*/, remap = false/*?}*/)` | `idle` |
| `mods/src/main/java/_959/server_waypoint/mixin/ServerPlayerEntityMixin.java:45` | event-mixin: `@Inject(method = "die", at = @At("HEAD")/*? if >= 26 {*/, remap = false/*?}*/)` | `idle` |
| `mods/src/main/java/_959/server_waypoint/mixin/ServerPlayerEntityMixin.java:51` | event-mixin: `@Inject(method = "readAdditionalSaveData", at = @At("TAIL")/*? if >= 26 {*/, remap = false/*?}*/)` | `idle` |
| `mods/src/main/java/_959/server_waypoint/mixin/ServerPlayerEntityMixin.java:57` | event-mixin: `@Inject(method = "addAdditionalSaveData", at = @At("TAIL")/*? if >= 26 {*/, remap = false/*?}*/)` | `idle` |
| `mods/src/main/java/_959/server_waypoint/mixin/ServerPlayerEntityMixin.java:67` | event-mixin: `/*@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))` | `idle` |
| `mods/src/main/java/_959/server_waypoint/mixin/ServerPlayerEntityMixin.java:84` | event-mixin: `@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))` | `idle` |
| `mods/src/main/java/_959/server_waypoint/mixin/ServerPlayerEntityMixin.java:95` | event-mixin: `@Inject(method = "restoreFrom", at = @At("TAIL")/*? if >= 26 {*/, remap = false/*?}*/)` | `idle` |
| `mods/src/main/java/_959/server_waypoint/mixin/ServerPlayerEntityMixin.java:136` | event-mixin: `@Inject(` | `idle` |
| `mods/src/main/java/_959/server_waypoint/mixin/WaypointBlockItemAlphaMixin.java:22` | event-mixin: `/*@ModifyExpressionValue(` | Excluded: Client-only mixin in mods/src/main/resources/server_waypoint-common.mixins.json client list, absent from common/server lists at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/mixin/WorldRendererMixin.java:32` | event-mixin: `@Inject(` | Excluded: Client-only mixin in mods/src/main/resources/server_waypoint-common.mixins.json client list, absent from common/server lists at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/mixin/WorldRendererMixin.java:51` | event-mixin: `/^@Inject(` | Excluded: Client-only mixin in mods/src/main/resources/server_waypoint-common.mixins.json client list, absent from common/server lists at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/mixin/WorldRendererMixin.java:61` | event-mixin: `/^@Inject(` | Excluded: Client-only mixin in mods/src/main/resources/server_waypoint-common.mixins.json client list, absent from common/server lists at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/mixin/WorldSessionMixin.java:12` | event-mixin: `@Inject(` | Excluded: Client-only mixin in mods/src/main/resources/server_waypoint-common.mixins.json client list, absent from common/server lists at d9f532f8b5a14c9be4bc47531b05b44fd1dc5773; no server execution path. |
| `mods/src/main/java/_959/server_waypoint/neoforge/ServerWaypointNeoForge.java:137` | background: `this.waypointServer.navigation().tick();` | `idle` |
| `mods/src/main/java/_959/server_waypoint/neoforge/ServerWaypointNeoForge.java:144` | background: `this.waypointServer.navigation().tick();` | `idle` |
| `mods/src/main/java/_959/server_waypoint/neoforge/ServerWaypointNeoForge.java:297` | registration: `registerLegacyNoopClientPayload(MessageChunkS2CPayload.class, 0, MessageChunkS2CPayload::new);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/neoforge/ServerWaypointNeoForge.java:298` | registration: `registerLegacyNoopClientPayload(ServerHandshakeS2CPayload.class, 1, ServerHandshakeS2CPayload::new);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/neoforge/ServerWaypointNeoForge.java:299` | registration: `registerLegacyNoopClientPayload(UploadRequestS2CPayload.class, 2, UploadRequestS2CPayload::new);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/neoforge/ServerWaypointNeoForge.java:301` | registration: `registerLegacyNoopClientPayload(XaerosWorldIdS2CPayload.class, 3, XaerosWorldIdS2CPayload::new);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/neoforge/ServerWaypointNeoForge.java:305` | registration: `private static <T extends _959.server_waypoint.common.network.payload.ModPayload> void registerLegacyNoopClientPayload(` | `idle` |
| `mods/src/main/java/_959/server_waypoint/neoforge/ServerWaypointNeoForge.java:319` | registration: `this.waypointCommand.register(event.getDispatcher());` | `idle` |
| `mods/src/main/java/_959/server_waypoint/neoforge/ServerWaypointNeoForgeClient.java:83` | registration: `event.register(keyBinding);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/neoforge/ServerWaypointNeoForgeClient.java:111` | registration: `ClientWaypointCommand.register(event.getDispatcher());` | `idle` |
| `mods/src/main/java/_959/server_waypoint/neoforge/ServerWaypointNeoForgeClient.java:174` | registration: `registerLegacyClientPayload(channel, MessageChunkS2CPayload.class, 0, MessageChunkS2CPayload::new, messageChunkHandler);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/neoforge/ServerWaypointNeoForgeClient.java:175` | registration: `registerLegacyClientPayload(channel, ServerHandshakeS2CPayload.class, 1, ServerHandshakeS2CPayload::new, serverHandshakeHandler);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/neoforge/ServerWaypointNeoForgeClient.java:176` | registration: `registerLegacyClientPayload(channel, UploadRequestS2CPayload.class, 2, UploadRequestS2CPayload::new, uploadRequestHandler);` | `idle` |
| `mods/src/main/java/_959/server_waypoint/neoforge/ServerWaypointNeoForgeClient.java:179` | registration: `private static <P extends _959.server_waypoint.common.network.payload.ModPayload> void registerLegacyClientPayload(` | `idle` |
| `paper/src/main/java/_959/server_waypoint/PaperScheduler.java:63` | background: `return entity.getScheduler().runAtFixedRate(` | `idle` |
| `paper/src/main/java/_959/server_waypoint/ServerWaypointPaperMC.java:156` | background: `server.getAsyncScheduler().runAtFixedRate(` | `idle` |
| `paper/src/main/java/_959/server_waypoint/ServerWaypointPaperMC.java:165` | registration: `this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {` | `idle` |
| `paper/src/main/java/_959/server_waypoint/ServerWaypointPaperMC.java:166` | registration: `commands.registrar().register(command);` | `idle` |
| `paper/src/main/java/_959/server_waypoint/ServerWaypointPaperMC.java:167` | registration: `commands.registrar().register(waypointCommand.buildKeyGenerationCommand());` | `idle` |
| `paper/src/main/java/_959/server_waypoint/ServerWaypointPaperMC.java:170` | registration: `server.getPluginManager().registerEvents(crossServer, this);` | `idle` |
| `paper/src/main/java/_959/server_waypoint/ServerWaypointPaperMC.java:173` | registration: `server.getPluginManager().registerEvents(chatListener, this);` | `idle` |
| `paper/src/main/java/_959/server_waypoint/ServerWaypointPaperMC.java:174` | registration: `server.getPluginManager().registerEvents(channelRegisterListener, this);` | `idle` |
| `paper/src/main/java/_959/server_waypoint/ServerWaypointPaperMC.java:175` | registration: `server.getPluginManager().registerEvents(navigationListener, this);` | `idle` |
| `paper/src/main/java/_959/server_waypoint/ServerWaypointPaperMC.java:209` | registration: `messenger.registerOutgoingPluginChannel(this, MESSAGE_CHUNK_CHANNEL.ID);` | `idle` |
| `paper/src/main/java/_959/server_waypoint/ServerWaypointPaperMC.java:210` | registration: `messenger.registerOutgoingPluginChannel(this, SERVER_HANDSHAKE_CHANNEL.ID);` | `idle` |
| `paper/src/main/java/_959/server_waypoint/ServerWaypointPaperMC.java:211` | registration: `messenger.registerOutgoingPluginChannel(this, UPLOAD_REQUEST_CHANNEL.ID);` | `idle` |
| `paper/src/main/java/_959/server_waypoint/ServerWaypointPaperMC.java:213` | registration: `messenger.registerIncomingPluginChannel(this, CLIENT_HANDSHAKE_CHANNEL.ID, this);` | `idle` |
| `paper/src/main/java/_959/server_waypoint/ServerWaypointPaperMC.java:214` | registration: `messenger.registerIncomingPluginChannel(this, MESSAGE_CHUNK_CHANNEL.ID, this);` | `idle` |
| `paper/src/main/java/_959/server_waypoint/ServerWaypointPaperMC.java:215` | registration: `messenger.registerIncomingPluginChannel(this, UPLOAD_CHUNK_CHANNEL.ID, this);` | `idle` |
| `paper/src/main/java/_959/server_waypoint/ServerWaypointPaperMC.java:218` | registration: `messenger.registerOutgoingPluginChannel(this, XAEROS_WORLD_ID_CHANNEL.ID);` | `idle` |
| `paper/src/main/java/_959/server_waypoint/ServerWaypointPaperMC.java:219` | registration: `messenger.registerOutgoingPluginChannel(this, XaeroCompatibilityPayloads.XAERO_WORLD_MAP_CHANNEL);` | `idle` |
| `paper/src/main/java/_959/server_waypoint/ServerWaypointPaperMC.java:220` | registration: `messenger.registerOutgoingPluginChannel(this, XaeroCompatibilityPayloads.XAEROLIB_CHANNEL);` | `idle` |
| `paper/src/main/java/_959/server_waypoint/handoff/PaperCrossServerRuntime.java:52` | event-mixin: `@EventHandler(priority = EventPriority.MONITOR) public void onJoin(PlayerJoinEvent event) {` | `idle` |
| `paper/src/main/java/_959/server_waypoint/listener/ChatMessageListenerPaperMC.java:23` | event-mixin: `@EventHandler` | `sharing-icons` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:103` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:147` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:170` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:179` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:192` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:210` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:227` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:242` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:251` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:265` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:284` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:299` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:311` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:331` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:356` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:372` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:393` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:401` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:409` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:427` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:432` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:445` | event-mixin: `@EventHandler(priority = EventPriority.HIGHEST)` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java:575` | background: `ScheduledTask task = this.scheduler.runAtFixedRate(` | `protection` |
| `paper/src/main/java/_959/server_waypoint/listener/PlayerRegisterChannelListener.java:27` | event-mixin: `@EventHandler(priority = EventPriority.MONITOR)` | `sync` |
| `paper/src/main/java/_959/server_waypoint/listener/PlayerRegisterChannelListener.java:41` | event-mixin: `@EventHandler(priority = EventPriority.MONITOR)` | `sync` |
| `paper/src/main/java/_959/server_waypoint/listener/PlayerRegisterChannelListener.java:48` | event-mixin: `@EventHandler(priority = EventPriority.MONITOR)` | `sync` |
| `paper/src/main/java/_959/server_waypoint/listener/PlayerRegisterChannelListener.java:53` | event-mixin: `@EventHandler(priority = EventPriority.MONITOR)` | `sync` |
| `paper/src/main/java/_959/server_waypoint/network/PaperMessageSender.java:53` | background: `plugin.getServer().getAsyncScheduler().runAtFixedRate(` | `sync` |
| `proxy-common/src/main/java/_959/server_waypoint/proxy/pairing/PairingCoordinator.java:56` | background: `timer = Executors.newSingleThreadScheduledExecutor(task -> {` | `transport` |
| `proxy-common/src/main/java/_959/server_waypoint/proxy/pairing/PairingCoordinator.java:59` | background: `timer.scheduleWithFixedDelay(this::expire, 1, 1, TimeUnit.SECONDS);` | `transport` |
| `proxy-common/src/main/java/_959/server_waypoint/proxy/transport/CoordinatorAgent.java:79` | background: `writers = new ScheduledThreadPoolExecutor(Math.min(4, limits.connections()), daemonThreads("server-waypoint-coordinator-heartbeat"));` | `transport` |
| `proxy-common/src/main/java/_959/server_waypoint/proxy/transport/CoordinatorAgent.java:81` | background: `distributions = new ScheduledThreadPoolExecutor(Math.min(4, limits.connections()), daemonThreads("server-waypoint-coordinator-catalog"));` | `transport` |
| `proxy-common/src/main/java/_959/server_waypoint/proxy/transport/CoordinatorAgent.java:83` | background: `distributions.scheduleWithFixedDelay(catalogs::maintain, settings.heartbeatMillis(), settings.heartbeatMillis(), TimeUnit.MILLISECONDS);` | `transport` |
| `proxy-common/src/main/java/_959/server_waypoint/proxy/transport/CoordinatorAgent.java:126` | background: `maintenance = writers.scheduleWithFixedDelay(owner::maintain, 100, 100, TimeUnit.MILLISECONDS);` | `transport` |
| `proxy-common/src/main/java/_959/server_waypoint/proxy/transport/CoordinatorAgent.java:128` | background: `heartbeat = writers.scheduleWithFixedDelay(() -> sendHeartbeat(session), settings.heartbeatMillis(),` | `transport` |
| `proxy-common/src/main/java/_959/server_waypoint/proxy/transport/CoordinatorAgent.java:131` | background: `distribution = distributions.scheduleWithFixedDelay(() -> {` | `transport` |
| `velocity/src/main/java/_959/server_waypoint/velocity/ServerWaypointVelocity.java:37` | registration: `proxy.getChannelRegistrar().register(RESERVED);` | `transport` |
| `velocity/src/main/java/_959/server_waypoint/velocity/ServerWaypointVelocity.java:38` | registration: `proxy.getCommandManager().register(` | `transport` |
