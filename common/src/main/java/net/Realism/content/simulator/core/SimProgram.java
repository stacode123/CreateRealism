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

        private Entry(InstructionKind kind, Pattern pattern, String filterText, double throttle) {
            this.kind = kind;
            this.pattern = pattern;
            this.filterText = filterText;
            this.throttle = throttle;
        }

        public static Entry destination(String filter) {
            return new Entry(InstructionKind.DESTINATION, SimGlob.compile(filter), filter, 1);
        }

        public static Entry throttle(double fraction) {
            return new Entry(InstructionKind.THROTTLE, null, "", fraction);
        }

        public static Entry noOp() {
            return new Entry(InstructionKind.NO_OP, null, "", 1);
        }
    }

    public final List<Entry> entries = new ArrayList<>();
    public boolean cyclic = true;
}
