package net.Realism.content.simulator;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import net.Realism.content.simulator.core.SimCondition;
import net.Realism.content.simulator.core.SimProgram;
import net.Realism.content.simulator.core.SimTrainSpec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Captures every live train on one track graph as simulator input. Must run
 * on the server thread. Trains whose behavior a static projection cannot
 * predict (manual, paused, unsupported conditions, unmappable position)
 * become static obstacles and are reported with a reason.
 */
public class NetworkSnapshotter {

    public record Excluded(String trainName, String translationKey, String detail) {}

    public record Snapshot(List<SimTrainSpec> specs, List<Excluded> excluded) {}

    public static Snapshot snapshot(Level level, TrackGraph graph, SimTopology topology) {
        List<Train> trains = new ArrayList<>();
        for (Train train : Create.RAILWAYS.sided(level).trains.values())
            if (train.graph != null && train.graph.id.equals(graph.id) && !train.carriages.isEmpty())
                trains.add(train);
        trains.sort(Comparator.comparing(train -> train.id.toString()));

        List<SimTrainSpec> specs = new ArrayList<>();
        List<Excluded> excluded = new ArrayList<>();
        for (Train train : trains) {
            SimTrainSpec spec = snapshotTrain(train, topology, excluded);
            if (spec != null)
                specs.add(spec);
        }
        return new Snapshot(specs, excluded);
    }

    private static SimTrainSpec snapshotTrain(Train train, SimTopology topology, List<Excluded> excluded) {
        String name = train.name.getString();
        TravellingPoint head = train.carriages.get(0).getLeadingPoint();
        if (head.node1 == null || head.node2 == null || head.edge == null) {
            excluded.add(new Excluded(name, "realism.sim.exclude.unmappable", ""));
            return null;
        }
        SimTopology.Location location =
                topology.locate(head.node1.getNetId(), head.node2.getNetId(), head.position);
        if (location == null) {
            excluded.add(new Excluded(name, "realism.sim.exclude.unmappable", ""));
            return null;
        }

        double length = 2;
        for (Carriage carriage : train.carriages)
            length += carriage.bogeySpacing;
        for (int spacing : train.carriageSpacing)
            length += spacing;

        ScheduleRuntime runtime = train.runtime;
        Schedule schedule = runtime.getSchedule();

        SimProgram program = null;
        List<String> notices = new ArrayList<>();
        String obstacleReason = null;
        String obstacleDetail = "";

        if (train.derailed) {
            obstacleReason = "realism.sim.exclude.derailed";
        } else if (schedule == null) {
            obstacleReason = "realism.sim.exclude.no_schedule";
        } else if (runtime.paused) {
            obstacleReason = runtime.completed
                    ? "realism.sim.exclude.completed"
                    : "realism.sim.exclude.paused";
        } else {
            ScheduleCompiler.CompileResult compiled = ScheduleCompiler.compile(schedule);
            if (!compiled.clean()) {
                ScheduleCompiler.Problem problem = compiled.problems().get(0);
                obstacleReason = problem.translationKey();
                obstacleDetail = problem.detail();
            } else {
                program = compiled.program();
                notices.addAll(compiled.notices());
                if (!hasTimeAnchor(program))
                    notices.add("realism.sim.notice.snapshot_anchor");
            }
        }

        SimTrainSpec spec = new SimTrainSpec(train.id.toString(), name, length,
                train.acceleration(), train.maxSpeed(), train.maxTurnSpeed(),
                Math.max(0.05, Math.min(1, train.throttle)), program,
                location.edgeId(), location.offset());
        spec.canReverse = train.doubleEnded;
        spec.initialSpeed = Math.min(Math.abs(train.speed), train.maxSpeed());
        spec.notices.addAll(notices);

        if (program == null) {
            excluded.add(new Excluded(name, obstacleReason, obstacleDetail));
            spec.notices.add("realism.sim.notice.static_obstacle");
            return spec;
        }

        int entryCount = program.entries.size();
        if (entryCount == 0) {
            excluded.add(new Excluded(name, "realism.sim.exclude.no_schedule", ""));
            return spec;
        }
        spec.startEntry = Math.max(0, Math.min(runtime.currentEntry, entryCount - 1));
        if (runtime.state == ScheduleRuntime.State.POST_TRANSIT) {
            spec.startWaiting = true;
            int columns = runtime.conditionProgress.size();
            spec.startColumnProgress = new int[columns];
            spec.startColumnElapsed = new int[columns];
            for (int i = 0; i < columns; i++) {
                spec.startColumnProgress[i] = runtime.conditionProgress.get(i);
                CompoundTag context = i < runtime.conditionContext.size()
                        ? runtime.conditionContext.get(i) : new CompoundTag();
                spec.startColumnElapsed[i] = context.getInt("Time");
            }
        } else if (train.navigation != null && train.navigation.destination != null) {
            spec.resumeDestination = train.navigation.destination.id;
        }
        return spec;
    }

    private static boolean hasTimeAnchor(SimProgram program) {
        for (SimProgram.Entry entry : program.entries)
            for (List<SimCondition> column : entry.columns)
                for (SimCondition condition : column)
                    if (condition instanceof SimCondition.TimeOfDay
                            || condition instanceof SimCondition.TimeOfDayRealistic)
                        return true;
        return false;
    }
}
