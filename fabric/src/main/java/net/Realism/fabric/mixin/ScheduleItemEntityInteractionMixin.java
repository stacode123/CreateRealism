package net.Realism.fabric.mixin;

import com.simibubi.create.content.trains.schedule.ScheduleItemEntityInteraction;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ScheduleItemEntityInteraction.class)
public class ScheduleItemEntityInteractionMixin {
//    @ModifyVariable(
//            method = "interactWithConductor",
//            at = @At("STORE"),
//            ordinal = 0,
//            name = "itemStack")
//    private static Player onGetItemStack(Player value) {
//        // Your code here after itemStack is assigned
//        return value;
//    }
}