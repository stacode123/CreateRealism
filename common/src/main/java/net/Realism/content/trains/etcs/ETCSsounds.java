package net.Realism.content.trains.etcs;

import net.Realism.RealismSounds;
import net.Realism.config.RealismConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

public class ETCSsounds {
    private static boolean isWarningLooping = false;
    private static long lastWarningTime = 0;
    private static final long WARNING_INTERVAL = 1584;

    public static void playWarningSound() {
        if (RealismConfig.CLIENT.ETCSSounds.get()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                mc.level.playSound(
                        Minecraft.getInstance().player, // Only this player will hear the sound
                        Minecraft.getInstance().player.getX(),
                        Minecraft.getInstance().player.getY(),
                        Minecraft.getInstance().player.getZ(),
                        RealismSounds.ETCS_WARNING.get(),
                        SoundSource.MASTER,
                        1.0F, // Volume
                        1.0F  // Pitch
                );
            }
        }
    }
    public static void playBeepSound() {
        if (RealismConfig.CLIENT.ETCSSounds.get()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                if (Minecraft.getInstance().player != null) {
                    mc.level.playSound(
                            Minecraft.getInstance().player, // Only this player will hear the sound
                            Minecraft.getInstance().player.getX(),
                            Minecraft.getInstance().player.getY(),
                            Minecraft.getInstance().player.getZ(),
                            RealismSounds.ETCS_BEEP.get(),
                            SoundSource.MASTER,
                            1.0F, // Volume
                            1.0F  // Pitch
                    );
                }
            }
        }
    }

    public static void startWarningLoop() {
        if (isWarningLooping) {
            return; // Already looping
        }
        isWarningLooping = true;
        lastWarningTime = System.currentTimeMillis();
        playWarningSound(); // Play immediately when starting
    }

    public static void stopWarningLoop() {
        isWarningLooping = false;
    }

    // Call this method every frame or tick to manage the loop
    public static void updateWarningLoop() {
        if (isWarningLooping && System.currentTimeMillis() - lastWarningTime >= WARNING_INTERVAL) {
            playWarningSound();
            lastWarningTime = System.currentTimeMillis();
        }
    }
}
