package net.Realism.forge;


import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.Realism.Interfaces.IOrientedContraptionEntity;
import net.Realism.config.RealismConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "realism", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ComputeCameraAngleser {

    float roal = 0f;

    @SubscribeEvent
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
