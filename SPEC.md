# Advanced Schedule — Feature Specification

## Context

Create Realism's `Advanced-Schedule` fork (branch of `1.20.1-C6`, MC 1.20.1, Forge + Fabric) contains a January 2026 prototype of an upgraded schedule item: a custom editor GUI, a simplified track graph, and a toy train simulator. The vision, refined through a design interview on 2026-07-29, is a schedule item that does everything Create's does, **plus**: simulate the schedule against the whole rail network, report conflicts with other trains (junctions, headways, deadlocks, platforms), and open a graph map by selecting a track in-world — all while keeping full compatibility with Create Railways Navigator (CRN) schedule options such as Train Separation.

Exploration of the prototype and of Create 6.0.8 / CRN sources established hard constraints that shaped every decision below. The prototype's GUI bridge and simulator are treated as **concept references, mostly to be rebuilt**; the decisions record explains why.

## 1. Decisions record (interview outcomes)

| Topic | Decision |
|---|---|
| Base branch | Stay on `Advanced-Schedule` (old 1.20.1 base); no rebase/port as part of this feature |
| GUI architecture | **Subclass Create's `ScheduleScreen`** (required for CRN compat); DragonLib windows layered on top for new panels |
| Item role | **Full replacement** for Create's schedule item (conductor-compatible) |
| Simulation model | **Static timetable projection** with a start-time selector |
| Sim compute | **Server-side, async thread**; compact results synced to client |
| Physics fidelity | **Kinematic approximation** (Realism accel + per-edge speed caps) |
| Speed constraints (2026-07-29, M1) | Curvature-derived speeds are **advisory, display-only** — Create trains ignore curve radius. The simulator enforces only **Tramways sign limits** (when installed), train max speed and throttle instructions |
| Phantom train | Settings window with **acceleration picker + carriage amount** |
| Phase anchoring | **Snapshot live progress** for non-Time-of-Day trains; conflicts involving them carry a **"non-deterministic" notice** |
| Unpredictable wait conditions | **Not simulated.** Network trains containing them are **excluded** (listed with reason). If the edited schedule contains one: **refuse to simulate + explain** which condition to remove |
| Conflict types | Section/junction contention, deadlocks, headway violations, platform conflicts — **all four** |
| Results UI | **Conflict list panel + time-distance diagram** (no map playback/animation) |
| Graph viewer | **Right-click a track with the item → map** focused on that edge; right-click elsewhere → editor |
| Editor QoL beyond Create parity | **Simulated times on cards** only (reorder/duplicate/remove are inherited Create features) |
| Sim horizon | **User-selected duration, server-config cap + per-player cooldown**; must work with day-length/time-speed mods (mirror CRN's time handling) |

## 2. Hard constraints discovered (must-not-violate)

1. **CRN's `instanceof ScheduleScreen` gate.** CRN's Train Separation, Travel Section, and Prioritized Destination render only a "Configure" button whose handler requires `Minecraft.getInstance().screen instanceof ScheduleScreen` and reads the screen's private `onEditorClose` via accessor mixin. The editor **must be a real subclass** of `com.simibubi.create.content.trains.schedule.ScheduleScreen`, and must hand instructions a **real `ModularGuiLineBuilder`** (CRN reads its private `target/font/x/y` via `ModularGuiLineBuilderAccessor`). The prototype's throwaway-builder bridge (`ModularDragonGuiBuilder`) silently kills these — do not use it for editing.
2. **`ScheduleEditPacket` rejects non-vanilla items** (`AllItems.SCHEDULE.isIn(...)` guard). We own the save path: override `removed()` and send our own validated packet.
3. **`ScheduleRuntime.returnSchedule()` always fabricates a vanilla schedule item.** Without intervention, a conductor hands back a *vanilla* schedule. Requires a marker + mixin (see §4.2).
4. **NBT round-trip must preserve unknown data.** `ScheduleWaitCondition.fromTag` returns `null` for unknown ids (NPE risk Create-side; CRN patches it), unknown instructions collapse to blank `DestinationInstruction`. Rule: keep the live `IScheduleInput` objects, save via their own `write()`, never rebuild schedules through our own DTOs; tolerate `null` conditions when compiling for sim (skip + warn).
5. **NBT types are key-specific** (`Threshold` is a String, CRN's `TrainFilter`/`TimeSource` are bytes, categories are UUID int-arrays). Another reason to never re-serialize through generic widget state.
6. **`RNetworking` wire IDs are positional** — append new packets at the end, bump `VERSION`.
7. **CRN separation semantics:** `train_separation` reports `totalWaitTicks() == 0` and gates departure via delayed conditions comparing against departure history — the simulator must reproduce this with its own sim-internal departure history, or its predictions will disagree with in-game behavior.

## 3. What is salvaged vs rebuilt from the prototype

**Deleted:** both ~1,100-line dead `AdvancedScheduleScreen` copies (common + forge) and both copied `AdvancedScheduleMenu`s (they are unreachable Create forks); `SaveAdvancedSchedule` (unvalidated whole-tag overwrite from client — a vuln); `content/gui/schedule/*` editing widgets (superseded by subclassing, incl. `ModularDragonGuiBuilder`/`DragonGuiConverter`/`EditWindow`); simulator v1 (`content/simulator/*` — point-mass, hardcoded physics, "Zakopane 1" test route); dead NBT persistence in `content/graph/*`; the no-op `ScheduleItemEntityInteractionMixin`s.

**Concepts kept (reimplemented):** graph collapse to junction/signal/station nodes; chain-signal edges reserved atomically; stations as offsets along edges; directed edge pairs with `opposite` links; edge-reservation-driven blocking. `viewer.html` + JSON export survive only as a debug tool behind a config flag.

**Kept as-is:** `AdvancedScheduleItem extends ScheduleItem` (with fixes), `AllRealismItems`/`AllMenuTypes` registration skeleton, `TimeOfDayRealistic`, the `/realism` command shell (rewritten contents).

## 4. Design

### 4.1 Editor screen
- `AdvancedScheduleScreen2` (final name TBD) **extends Create's `ScheduleScreen`**, opened via our own `MenuType` whose menu **extends Create's `ScheduleMenu`** (drop the copies). All entry editing, ghost slots, destination suggestions, card remove/duplicate/move-up/move-down come from Create; CRN mixins apply to the superclass and keep working.
- Overrides: `removed()` → send our validated save packet (never `ScheduleEditPacket`).
- Added chrome around Create's layout: buttons for **Simulate**, **Map**, **Train setup**; results surface as DragonLib overlay windows (`DLWindow.openWindow` on top of the screen — the exact pattern CRN itself uses, so it is proven compatible).
- **Times on cards:** after a sim run, render each entry's simulated arrival/departure time onto its card (overlay drawn after `super.render`). Cleared/greyed when the schedule is edited after the run (results are stamped with a schedule content hash).

### 4.2 Item lifecycle (full replacement)
- Assets: item texture, model, `item.realism.advanced_schedule` lang (en_us + zh_cn).
- `use()` routing: targeting a track block → open graph map focused on that edge (C2S request → S2C graph payload → map screen); otherwise → open editor menu (proper `openScreen`, not the prototype's S2C-packet hack).
- Conductor round-trip: `AdvancedScheduleItem.handScheduleTo` marks the train's `ScheduleRuntime` (extend the existing `IScheduleRuntimeMixin` interface); a small mixin on `returnSchedule()` emits the advanced item instead of `AllItems.SCHEDULE` when marked. Auto-schedule behavior unchanged.
- Save packet (C2S): server verifies the sender holds an `AdvancedScheduleItem` in the **main hand** (no `getUsedItemHand()`; main-hand-only because Create's ghost menu itself is bound to the selected main-hand stack via `stillValid`), deserializes via `Schedule.fromTag`, re-serializes via `schedule.write()` (sanitizes; preserves unknown `Data` keys through live objects), enforces a size cap, writes only the `Schedule` sub-tag (never the whole item tag).

### 4.3 Graph model v2 (`content/graph`, rebuilt)
- Nodes: junctions (degree ≠ 2), signal boundaries, dead ends — with **deterministic IDs** (position-derived, not `UUID.randomUUID()`), dimension tag, world `Vec3`.
- Edges: directed pairs with `opposite`; `double` length; **advisory curve-speed profile** computed at translation (curvature-based, reusing Realism's track-speed math from the TrackOverlay/BezierConnection code — display only, since Create trains ignore curvature) plus **Tramways sign limits** via `TramwaysCompat` when installed (the only edge caps the simulator enforces); entry/chain signal typing; station list with platform identity (= `GlobalStation`); **diamond crossings (`TrackEdgeIntersection`) captured as exclusive point resources** — they are exactly the junction conflicts this feature is about, and the prototype dropped them.
- Use Create's **public API** (`TrackGraph.getNodes()`/`locateNode`/`getConnectionsFrom`/`getPoints`) — the prototype's reflection is avoidable and silently fails.
- Lifecycle: translated server-side on demand (map open / sim request), cached per `TrackGraph`, invalidated by a cheap dirty hook on track graph modification (fallback: staleness timeout). Snapshot on the server thread, heavy assembly async. Server-config node cap with a clear "network too large" error.
- All prototype math bugs (signal-distance sign error, station offset double-transform, chain propagation bleeding backwards, O(E²)/parallel-edge opposite-linking) die with the rewrite; translation must be **deterministic** (sorted iteration, no hash-order dependence).

### 4.4 Simulator v2 (`content/simulator`, rebuilt)
- **Pure, MC-independent core** operating on immutable snapshot inputs — unit-testable with JUnit on synthetic graphs; deterministic (sorted train order, fixed tick step, no wall clock).
- Inputs: graph v2; per-train `SimTrain` (length + acceleration derived from carriage count via Realism's acceleration formula; max speed; compiled schedule program; phase anchor); sim start day-time; horizon.
- Trains are **1-D intervals** (length-aware occupancy — a long train blocks a junction honestly), moving with accel/brake ramps toward `min(train max speed, Tramways sign limits ahead, braking curve to next stop or blocked section)` — curvature-advisory speeds are not enforced (they don't affect real trains); signal-block sections reserved as units, chain-signal chains atomically; crossings are exclusive point resources.
- Condition support (deterministic set): `ScheduledDelay`, `TimeOfDay`, `TimeOfDayRealistic`, CRN `dynamic_delay` (min-dwell rule), CRN `train_separation` (gated via sim-internal departure history honoring `Ticks`/`TrainFilter`/`StationFilter`). Everything else (cargo/fluid thresholds, redstone, player count, idle, packages, `powered`, `unloaded`) is **unpredictable → the owning train is excluded** and listed with the reason. Instructions: `destination` (Dijkstra shortest-path by distance, direction-legal, no U-turns), `throttle` (caps speed), `rename`/`reset_timings` (no movement effect), CRN `prioritized_destination` (use primary filter; note in results), `travel_section` (no movement effect).
- Phase anchors: Time-of-Day-conditioned trains lock to the clock; all others snapshot their live `ScheduleRuntime` progress + position at request time. **Unscheduled/manual trains become static obstacles** at their snapshot positions (listed as such).
- Phantom train: compiled from the edited item's schedule; starts at its first destination station at sim start; parameters from the **Train setup window** (acceleration picker mirroring Realism's modes, carriage count). If the edited schedule contains an unpredictable condition, the Simulate button disables with a message naming the offending condition(s).
- **Time handling:** all times are game-time ticks via `Level.getDayTime` deltas — never assume a 24000-tick day. Before implementation, read how CRN's `ETimeSource`/time utilities handle modified day length and mirror that (interview directive).
- Budgeting: horizon selector in GUI, server-config max, per-player cooldown, one concurrent sim per player, dedicated worker thread, cancellation on disconnect.

### 4.5 Conflict detection
Derived from the sim event stream; every conflict record = {type, tick window, resource + world position, involved trains, determinism flag}:
- **Section/junction contention:** overlapping demands on an exclusive resource (signal section, crossing); includes "held > threshold" wait events.
- **Deadlock:** cycle detection on the wait-for graph among blocked trains; report the cycle's members and resources.
- **Headway:** consecutive trains through a section closer than the threshold (config default, per-run override in the setup window). CRN separation conditions gate departures in-sim; violations are judged against the stricter of the CRN condition and the threshold.
- **Platform:** overlapping dwell windows at the same `GlobalStation`.
- Any conflict involving a live-snapshot-anchored train (or static obstacle) carries the **"non-deterministic — depends on current network state"** notice.

### 4.6 Networking (append-only, bump `RNetworking.VERSION`)
- `AdvancedScheduleSavePacket` (C2S) — §4.2.
- `RequestGraphViewPacket` (C2S: edge/world pos) → `GraphViewPacket` (S2C: serialized subgraph, node-capped).
- `RequestSimulationPacket` (C2S: sim settings + schedule content hash) → `SimulationResultPacket` (S2C: conflict list, excluded-train list with reasons, per-train trajectories downsampled to keypoints — station events + uniform stride — for the diagram, graph version stamp).

### 4.7 Map viewer
2D top-down screen: edges as chords (bezier polyline sampling optional later), junction/signal/station markers, pan/zoom, per-dimension views, opened focused on the in-world-selected edge. After a sim: conflict badges; clicking a conflict list entry focuses the map. No train animation (decided). Phantom route highlighted.

### 4.8 Time-distance diagram
Classic train graph in a DragonLib window: Y = distance along the phantom train's route corridor, X = game time (day-length aware); one polyline per train traversing corridor edges from the downsampled trajectories; conflict markers at their time/place; hover tooltips (train, arrival/departure); pan/zoom on the time axis.

## 5. Edge cases & rules
- Null conditions (mod removed) → skip + warn during compile; never crash; never drop on re-save (live-object rule).
- Empty schedules: guard `savedProgress` clamp (Create's `clamp(0,0,-1)` hazard); Simulate disabled for empty schedules.
- Cyclic and non-cyclic schedules both simulate; non-cyclic trains park at end (become static occupancy for the remainder).
- Inter-dimensional edges: traversal time from edge length; map/diagram render per dimension.
- Results are stamped with graph version + schedule hash; edits or track changes mark them stale in the UI.
- Two players editing one train's schedule: last save wins (vanilla Create behavior), sim results are per-player.
- Off-hand item works everywhere main hand does, except opening the editor (main-hand only — Create's own `ScheduleScreen`/`ScheduleEditPacket` are hardcoded to the main hand and we inherit that). Map right-click and conductor hand-off work from either hand.

## 6. Config
**Server:** max sim horizon, sim cooldown, max concurrent sims, graph node cap, default headway threshold, wait-event threshold, debug JSON/SVG export toggle. **Client:** diagram/map colors only if trivial — keep minimal.

## 7. Milestones
1. **M1 — Graph v2 + map viewer:** translator rebuild (speed caps, crossings, determinism, cache), in-world right-click → map. Verifiable standalone. ✅ **Done 2026-07-29** (map later gained: clicked-line resolution via `TrackGraphHelper` axis walk, axis-aware highlight, nearby separate networks rendered as dimmed context).
2. **M2 — Editor + item lifecycle:** `ScheduleScreen` subclass, menu, save packet, `returnSchedule` mixin, assets; delete dead prototype code. Verify parity + CRN editing (install CRN `1.20.1-beta-0.8.0-C6` or newer 1.20.1 build) + conductor round-trip. ✅ **Done 2026-07-30** on Forge + Fabric. Extra scope discovered: blaze-burner conductors and station auto-schedule gate items by registry entry (`AllItems.SCHEDULE.isIn`) — widened via `ConductorBlockInteractionMixin` + `StationBlockEntityMixin`. CRN `1.20.1-beta-0.9.1-C6` is a dev-only runtime dep (needs `modCompileOnly` too so loom remaps its Create calls; required DragonLib bump to `beta-3.0.28` via Modrinth maven — the old DragonLib maven is dead).
3. **M3 — Simulator core:** MC-free engine + JUnit fixtures (incl. determinism test: identical inputs → identical results), schedule compilation, exclusion rules, phantom setup window, async server harness + packets. ✅ **Done 2026-07-30.** Engine (`content/simulator/core`, zero MC imports, 8 JUnit tests via `:common:test`) mirrors Create 6.0.8 mechanics: bang-bang `v²/2a` control, per-tick section reservations in train order, chain signals atomic, occupancy transitive across crossings (`SignalEdgeGroup.intersectingResolved`), turns capped at train turn speed (curvature caps are display-only — in-game trains don't obey them), conditions replicate `ScheduleRuntime` column semantics + CRN separation departure-history gates. Translator now emits a server-only `SimTopology` (edge tangents for the 7/8-dot turn rule, directional sign caps, Create-microedge→collapsed-edge mapping for live-train placement). Implementation notes vs plan: trajectory samples stay server-side until M5's diagram needs them; day-time rate is gamerule-based (doDaylightCycle 0/1) — measuring modded rates deferred to M6; CRN dynamic_delay projects full dwell (its live-delay baseline doesn't exist in a static projection); CRN line/category separation resolved for real: section identity (line/category tokens) compiled from travel-section instructions per entry (UUID or genMD5Uuid-of-name, mirroring ScheduleSection/DepartureHistory), departure history keyed by line/category/name; sim departure history starts empty at sim start. Perf (2026-07-30, after an 80k-node/1k-train server hit the wall-clock budget): static-occupancy baseline for obstacles/parked trains, precomputed predecessor table, inverted station-coverage penalty build + per-pattern target cache, Euclidean bound-pruned Dijkstra (incumbent-seeded reverse search; exploration order and results provably unchanged), and a path-failure memo (penalties never sever reachability, so a failed search is permanent per position+targets — Create's 40-tick retry storm becomes a hash lookup). 28× on the synthetic benchmark (`SimEngineBenchmarkTest`, opt-in via `-Dsim.benchmark=true`, result digest guards semantics). Wall budget now configurable (`Sim Max Wall Seconds`); each result reports its compute breakdown in the notes panel.
4. **M4 — Conflicts:** all four detectors, conflict list panel, non-determinism notices.
5. **M5 — Presentation:** time-distance diagram, times on cards, map conflict badges.
6. **M6 — Polish:** config, lang (en_us + zh_cn), `/realism` debug commands, CHANGELOG, wiki notes.

## 8. Verification
- Per-milestone: `./gradlew :forge:runClient` first, wait for user's in-game confirmation, then `:fabric:runClient` (established workflow). CRN + Create installed for M2+.
- Staged conflict scenarios on a test world: two trains ↔ single track (section conflict); a loop with opposing circulation (deadlock); two same-route trains 30 s apart with a 60 s separation (headway); two schedules into one station (platform).
- Simulator core: JUnit on synthetic graphs (no Minecraft), plus the determinism test.
- Compat regression: schedule authored in our editor must load identically in Create's vanilla editor (temporarily move NBT onto a vanilla item) and drive a real train via conductor hand-off; CRN Configure windows must open and persist their settings through our save path.
