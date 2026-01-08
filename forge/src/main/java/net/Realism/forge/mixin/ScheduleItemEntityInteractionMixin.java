package net.Realism.forge.mixin;

import com.simibubi.create.content.trains.schedule.ScheduleItemEntityInteraction;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ScheduleItemEntityInteraction.class,remap = false)
public class ScheduleItemEntityInteractionMixin {
    private static final Logger log = LoggerFactory.getLogger(ScheduleItemEntityInteractionMixin.class);

    @Inject(
            method = "interactWithConductor",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/event/entity/player/PlayerInteractEvent$EntityInteractSpecific;getItemStack()Lnet/minecraft/world/item/ItemStack;",
                    shift = At.Shift.AFTER
            )
    )
    private static void afterGetItemStack(PlayerInteractEvent.EntityInteractSpecific event, CallbackInfo ci) {
        log.debug("interactWithConductor called");

    }
}