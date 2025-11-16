package net.Realism.gui;


import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.infrastructure.config.AllConfigs;
import de.mrjulsen.mcdragonlib.client.gui.events.DLGuiStandardEvents;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindowManager;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLCycleButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLNumberPicker;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLRichTextLabel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.util.INumberFormatAdapter;
import de.mrjulsen.mcdragonlib.client.gui.widgets.util.ITextFormatter;
import de.mrjulsen.mcdragonlib.client.util.DLGuiGraphics;
import de.mrjulsen.mcdragonlib.client.util.GuiUtils;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;
import net.Realism.Interfaces.ITrainInterface;
import net.Realism.RNetworking;
import net.Realism.config.RealismConfig;
import net.Realism.network.TrainSettingsSavePacket;
import net.Realism.trains.TrainSettings;
import net.minecraft.network.chat.Component;

import java.util.List;

public class TrainSettingsGui extends DLWindow {
    public TrainSettingsGui(DLWindowManager manager, ITrainInterface Rtrain) {
        super(manager);

        TrainSettings cs = Rtrain.realism$getSettings();
        // Set window size and position
        setSize(500, 300);
        setPosition(0, 0);

        // Add components
        DLRichTextLabel title = addComponent(
                new DLRichTextLabel(140, 10, 200, 20) {}
        );
        title.text.get().set("Create: Realism Train Settings");



        DLCycleButton Tiltbutton = addComponent(
                new DLCycleButton(70, 40, 80, 20){


                }
        );
        Tiltbutton.items.add("None");
        Tiltbutton.items.add("Passive");
        Tiltbutton.items.add("Active");
        Tiltbutton.items.add("Custom");
        Tiltbutton.text.set(Component.literal(""));
        Tiltbutton.textFormat.set((ITextFormatter<DLCycleButton<String>>) (src ->
                TextUtils.text(src.selectedItem.get().map(Object::toString).orElse(""))
                        .withStyle(src.text.get().getStyle())
        ));

        Tiltbutton.selectedIndex.set(cs.ts.ordinal());

        DLRichTextLabel tiltLabel = addComponent(
                new DLRichTextLabel(15, 45, 60, 20) {
                    @Override
                    public void renderFrontLayer(DLGuiGraphics g,double mousex, double mousey, Rectangle bounds){
                        super.renderFrontLayer(g,mousex,mousey,bounds);
                        int screenW = (int)getWindowManager().getScreenWidth();
                        if (isMouseOver(mousex,mousey)) {
                            GuiUtils.drawTooltip(g,g.defaultFont(), (int) mousex, (int) mousey,
                                    List.of(
                                            TextUtils.text("Select the train's tilt setting."),
                                            TextUtils.text("None: Only Centrifugal forces.(Max 2°)"),
                                            TextUtils.text("Passive: Passively tilts to counteract lateral forces.(Max 4°)"),
                                            TextUtils.text("Active: Actively tilts to counteract lateral forces.(Max >8°)"),
                                            TextUtils.text("Custom: User-defined tilt parameters.")
                                    ),
                                    screenW
                            );
                        }
                    }
                }
        );
        tiltLabel.text.get().set("Train Tilt: ");

        DLNumberPicker minSpeedPicker = addComponent(
                new DLNumberPicker(75, 70, 80, 20){
                    @Override
                    public void renderFrontLayer(DLGuiGraphics g,double mousex, double mousey, Rectangle bounds){
                        super.renderFrontLayer(g,mousex,mousey,bounds);
                        int screenW = (int)getWindowManager().getScreenWidth();
                        if (isMouseOver(mousex,mousey)) {
                            GuiUtils.drawTooltip(g,g.defaultFont(), (int) mousex, (int) mousey,
                                    List.of(
                                            TextUtils.text("Minimum speed km/h at which the tilt effect starts functioning")

                                    ),
                                    screenW
                            );
                        }
                    }
                }
        );
        minSpeedPicker.min.set((double) 0);
        minSpeedPicker.max.set(300.0);
        minSpeedPicker.value.set(cs.customMinSpeed);
        minSpeedPicker.visible.set(cs.ts == TrainSettings.tiltSetting.CUSTOM);


        DLRichTextLabel minSpeedLabel = addComponent(
                new DLRichTextLabel(19, 75, 60, 20)
        );
        minSpeedLabel.text.get().set("Min Speed: ");
        minSpeedLabel.visible.set(cs.ts == TrainSettings.tiltSetting.CUSTOM);

        DLNumberPicker maxTiltPicker = addComponent(
                new DLNumberPicker(75, 95, 80, 20){
                    @Override
                    public void renderFrontLayer(DLGuiGraphics g,double mousex, double mousey, Rectangle bounds){
                        super.renderFrontLayer(g,mousex,mousey,bounds);
                        int screenW = (int)getWindowManager().getScreenWidth();
                        if (isMouseOver(mousex,mousey)) {
                            GuiUtils.drawTooltip(g,g.defaultFont(), (int) mousex, (int) mousey,
                                    List.of(
                                            TextUtils.text("Maximum tilt angle in degrees")
                                    ),
                                    screenW
                            );
                        }
                    }
                }
        );
        maxTiltPicker.min.set((double) 0);
        maxTiltPicker.max.set(90.0);
        maxTiltPicker.format.set(new INumberFormatAdapter.DecimalNumberFormat(1));
        maxTiltPicker.value.set((double) cs.customMaxTilt);
        maxTiltPicker.visible.set(cs.ts == TrainSettings.tiltSetting.CUSTOM);
        maxTiltPicker.step.set(0.1);


        DLRichTextLabel maxTiltLabel = addComponent(
                new DLRichTextLabel(29, 100, 60, 20)
        );
        maxTiltLabel.text.get().set("Max Tilt: ");
        maxTiltLabel.visible.set(cs.ts == TrainSettings.tiltSetting.CUSTOM);

        DLNumberPicker IntensityPicker = addComponent(
                new DLNumberPicker(75, 120, 80, 20){
                    @Override
                    public void renderFrontLayer(DLGuiGraphics g,double mousex, double mousey, Rectangle bounds){
                        super.renderFrontLayer(g,mousex,mousey,bounds);
                        int screenW = (int)getWindowManager().getScreenWidth();
                        if (isMouseOver(mousex,mousey)) {
                            GuiUtils.drawTooltip(g,g.defaultFont(), (int) mousex, (int) mousey,
                                    List.of(
                                            TextUtils.text("How powerful the tilt effect is")
                                    ),
                                    screenW
                            );
                        }
                    }

                }
        );
        IntensityPicker.min.set((double) 0);
        IntensityPicker.max.set(20.0);
        IntensityPicker.format.set(new INumberFormatAdapter.DecimalNumberFormat(1));
        IntensityPicker.value.set((double) cs.customTiltIntensity);
        IntensityPicker.visible.set(cs.ts == TrainSettings.tiltSetting.CUSTOM);
        IntensityPicker.step.set(0.1);


        DLRichTextLabel IntensityLabel = addComponent(
                new DLRichTextLabel(5, 125, 72, 20){

                }
        );
        IntensityLabel.text.get().set("Tilt Intensity: ");
        IntensityLabel.visible.set(cs.ts == TrainSettings.tiltSetting.CUSTOM);

        DLRichTextLabel TiltDirectionLabel = addComponent(
                new DLRichTextLabel(5, 155, 72, 20){});
        TiltDirectionLabel.text.get().set("Tilt Direction: ");
        TiltDirectionLabel.visible.set(cs.ts == TrainSettings.tiltSetting.CUSTOM);


        DLCycleButton DirectionButton = addComponent(
                new DLCycleButton(75, 150, 72, 20){

                }
        );
        DirectionButton.items.add("Inside");
        DirectionButton.items.add("Outside");
        DirectionButton.textFormat.set((ITextFormatter<DLCycleButton<String>>) (src ->
                TextUtils.text(src.selectedItem.get().map(Object::toString).orElse(""))
                        .withStyle(src.text.get().getStyle())
        ));
        DirectionButton.selectedIndex.set(cs.Inside?0:1);
        DirectionButton.visible.set(cs.ts == TrainSettings.tiltSetting.CUSTOM);

        Tiltbutton.addEventListener(DLCycleButton.SelectedItemChanged.class, (src, e) -> {
            // e.item() is Optional<T>; e.index() is the selected index (or -1)
            DLCycleButton.SelectedItemChanged te = (DLCycleButton.SelectedItemChanged)  e;
            if (te.index() == 3) { // Custom selected
                minSpeedPicker.visible.set(true);
                maxTiltPicker.visible.set(true);
                IntensityPicker.visible.set(true);
                minSpeedLabel.visible.set(true);
                maxTiltLabel.visible.set(true);
                IntensityLabel.visible.set(true);
                DirectionButton.visible.set(true);
                TiltDirectionLabel.visible.set(true);
            }
            else {
                minSpeedPicker.visible.set(false);
                maxTiltPicker.visible.set(false);
                IntensityPicker.visible.set(false);
                minSpeedLabel.visible.set(false);
                maxTiltLabel.visible.set(false);
                IntensityLabel.visible.set(false);
                DirectionButton.visible.set(false);
                TiltDirectionLabel.visible.set(false);
            }

            return false; // return true to stop further propagation if needed
        });

        DLCycleButton AccelerationSettingbutton = addComponent(
                new DLCycleButton(275, 40, 80, 20){

                }
        );
        AccelerationSettingbutton.items.add("None");
        AccelerationSettingbutton.items.add("Standard");
        AccelerationSettingbutton.items.add("Custom");

        AccelerationSettingbutton.textFormat.set((ITextFormatter<DLCycleButton<String>>) (src ->
                TextUtils.text(src.selectedItem.get().map(Object::toString).orElse(""))
                        .withStyle(src.text.get().getStyle())
        ));

        AccelerationSettingbutton.selectedIndex.set(cs.as.ordinal());

        DLRichTextLabel AccelerationSettingLabel = addComponent(
                new DLRichTextLabel(165, 45, 105, 20){
                    @Override
                    public void renderFrontLayer(DLGuiGraphics g,double mousex, double mousey, Rectangle bounds){
                        super.renderFrontLayer(g,mousex,mousey,bounds);
                        int screenW = (int)getWindowManager().getScreenWidth();
                        if (isMouseOver(mousex,mousey)) {
                            GuiUtils.drawTooltip(g,g.defaultFont(), (int) mousex, (int) mousey,
                                    List.of(
                                            TextUtils.text("Select the train's acceleration setting."),
                                            TextUtils.text("None: Acceleration not affected"),
                                            TextUtils.text("Standard: Acceleration is affected,"),
                                            TextUtils.text("by train's weight and engine power."),
                                            TextUtils.text("Custom: User-defined acceleration.")
                                    ),
                                    screenW
                            );
                        }
                    }
                }
        );
        AccelerationSettingLabel.text.get().set("Train Acceleration: ");

        DLNumberPicker AccelerationPicker = addComponent(
                new DLNumberPicker(275, 70, 80, 20)
        );
        AccelerationPicker.min.set((double) 0);
        if(RealismConfig.COMMON.AllowBiggerValuesTrains.get()){
            AccelerationPicker.max.set(50.0);
        }
        else {
            AccelerationPicker.max.set((double) AllConfigs.server().trains.trainAcceleration.getF());
        }
        AccelerationPicker.format.set(new INumberFormatAdapter.DecimalNumberFormat(1));
        AccelerationPicker.value.set((double) cs.customAcceleration);
        AccelerationPicker.visible.set(cs.as == TrainSettings.accelerationSetting.CUSTOM);
        AccelerationPicker.step.set(0.1);



        DLRichTextLabel AccelerationLabel = addComponent(
                new DLRichTextLabel(195, 75, 105, 20)
        );
        AccelerationLabel.text.get().set("Acceleration: ");
        AccelerationLabel.visible.set(cs.as == TrainSettings.accelerationSetting.CUSTOM);

        DLRichTextLabel CurrentAccelerationLabel = addComponent(
                new DLRichTextLabel(195, 100, 200, 20)
        );
        CurrentAccelerationLabel.text.get().set("Default Acceleration: " + AllConfigs.server().trains.trainAcceleration.get().toString() + " m/s^2");






        AccelerationSettingbutton.addEventListener(DLCycleButton.SelectedItemChanged.class, (src, e) -> {
            // e.item() is Optional<T>; e.index() is the selected index (or -1)
            DLCycleButton.SelectedItemChanged te = (DLCycleButton.SelectedItemChanged)  e;
            if (te.index() == 2) { // Custom selected
                AccelerationPicker.visible.set(true);
                AccelerationLabel.visible.set(true);
                CurrentAccelerationLabel.visible.set(true);
            }
            else{
                AccelerationPicker.visible.set(false);
                AccelerationLabel.visible.set(false);
                CurrentAccelerationLabel.visible.set(false);
                }
            return false; // return true to stop further propagation if needed

       });
        DLButton ConfirmButton = addComponent(
                new DLButton(180, 175, 80, 20)
        );
        ConfirmButton.text.set(Component.literal("Confirm"));
        ConfirmButton.visible.set(true);
        ConfirmButton.addEventListener(DLGuiStandardEvents.ClickEvent.class, (evt,s) -> {
            TrainSettings ts = new TrainSettings();
            int o = (int) Tiltbutton.selectedIndex.get();
            if (o == 0) {
                ts.ts = TrainSettings.tiltSetting.NONE;
            } else if (o == 1) {
                ts.ts = TrainSettings.tiltSetting.PASSIVE;
            } else if (o == 2) {
                ts.ts = TrainSettings.tiltSetting.ACTIVE;
            } else if (o == 3) {
                ts.ts = TrainSettings.tiltSetting.CUSTOM;
                ts.customMinSpeed = minSpeedPicker.value.get().doubleValue();
                ts.customMaxTilt = maxTiltPicker.value.get().floatValue();
                ts.customTiltIntensity = IntensityPicker.value.get().floatValue();
            }

            int object = (int) AccelerationSettingbutton.selectedIndex.get();
            if (object == 0) {
                ts.as = TrainSettings.accelerationSetting.NONE;
            } else if (object == 1) {
                ts.as = TrainSettings.accelerationSetting.REALISTIC;
            } else if (object == 2) {
                ts.as = TrainSettings.accelerationSetting.CUSTOM;
                ts.customAcceleration = AccelerationPicker.value.get().doubleValue();
            }
            if (((int) DirectionButton.selectedIndex.get())==0){
                ts.Inside = true;
            }else{
                ts.Inside = false;
            }
            Rtrain.realism$setSettings(ts);
            RNetworking.sendToServer(new TrainSettingsSavePacket(ts,((Train)Rtrain).id));
            closeWindow();

            return false;
        });



   }

}
