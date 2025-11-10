package net.Realism.debug;

import net.Realism.config.RealismConfig;
import net.minecraft.client.Minecraft;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RealismDebuger {
    private static final RealismDebuger INSTANCE = new RealismDebuger();
    private final Queue<String> pendingMessages = new LinkedList<>();
    private final Set<String> knownMessages = new HashSet<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private boolean isRunning = false;
    private boolean hasNewData = false;

    private RealismDebuger() {
        // Private constructor for singleton
        startLogging(); // Auto-start the scheduler
    }

    public static RealismDebuger getInstance() {
        return INSTANCE;
    }

    public void startLogging() {
        if (!isRunning) {
            isRunning = true;
            scheduler.scheduleAtFixedRate(this::logDebugInfo, 0, 1, TimeUnit.SECONDS);
        }
    }

    public void stopLogging() {
        if (isRunning) {
            scheduler.shutdown();
            isRunning = false;
        }
    }

    public void addAccelerationDebugInfo(float acceleration, int carriageCount, int locomotiveCount) {
        // Only collect debug info if debug mode is enabled
        if (RealismConfig.CLIENT.debugMode.get()) {
            String message = String.format("Train acceleration: %.5f | Carriages: %d | PowerCars: %d",
                    acceleration, carriageCount, locomotiveCount);

            synchronized (pendingMessages) {
                if (!knownMessages.contains(message)) {
                    // This is a new message we haven't seen before
                    pendingMessages.offer(message);
                    knownMessages.add(message);
                    hasNewData = true;

                    // Limit the size of knownMessages to prevent memory issues
                    if (knownMessages.size() > 100) {
                        // Since we can't easily remove oldest elements from a HashSet,
                        // clear half of the history when we hit the limit
                        knownMessages.clear();
                        knownMessages.addAll(pendingMessages);
                    }
                }
            }
        }
    }

    public void addDebugMessage(String message) {
        // Only collect debug info if debug mode is enabled
        if (RealismConfig.CLIENT.debugMode.get()) {
            synchronized (pendingMessages) {
                if (!knownMessages.contains(message)) {
                    // This is a new message we haven't seen before
                    pendingMessages.offer(message);
                    knownMessages.add(message);
                    hasNewData = true;
                }
            }
        }
    }

    private void logDebugInfo() {
        // Check conditions: debug mode is on, there's a world loaded, and we have new data
        if (!RealismConfig.CLIENT.debugMode.get() || Minecraft.getInstance().level == null || !hasNewData) {
            return;
        }

        synchronized (pendingMessages) {
            if (!pendingMessages.isEmpty()) {
                System.out.println("=== REALISM DEBUG INFO ===");
                for (String message : pendingMessages) {
                    System.out.println(message);
                }
                pendingMessages.clear();
                System.out.println("======================");
                hasNewData = false;
            }
        }
    }
}