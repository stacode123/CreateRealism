package net.Realism.content.trains.schedule;


import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.ScheduleItem;
import com.simibubi.create.content.trains.track.ITrackBlock;
import net.Realism.Interfaces.IScheduleRuntimeMixin;
import net.Realism.content.graph.v2.GraphViewService;
import net.Realism.foundation.util.AllMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;


public class AdvancedScheduleItem extends ScheduleItem {
    public AdvancedScheduleItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player != null && !player.isShiftKeyDown()
                && level.getBlockState(pos).getBlock() instanceof ITrackBlock) {
            if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer serverPlayer)
                GraphViewService.request(serverPlayer, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useOn(context);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new AdvancedScheduleMenu(AllMenuTypes.ADVANCED_SCHEDULE.get(), id, inv, player.getMainHandItem());
    }

    @Override
    public InteractionResult handScheduleTo(ItemStack pStack, Player pPlayer, LivingEntity pInteractionTarget,
                                            InteractionHand pUsedHand) {
        Train train = null;
        if (!pPlayer.level().isClientSide && pInteractionTarget != null
                && pInteractionTarget.getRootVehicle() instanceof CarriageContraptionEntity cce
                && cce.getCarriage() != null)
            train = cce.getCarriage().train;
        boolean hadSchedule = train != null && train.runtime.getSchedule() != null;

        InteractionResult result = super.handScheduleTo(pStack, pPlayer, pInteractionTarget, pUsedHand);

        // A null -> non-null schedule transition means super applied ours; mark
        // the runtime so returnSchedule() hands back an Advanced Schedule.
        if (train != null && !hadSchedule && train.runtime.getSchedule() != null)
            ((IScheduleRuntimeMixin) train.runtime).setAdvancedSchedule(true);
        return result;
    }
}
