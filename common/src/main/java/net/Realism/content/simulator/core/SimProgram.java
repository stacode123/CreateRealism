package net.Realism.content.simulator.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A compiled, deterministic schedule: the subset of Create/CRN schedule
 * semantics the simulator supports, stripped of NBT and live objects.
 * Condition columns mirror Create's {@code ScheduleRuntime}: columns race in
 * parallel (OR), conditions inside a column complete one after another (AND).
 */
public class SimProgram {

    public enum InstructionKind {
        /** Travel to a station matching {@code pattern}. */
        DESTINATION,
        /** Set throttle to {@code throttle}; no conditions, no movement. */
        THROTTLE,
        /** No effect on movement (rename, reset timings, travel section). */
        NO_OP
    }

    public static class Entry {
        public final InstructionKind kind;
        /** Compiled station-name glob for DESTINATION, else null. */
        public final Pattern pattern;
        /** Raw filter text (for separation history + display), else "". */
        public final String filterText;
        /**
         * All destination globs in priority order (CRN prioritized
         * destinations; a plain destination has exactly one). The engine
         * takes the first reachable filter — earlier ones only get skipped
         * for occupancy when {@link #avoidTrains} is set, mirroring
         * {@code PrioritizedDestinationInstruction.start}.
         */
        public final List<Pattern> patterns;
        /** CRN "avoid trains": prefer a later filter over a busy station. */
        public final boolean avoidTrains;
        /**
         * Steam 'n' Rails waypoint: route through the station without
         * stopping — no braking toward it, no dwell, schedule advances the
         * moment it is passed.
         */
        public final boolean waypoint;
        /** Throttle fraction for THROTTLE, else 1. */
        public final double throttle;
        /**
         * CRN section identity active at this entry (from the last
         * travel-section instruction before it), as opaque comparison
         * tokens; null = default section. Drives SAME_LINE/SAME_CATEGORY
         * separation.
         */
        public String lineToken;
        public String categoryToken;
        public final List<List<SimCondition>> columns = new ArrayList<>();

        private Entry(InstructionKind kind, List<Pattern> patterns, String filterText,
                      boolean avoidTrains, boolean waypoint, double throttle) {
            this.kind = kind;
            this.patterns = patterns;
            this.pattern = patterns == null || patterns.isEmpty() ? null : patterns.get(0);
            this.filterText = filterText;
            this.avoidTrains = avoidTrains;
            this.waypoint = waypoint;
            this.throttle = throttle;
        }

        public static Entry destination(String filter) {
            return new Entry(InstructionKind.DESTINATION,
                    List.of(SimGlob.compile(filter)), filter, false, false, 1);
        }

        public static Entry destinationPrioritized(List<String> filters, boolean avoidTrains) {
            return new Entry(InstructionKind.DESTINATION,
                    filters.stream().map(SimGlob::compile).toList(),
                    filters.get(0), avoidTrains, false, 1);
        }

        public static Entry waypointDestination(String filter) {
            return new Entry(InstructionKind.DESTINATION,
                    List.of(SimGlob.compile(filter)), filter, false, true, 1);
        }

        public static Entry throttle(double fraction) {
            return new Entry(InstructionKind.THROTTLE, null, "", false, false, fraction);
        }

        public static Entry noOp() {
            return new Entry(InstructionKind.NO_OP, null, "", false, false, 1);
        }
    }

    public final List<Entry> entries = new ArrayList<>();
    public boolean cyclic = true;
}
