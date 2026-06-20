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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TrainSettingsGui extends DLWindow {

    // Suppress picker change events when syncTiltPickers is updating them programmatically
    private boolean suppressingPickerEvents = false;

    private Font getFont() {
        return Minecraft.getInstance().font;
    }

    // Returns [labelX, labelWidth] for a label whose right edge pins at controlX
    private int[] computeLabel(String key, int minWidth, int controlX) {
        Font font = getFont();
        String text = Component.translatable(key).getString();
        int textW = font.width(text);
        //int finalW = Math.max(minWidth, textW);
        return new int[]{controlX - textW, textW+10};
    }

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
        title.text.get().set(Component.translatable("realism.gui.train_settings.title").getString());



        DLCycleButton Tiltbutton = addComponent(
                new DLCycleButton(75, 40, 80, 20){


                }
        );
        Tiltbutton.items.add(Component.translatable("realism.gui.tilt.disabled").getString());
        Tiltbutton.items.add(Component.translatable("realism.gui.tilt.none").getString());
        Tiltbutton.items.add(Component.translatable("realism.gui.tilt.passive").getString());
        Tiltbutton.items.add(Component.translatable("realism.gui.tilt.active").getString());
        Tiltbutton.items.add(Component.translatable("realism.gui.tilt.custom").getString());
        Tiltbutton.text.set(Component.literal(""));
        Tiltbutton.textFormat.set((ITextFormatter<DLCycleButton<String>>) (src ->
                TextUtils.text(src.selectedItem.get().map(Object::toString).orElse(""))
                        .withStyle(src.text.get().getStyle())
        ));

        Tiltbutton.selectedIndex.set(cs.ts.ordinal());

        DLRichTextLabel tiltLabel = addComponent(
                new DLRichTextLabel(
                        computeLabel("realism.gui.tilt.label", 70, 70)[0], 45,
                        computeLabel("realism.gui.tilt.label", 70, 70)[1], 20) {
                    @Override
                    public void renderFrontLayer(DLGuiGraphics g,double mousex, double mousey, Rectangle bounds){
                        super.renderFrontLayer(g,mousex,mousey,bounds);
                        int screenW = (int)getWindowManager().getScreenWidth();
                        if (isMouseOver(mousex,mousey)) {
                            GuiUtils.drawTooltip(g,g.defaultFont(), (int) mousex, (int) mousey,
                                    List.of(
                                            TextUtils.text(Component.translatable("realism.gui.tilt.tooltip.header").getString()),
                                            TextUtils.text(Component.translatable("realism.gui.tilt.tooltip.disabled").getString()),
                                            TextUtils.text(Component.translatable("realism.gui.tilt.tooltip.none").getString()),
                                            TextUtils.text(Component.translatable("realism.gui.tilt.tooltip.passive").getString()),
                                            TextUtils.text(Component.translatable("realism.gui.tilt.tooltip.active").getString()),
                                            TextUtils.text(Component.translatable("realism.gui.tilt.tooltip.custom").getString())
                                    ),
                                    screenW
                            );
                        }
                    }
                }
        );
        tiltLabel.text.get().set(Component.translatable("realism.gui.tilt.label").getString());

        DLNumberPicker minSpeedPicker = addComponent(
                new DLNumberPicker(75, 70, 80, 20){
                    @Override
                    public void renderFrontLayer(DLGuiGraphics g,double mousex, double mousey, Rectangle bounds){
                        super.renderFrontLayer(g,mousex,mousey,bounds);
                        int screenW = (int)getWindowManager().getScreenWidth();
                        if (isMouseOver(mousex,mousey)) {
                            GuiUtils.drawTooltip(g,g.defaultFont(), (int) mousex, (int) mousey,
                                    List.of(
                                            TextUtils.text(Component.translatable("realism.gui.tilt.min_speed.tooltip").getString())
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
        //minSpeedPicker.visible.set(cs.ts == TrainSettings.tiltSetting.CUSTOM);




        DLRichTextLabel minSpeedLabel = addComponent(
                new DLRichTextLabel(
                        computeLabel("realism.gui.tilt.min_speed.label", 70, 70)[0], 75,
                        computeLabel("realism.gui.tilt.min_speed.label", 70, 70)[1], 20)
        );
        minSpeedLabel.text.get().set(Component.translatable("realism.gui.tilt.min_speed.label").getString());
        //minSpeedLabel.visible.set(cs.ts == TrainSettings.tiltSetting.CUSTOM);

        DLNumberPicker maxTiltPicker = addComponent(
                new DLNumberPicker(75, 95, 80, 20){
                    @Override
                    public void renderFrontLayer(DLGuiGraphics g,double mousex, double mousey, Rectangle bounds){
                        super.renderFrontLayer(g,mousex,mousey,bounds);
                        int screenW = (int)getWindowManager().getScreenWidth();
                        if (isMouseOver(mousex,mousey)) {
                            GuiUtils.drawTooltip(g,g.defaultFont(), (int) mousex, (int) mousey,
                                    List.of(
                                            TextUtils.text(Component.translatable("realism.gui.tilt.max_tilt.tooltip").getString())
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
       // maxTiltPicker.visible.set(cs.ts == TrainSettings.tiltSetting.CUSTOM);
        maxTiltPicker.step.set(0.1);


        DLRichTextLabel maxTiltLabel = addComponent(
                new DLRichTextLabel(
                        computeLabel("realism.gui.tilt.max_tilt.label", 70, 70)[0], 100,
                        computeLabel("realism.gui.tilt.max_tilt.label", 70, 70)[1], 20)
        );
        maxTiltLabel.text.get().set(Component.translatable("realism.gui.tilt.max_tilt.label").getString());
        //maxTiltLabel.visible.set(cs.ts == TrainSettings.tiltSetting.CUSTOM);

        DLNumberPicker IntensityPicker = addComponent(
                new DLNumberPicker(75, 120, 80, 20){
                    @Override
                    public void renderFrontLayer(DLGuiGraphics g,double mousex, double mousey, Rectangle bounds){
                        super.renderFrontLayer(g,mousex,mousey,bounds);
                        int screenW = (int)getWindowManager().getScreenWidth();
                        if (isMouseOver(mousex,mousey)) {
                            GuiUtils.drawTooltip(g,g.defaultFont(), (int) mousex, (int) mousey,
                                    List.of(
                                            TextUtils.text(Component.translatable("realism.gui.tilt.intensity.tooltip").getString())
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
       //IntensityPicker.visible.set(cs.ts == TrainSettings.tiltSetting.CUSTOM);
        IntensityPicker.step.set(0.1);


        DLRichTextLabel IntensityLabel = addComponent(
                new DLRichTextLabel(
                        computeLabel("realism.gui.tilt.intensity.label", 72, 70)[0], 125,
                        computeLabel("realism.gui.tilt.intensity.label", 72, 70)[1], 20) {

                }
        );
        IntensityLabel.text.get().set(Component.translatable("realism.gui.tilt.intensity.label").getString());
        //IntensityLabel.visible.set(cs.ts == TrainSettings.tiltSetting.CUSTOM);

        DLRichTextLabel TiltDirectionLabel = addComponent(
                new DLRichTextLabel(
                        computeLabel("realism.gui.tilt.direction.label", 72, 70)[0], 155,
                        computeLabel("realism.gui.tilt.direction.label", 72, 70)[1], 20){});
        TiltDirectionLabel.text.get().set(Component.translatable("realism.gui.tilt.direction.label").getString());
        //TiltDirectionLabel.visible.set(cs.ts == TrainSettings.tiltSetting.CUSTOM);


        DLCycleButton DirectionButton = addComponent(
                new DLCycleButton(75, 150, 72, 20){

                }
        );
        DirectionButton.items.add(Component.translatable("realism.gui.tilt.direction.inside").getString());
        DirectionButton.items.add(Component.translatable("realism.gui.tilt.direction.outside").getString());
        DirectionButton.textFormat.set((ITextFormatter<DLCycleButton<String>>) (src ->
                TextUtils.text(src.selectedItem.get().map(Object::toString).orElse(""))
                        .withStyle(src.text.get().getStyle())
        ));
        DirectionButton.selectedIndex.set(cs.Inside?0:1);
        //DirectionButton.visible.set(cs.ts == TrainSettings.tiltSetting.CUSTOM);

        Tiltbutton.addEventListener(DLCycleButton.SelectedItemChanged.class, (src, e) -> {
            int index = ((DLCycleButton.SelectedItemChanged) e).index();
            syncTiltPickers(index, cs, minSpeedPicker, maxTiltPicker, IntensityPicker, DirectionButton);
            return false;
        });

        // Sync pickers to match the current train settings on window initialization
        syncTiltPickers((int) Tiltbutton.selectedIndex.get(), cs, minSpeedPicker, maxTiltPicker, IntensityPicker, DirectionButton);

        maxTiltPicker.addEventListener(DLNumberPicker.ValueChangedEvent.class, (src, e)->{
            if (suppressingPickerEvents) return false;
            if((int)Tiltbutton.selectedIndex.get() != 4){
            Tiltbutton.selectedIndex.set(4);}
            return false;
        });
        IntensityPicker.addEventListener(DLNumberPicker.ValueChangedEvent.class, (src, e)->{
            if (suppressingPickerEvents) return false;
            if((int)Tiltbutton.selectedIndex.get() != 4){
                Tiltbutton.selectedIndex.set(4);}
            return false;
        });
        minSpeedPicker.addEventListener(DLNumberPicker.ValueChangedEvent.class, (src, e)->{
            if (suppressingPickerEvents) return false;
            if((int)Tiltbutton.selectedIndex.get() != 4){
                Tiltbutton.selectedIndex.set(4);}
            return false;
        });
        DirectionButton.addEventListener(DLCycleButton.SelectedItemChanged.class, (src, e)->{
            if (suppressingPickerEvents) return false;
            if((int)Tiltbutton.selectedIndex.get() != 4){
                Tiltbutton.selectedIndex.set(4);}
            return false;
        });

        DLCycleButton AccelerationSettingbutton = addComponent(
                new DLCycleButton(275, 40, 80, 20){

                }
        );
        AccelerationSettingbutton.items.add(Component.translatable("realism.gui.accel.setting.none").getString());
        AccelerationSettingbutton.items.add(Component.translatable("realism.gui.accel.setting.standard").getString());
        AccelerationSettingbutton.items.add(Component.translatable("realism.gui.accel.setting.custom").getString());

        AccelerationSettingbutton.textFormat.set((ITextFormatter<DLCycleButton<String>>) (src ->
                TextUtils.text(src.selectedItem.get().map(Object::toString).orElse(""))
                        .withStyle(src.text.get().getStyle())
        ));

        AccelerationSettingbutton.selectedIndex.set(cs.as.ordinal());

        DLRichTextLabel AccelerationSettingLabel = addComponent(
                new DLRichTextLabel(
                        computeLabel("realism.gui.accel.label", 105, 270)[0], 45,
                        computeLabel("realism.gui.accel.label", 105, 270)[1], 20){
                    @Override
                    public void renderFrontLayer(DLGuiGraphics g,double mousex, double mousey, Rectangle bounds){
                        super.renderFrontLayer(g,mousex,mousey,bounds);
                        int screenW = (int)getWindowManager().getScreenWidth();
                        if (isMouseOver(mousex,mousey)) {
                            GuiUtils.drawTooltip(g,g.defaultFont(), (int) mousex, (int) mousey,
                                    List.of(
                                            TextUtils.text(Component.translatable("realism.gui.accel.tooltip.header").getString()),
                                            TextUtils.text(Component.translatable("realism.gui.accel.tooltip.none").getString()),
                                            TextUtils.text(Component.translatable("realism.gui.accel.tooltip.standard1").getString()),
                                            TextUtils.text(Component.translatable("realism.gui.accel.tooltip.standard2").getString()),
                                            TextUtils.text(Component.translatable("realism.gui.accel.tooltip.custom").getString())
                                    ),
                                    screenW
                            );
                        }
                    }
                }
        );
        AccelerationSettingLabel.text.get().set(Component.translatable("realism.gui.accel.label").getString());

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
                new DLRichTextLabel(
                        computeLabel("realism.gui.accel.value.label", 105, 270)[0], 75,
                        computeLabel("realism.gui.accel.value.label", 105, 270)[1], 20)
        );
        AccelerationLabel.text.get().set(Component.translatable("realism.gui.accel.value.label").getString());
        AccelerationLabel.visible.set(cs.as == TrainSettings.accelerationSetting.CUSTOM);

        DLRichTextLabel CurrentAccelerationLabel = addComponent(
                new DLRichTextLabel(195, 100, 200, 20)
        );
        CurrentAccelerationLabel.text.get().set(Component.translatable("realism.gui.accel.default", AllConfigs.server().trains.trainAcceleration.get().toString()).getString());
        CurrentAccelerationLabel.visible.set(cs.as == TrainSettings.accelerationSetting.CUSTOM);





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
        ConfirmButton.text.set(Component.translatable("realism.button.confirm"));
        ConfirmButton.visible.set(true);
        ConfirmButton.addEventListener(DLGuiStandardEvents.ClickEvent.class, (evt,s) -> {
            TrainSettings ts = new TrainSettings();
            int o = (int) Tiltbutton.selectedIndex.get();
            if (o == 0) {
                ts.ts = TrainSettings.tiltSetting.DISABLED;
            } else if (o == 1) {
                ts.ts = TrainSettings.tiltSetting.NONE;
            } else if (o == 2) {
                ts.ts = TrainSettings.tiltSetting.PASSIVE;
            } else if (o == 3){
                ts.ts = TrainSettings.tiltSetting.ACTIVE;
            }
            else if (o == 4) {
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

    private void syncTiltPickers(int index, TrainSettings ts,
                                  DLNumberPicker minSpeed, DLNumberPicker maxTilt,
                                  DLNumberPicker intensity, DLCycleButton direction) {
        suppressingPickerEvents = true;
        try {
            ts.ts = switch (index) {
                case 0 -> {
                    minSpeed.value.set(0.0);
                    maxTilt.value.set(0.0);
                    intensity.value.set(0.0);
                    direction.selectedIndex.set(1);
                    yield TrainSettings.tiltSetting.DISABLED;
                }
                case 1 -> {
                    minSpeed.value.set(80.0);
                    maxTilt.value.set(15.0);
                    intensity.value.set(1.0);
                    direction.selectedIndex.set(1);
                    yield TrainSettings.tiltSetting.NONE;
                }
                case 2 -> {
                    minSpeed.value.set(80.0);
                    maxTilt.value.set(15.0);
                    intensity.value.set(2.0);
                    direction.selectedIndex.set(0);
                    yield TrainSettings.tiltSetting.PASSIVE;
                }
                case 3 -> {
                    minSpeed.value.set(40.0);
                    maxTilt.value.set(15.0);
                    intensity.value.set(4.5);
                    direction.selectedIndex.set(0);
                    yield TrainSettings.tiltSetting.ACTIVE;
                }
                default -> {
                    minSpeed.value.set(ts.customMinSpeed);
                    maxTilt.value.set((double) ts.customMaxTilt);
                    intensity.value.set((double) ts.customTiltIntensity);
                    direction.selectedIndex.set(1);
                    yield TrainSettings.tiltSetting.CUSTOM;
                }
            };
        } finally {
            suppressingPickerEvents = false;
        }
    }

}
