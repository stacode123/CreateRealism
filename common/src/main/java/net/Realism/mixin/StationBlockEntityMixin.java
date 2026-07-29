package net.Realism.mixin;

import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.content.trains.schedule.ScheduleItem;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Predicate;

/**
 * The station's auto-schedule slot gates items by registry entry both on
 * insertion and on application — widen both to any {@link ScheduleItem} so
 * the Advanced Schedule works as an auto schedule.
 */
@Mixin(value = StationBlockEntity.class, remap = false)
public abstract class StationBlockEntityMixin {

    @Redirect(method = "addBehaviours",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/logistics/depot/DepotBehaviour;onlyAccepts"))
    public DepotBehaviour acceptAdvancedScheduleInsertion(DepotBehaviour depot, Predicate<ItemStack> filter) {
        return depot.onlyAccepts(stack -> stack.getItem() instanceof ScheduleItem);
    }

    @Redirect(method = "applyAutoSchedule",
            at = @At(value = "INVOKE", target = "Lcom/tterrag/registrate/util/entry/ItemEntry;isIn"))
    public boolean applyAdvancedAutoSchedule(ItemEntry<?> entry, ItemStack stack) {
        return entry.isIn(stack) || stack.getItem() instanceof ScheduleItem;
    }
}
