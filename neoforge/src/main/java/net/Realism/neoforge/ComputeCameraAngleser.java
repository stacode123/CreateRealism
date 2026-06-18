package net.Realism.neoforge;


import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.Realism.Interfaces.IOrientedContraptionEntity;
import net.Realism.config.RealismConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = "realism", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ComputeCameraAngleser {

    float roal = 0f;

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void drawLast(ViewportEvent.ComputeCameraAngles event) {
        if(Minecraft.getInstance().player.getVehicle() instanceof CarriageContraptionEntity car && RealismConfig.CLIENT.enablePlayerTilt.get()){
            if(car instanceof IOrientedContraptionEntity ioc){
                if(car.getCarriage().anyAvailableEntity().movingBackwards){
                event.setRoll(-ioc.realism$getViewRoll((float) event.getPartialTick()));}
                else{
                    event.setRoll(ioc.realism$getViewRoll((float) event.getPartialTick()));
                }
            }
        }

    }
}