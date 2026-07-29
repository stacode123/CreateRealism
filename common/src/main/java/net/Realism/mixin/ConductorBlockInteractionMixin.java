package net.Realism.mixin;

import com.simibubi.create.api.behaviour.interaction.ConductorBlockInteractionBehavior;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleItem;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.Realism.Interfaces.IScheduleRuntimeMixin;
import net.Realism.content.trains.schedule.AdvancedScheduleItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Blaze-burner conductors accept schedules through this behaviour, which
 * gates the held item by registry entry — widen it to any {@link ScheduleItem}
 * so the Advanced Schedule can be handed over, and mark the runtime so
 * returnSchedule() gives the advanced item back.
 */
@Mixin(value = ConductorBlockInteractionBehavior.class, remap = false)
public abstract class ConductorBlockInteractionMixin {

    @Redirect(method = "handlePlayerInteraction",
            at = @At(value = "INVOKE", target = "Lcom/tterrag/registrate/util/entry/ItemEntry;isIn"))
    public boolean acceptAdvancedSchedule(ItemEntry<?> entry, ItemStack stack) {
        return entry.isIn(stack) || stack.getItem() instanceof ScheduleItem;
    }

    @Redirect(method = "handlePlayerInteraction",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/schedule/ScheduleRuntime;setSchedule"))
    public void markAdvancedSchedule(ScheduleRuntime runtime, Schedule schedule, boolean auto,
                                     Player player, InteractionHand activeHand, BlockPos localPos,
                                     AbstractContraptionEntity contraptionEntity) {
        runtime.setSchedule(schedule, auto);
        if (player.getItemInHand(activeHand).getItem() instanceof AdvancedScheduleItem)
            ((IScheduleRuntimeMixin) runtime).setAdvancedSchedule(true);
    }
}
