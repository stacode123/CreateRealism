package net.Realism.trains;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.Realism.trains.SignalFinder.*;



public class ETCS {
    public static void init(GuiGraphics graphics, Train train, Font font){
        RenderSystem.setShaderTexture(0, new ResourceLocation("realism:textures/signal.png"));
        graphics.blit(new ResourceLocation("realism:textures/signal.png"), 250, -50, 0, 0, 32, 62, 32, 62);
        
        // Use the improved method that automatically detects direction
        SignalScanResult s = SignalFinder.scanAheadForSignals(train, 1000);
        
        // Add movement direction information to display
        String direction = train.speed < 0 ? "Backward" : "Forward";
        graphics.drawString(font, "Direction: " + direction + " (Speed: " + train.speed + ")", 10, -90, 0xFFFFFF);
        graphics.drawString(font, "Distance to signal: " + s.getDistanceToClosestOccupiedSignal()+ " Status: " + s.hasBlockedPath(), 10, -70, 0xFFFFFF);
        
        // Display all detected signals for debugging
        int yOffset = -50;
        for (SignalInfo signal : s.getSignals()) {
            String signalInfo = String.format("Signal at %.1fm (occupied: %s)", signal.getDistance(), signal.isOccupied());
            graphics.drawString(font, signalInfo, 10, yOffset, 0xFFFFFF);
            yOffset += 15;
        }
    };
}
