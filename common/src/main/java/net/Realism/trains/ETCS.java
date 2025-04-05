package net.Realism.trains;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.Realism.trains.SignalFinder.*;



public class ETCS {
    boolean backward = false;
    public Train train;
    public SignalScanResult previousSignalScanResult;
    boolean previousBackward;

    public ETCS(Train train) {
        this.train = train;
    }

    public void render(GuiGraphics graphics) {
        PoseStack posestack = graphics.pose();
        posestack.pushPose();
        posestack.scale(0.25f, 0.25f, 0.25f);
        Font font = Minecraft.getInstance().font;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getHeight();
        //RenderSystem.setShaderTexture(0, new ResourceLocation("realism:textures/signal.png"));
        //graphics.blit(new ResourceLocation("realism:textures/signal.png"), 250, -50, 0, 0, 32, 62, 32, 62);
        
        // Use the improved method that automatically detects direction
        if (train.speed < 0) {
            previousBackward = backward;
            backward = true;
        } else if (train.speed > 0) {
            previousBackward = backward;
            backward = false;
        }

        int xPos = (screenWidth * 4) - 536;  // 10 scaled pixels from right edge
        int yPos = 0;
        RenderSystem.setShaderTexture(0, new ResourceLocation("realism:textures/etcs.png"));
        graphics.blit(new ResourceLocation("realism:textures/etcs.png"),
                xPos, yPos,   // screen position
                0, 0,         // texture position (top left of texture)
                536, 401,   // width and height to render
                536, 401);  // texture width and height
        posestack.pushPose();

        // Move to the anchor point for rotation
        posestack.translate( xPos+ 167,  yPos + 130, 0);

        //-151.5
        //30
        //124
        float rotationDegrees = 0f;
        float rotationRadians = 0f;
        if(Math.abs(train.speed*20*3.6f) <= 160f){
            rotationDegrees = -151.5f+ (1.134f) * (float) Math.abs(train.speed*20*3.6f);
            // Apply rotation around anchor point
            rotationRadians = rotationDegrees * (float)(Math.PI / 180);
        }
        else{
            rotationDegrees = 30f + ((0.671f) * ((float) Math.abs(train.speed*20*3.6f)-160f));
            rotationRadians = rotationDegrees * (float)(Math.PI / 180);

        }
        // Add this for debugging
        posestack.mulPose(Axis.ZP.rotation(rotationRadians));
        // Move back so the image renders with the anchor point at the specified position
        posestack.translate(-24, -89, 0);

        // Render the image
        RenderSystem.setShaderTexture(0, new ResourceLocation("realism:textures/etcshand.png"));
        graphics.blit(new ResourceLocation("realism:textures/etcshand.png"),
                0,
                0,   // screen position (now relative to transformed coordinates)
                0, 0,   // texture position
                49, 112,   // width and height to render
                49, 112);  // texture width and height
        posestack.popPose();

        renderETCSlimits(graphics, train,posestack,xPos+10, yPos+10);

//        // Add movement direction information to display
//        String direction = train.speed < 0 ? "Backward" : "Forward";
//        graphics.drawString(font, "Direction: " + direction + " (Speed: " + train.speed + ")", 10, -90, 0xFFFFFF);
//        graphics.drawString(font, "Distance to signal: " + s.getDistanceToClosestOccupiedSignal()+ " Status: " + s.hasBlockedPath(), 10, -70, 0xFFFFFF);
//
//        // Display all detected signals for debugging
//        int yOffset = -50;
//        for (SignalInfo signal : s.getSignals()) {
//            String signalInfo = String.format("Signal at %.1fm (occupied: %s)", signal.getDistance(), signal.isOccupied());
//            graphics.drawString(font, signalInfo, 10, yOffset, 0xFFFFFF);
//            yOffset += 15;
        posestack.popPose();
        }
        public void renderETCSlimits(GuiGraphics graphics, Train train, PoseStack posestack,int Xpos, int Ypos) {
            SignalScanResult s = SignalFinder.scanAheadForSignals(train, 4000,backward);
            if(s== null){
                return;
            }
            if(previousSignalScanResult != null){

            if((Math.abs(s.getDistanceToClosestOccupiedSignal()-previousSignalScanResult.getDistanceToClosestOccupiedSignal())>100) && backward==previousBackward){
                s = previousSignalScanResult;
            }}
            previousSignalScanResult = s;
            posestack.pushPose();
            posestack.translate(Xpos + 361, Ypos + 227, 0);
            int CurrentX = 0;
            int CurrentY = 0;
            double px = 0;

            ResourceLocation startTex = new ResourceLocation("realism:textures/etcsplusstart.png");
            ResourceLocation midTex = new ResourceLocation("realism:textures/etcsplusmid.png");
            ResourceLocation endTex = new ResourceLocation("realism:textures/etcsplusend.png");
            ResourceLocation flagTex = new ResourceLocation("realism:textures/flag.png");

            if (s.getDistanceToClosestOccupiedSignal() > 4000) {
                CurrentY -= 9;
                renderElement(graphics, startTex, 0, CurrentY, 15, 9);

                for (int i = 0; i < 198; i += 1) {
                    CurrentY -= 1;
                    renderElement(graphics, midTex, 0, CurrentY, 15, 1);
                }

                posestack.popPose();
                return;
            }

            if (s.getDistanceToClosestOccupiedSignal() < 60) {
                double x = (s.getDistanceToClosestOccupiedSignal() * 0.304);
                for (int i = 0; i < (int) x; i += 1) {
                    CurrentY -= 1;
                    renderElement(graphics, midTex, 0, CurrentY, 15, 1);
                }
                renderElement(graphics, flagTex, 15, CurrentY, 15, 9);
                posestack.popPose();
                return;
            }

            CurrentY -= 9;
            renderElement(graphics, startTex, 0, CurrentY, 15, 9);
            if (s.getDistanceToClosestOccupiedSignal() <= 500){
                px = (s.getDistanceToClosestOccupiedSignal() * 0.21) - 18;}
            else {
                px = (s.getDistanceToClosestOccupiedSignal() * 0.21) - 9;
            }
            if (px > 96.0) {
                px = 96.0;
            }
            for (int i = 0; i < (int) px; i += 1) {
                CurrentY -= 1;
                renderElement(graphics, midTex, 0, CurrentY, 15, 1);
            }
            if (s.getDistanceToClosestOccupiedSignal() <= 500) {
                CurrentY -= 9;
                renderElement(graphics, endTex, 0, CurrentY, 15, 9);
                renderElement(graphics, flagTex, 15, CurrentY, 15, 9);
                posestack.popPose();
                return;
            }
            if (s.getDistanceToClosestOccupiedSignal() < 1000){
            px = ((s.getDistanceToClosestOccupiedSignal()-500) * 0.068) ;}
            else {px = ((s.getDistanceToClosestOccupiedSignal()-500) * 0.068);}
             if (px > 34.0) {
                px = 34.0;
            }
            for (int i = 0; i < (int) px; i += 1) {
                CurrentY -= 1;
                renderElement(graphics, midTex, 0, CurrentY, 15, 1);
            }
            if (s.getDistanceToClosestOccupiedSignal() <= 1000) {
                CurrentY -= 9;
                renderElement(graphics, endTex, 0, CurrentY, 15, 9);
                renderElement(graphics, flagTex, 15, CurrentY, 15, 9);
                posestack.popPose();
                return;
            }
            if (s.getDistanceToClosestOccupiedSignal() <= 2000){
                px = ((s.getDistanceToClosestOccupiedSignal()-1000) * 0.034);}
            else {px = ((s.getDistanceToClosestOccupiedSignal()-1000) * 0.034);}
            if (px > 34.0) {
                px = 34.0;
            }
            for (int i = 0; i < (int) px; i += 1) {
                CurrentY -= 1;
                renderElement(graphics, midTex, 0, CurrentY, 15, 1);
            }
            if (s.getDistanceToClosestOccupiedSignal() <= 2000) {
                CurrentY -= 9;
                renderElement(graphics, endTex, 0, CurrentY, 15, 9);
                renderElement(graphics, flagTex, 15, CurrentY, 15, 9);
                posestack.popPose();
                return;
            }
            px = ((s.getDistanceToClosestOccupiedSignal()-2000) * 0.017);
            for (int i = 0; i < (int) px; i += 1) {
                CurrentY -= 1;
                renderElement(graphics, midTex, 0, CurrentY, 15, 1);
            }
            CurrentY -= 9;
            renderElement(graphics, endTex, 0, CurrentY, 15, 9);
            renderElement(graphics, flagTex, 15, CurrentY, 15, 9);
            posestack.popPose();

        }

        public static void renderElement(GuiGraphics graphics, ResourceLocation path, int x, int y, int width, int height) {
            RenderSystem.setShaderTexture(0, path);
            graphics.blit(path, x, y, 0, 0, width, height, width, height);


        }
    };

