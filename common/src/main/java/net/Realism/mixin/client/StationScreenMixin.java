package net.Realism.mixin.client;

import com.simibubi.create.content.trains.station.AbstractStationScreen;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.station.StationScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.content.gui.TrainSettingsGui;
import net.Realism.foundation.util.AllRealismIcons;
import net.Realism.mixin.mixinaccesors.ScreenAccessor;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StationScreen.class, remap = false)
public abstract class StationScreenMixin extends AbstractStationScreen {

    @Shadow
    private IconButton disassembleTrainButton;

    @Unique
    private IconButton realism$settingsButton;

    @Unique
    private Button realism$disassembleButton;

    @Unique
    private boolean realism$updaterAdded = false;



    public StationScreenMixin(StationBlockEntity be, GlobalStation station) {
        super(be, station);
    }

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/station/StationScreen;tickTrainDisplay()V"), remap = true)
    private void realism$onInit(CallbackInfo ci) {
        // Create settings button with placeholder position; will be positioned next to Disassemble when found
        realism$settingsButton = new IconButton(this.disassembleTrainButton.getX()+this.disassembleTrainButton.getWidth()+2, this.disassembleTrainButton.getY(), 16, 16,AllRealismIcons.SETTINGS_ICON)
                .withCallback(this::onSettingsPressed);
        realism$settingsButton.visible = false; // hidden until we find a train/disassemble button
        ((ScreenAccessor)(Object)this).realism$invokeAddRenderableWidget(realism$settingsButton);

        // Add a lightweight per-frame updater without injecting into render()
        if (!realism$updaterAdded) {
            ((ScreenAccessor)(Object)this).realism$invokeAddRenderableOnly((graphics, mouseX, mouseY, partialTicks) -> alignAndToggleSettings());
            realism$updaterAdded = true;
        }

        alignAndToggleSettings();
    }



    @Unique
    private void alignAndToggleSettings() {
            realism$settingsButton.visible = this.disassembleTrainButton.visible && this.disassembleTrainButton.active;
        }
    @Unique
    private void onSettingsPressed() {
        this.onClose();
        if(displayedTrain.get() instanceof ITrainInterface Rtrain)
        DLWindow.openWindow((manager) -> new TrainSettingsGui(manager, Rtrain));
    }
}
