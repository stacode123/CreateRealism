package net.Realism.content.gui.schedule;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import de.mrjulsen.mcdragonlib.client.gui.events.DLGuiStandardEvents;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindowManager;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLPanel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLRichTextLabel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLScrollBar;
import de.mrjulsen.mcdragonlib.client.gui.widgets.layout.BorderLayout;
import de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout;
import de.mrjulsen.mcdragonlib.client.gui.widgets.richtext.Padding;
import de.mrjulsen.mcdragonlib.client.gui.widgets.richtext.RichTextComponent;
import de.mrjulsen.mcdragonlib.client.gui.widgets.richtext.TextStyle;
import de.mrjulsen.mcdragonlib.client.render.DefaultGuiTextures;
import de.mrjulsen.mcdragonlib.client.util.DLGuiGraphics;
import de.mrjulsen.mcdragonlib.data.ETextAlignment;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;
import net.Realism.RNetworking;
import net.Realism.foundation.network.SaveAdvancedSchedule;
import net.createmod.catnip.data.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class SimulatedScheduleWindow extends DLWindow {
    ItemStack stack;
    Schedule schedule;
    DLPanel leftPart;
    DLScrollBar scrollBar;
    int CardWidth = 202;
    public SimulatedScheduleWindow(DLWindowManager manager, ItemStack stack) {
        super(manager);
        this.schedule = new Schedule();
        CompoundTag tag = stack.getOrCreateTag()
                .getCompound("Schedule");
        if (!tag.isEmpty())
            schedule = Schedule.fromTag(tag);
        this.stack = stack;
        setSize(manager.getScreenWidth()-25, manager.getScreenHeight()-25);
        setPosition(12, 12);
        BorderLayout rootLayout = new BorderLayout(0, 0);
        rootLayout.setPadding(new Padding(0, 3, 3, 3));
        this.layout.set(rootLayout);

        // 2. Create and Add the Title Bar
        DLPanel titleBar = new DLPanel(0, 0, 0, 14);
        titleBar.backgroundTint.set(DLColor.fromHex("#FF333333").withAlpha(25));
        titleBar.layoutContraint.set(BorderLayout.BorderPosition.NORTH);

        FlowLayout titleLayout = new FlowLayout();
        titleLayout.flowDirection.set(FlowLayout.Direction.HORIZONTAL);
        titleLayout.fillCrossAxis.set(true);
        titleLayout.wrap.set(false);
        titleBar.layout.set(titleLayout);

        DLRichTextLabel titleText = new DLRichTextLabel(0, 0, 0, 0);
        RichTextComponent rtc = new RichTextComponent();
        rtc.append("text", new TextStyle.Builder().color(0xFFFFFFFF).build(), ETextAlignment.CENTER);
        titleText.text.set(rtc);
        titleText.contentPadding.set(new Padding(2, 0, 0, 0));
        titleText.layoutContraint.set(FlowLayout.FlowConstraint.FILL);
        titleBar.addComponent(titleText);

        this.addComponent(titleBar);

        // 3. Create the Main Content Area (The container for the split)
        DLPanel contentArea = new DLPanel(0, 0, 0, 0);
        contentArea.layoutContraint.set(BorderLayout.BorderPosition.CENTER);

        FlowLayout splitLayout = new FlowLayout();
        splitLayout.flowDirection.set(FlowLayout.Direction.HORIZONTAL);
        splitLayout.fillCrossAxis.set(true);
        splitLayout.wrap.set(false);
        contentArea.layout.set(splitLayout);

        this.addComponent(contentArea);

        // 4. Create the 3-Part Split inside the contentArea

        // Left Side - Blue
        //LeftSide Layout
        FlowLayout cardsLayout = new FlowLayout();
        cardsLayout.flowDirection.set(FlowLayout.Direction.VERTICAL);
        cardsLayout.fillCrossAxis.set(false);
        cardsLayout.wrap.set(false);
        cardsLayout.padding.set(new Padding(5, 15, 5, 10));
        //cardsLayout.verticalGap.set(5);



        DLPanel leftPart = new DLPanel(0, 0, 0, 0){
            @Override
            public Rectangle getChildRenderBounds() {
                return Rectangle.withSize(10, 5, width()-25, height()-10);
            }

        };
        this.leftPart = leftPart;
        leftPart.layoutContraint.set(FlowLayout.FlowConstraint.FILL);
        //leftPart.backgroundTint.set(DLColor.BLUE.withAlpha(40));
        leftPart.layout.set(cardsLayout);

        scrollBar = new DLScrollBar(leftPart.width()-4, 0, 6,0, DLScrollBar.Orientation.VERTICAL);
        scrollBar.layoutContraint.set(FlowLayout.FlowConstraint.START);
        scrollBar.max.set(15);
        scrollBar.addEventListener(DLScrollBar.ValueChangedEvent.class, (src, event) -> {
            // Update the container's scroll offset whenever the scrollbar moves
            leftPart.setScrollOffsetY(event.value());
            return false;
        });
        scrollBar.screenSize.set(leftPart.height()-cardsLayout.padding.get().top()-cardsLayout.padding.get().bottom());

        leftPart.addEventListener(DLGuiStandardEvents.ScrollEvent.class, (src, event) -> {
            // Forward the scroll wheel input to the scrollbar
            scrollBar.value.set(scrollBar.value.get() - (-event.deltaY()) * scrollBar.scrollSteps.get());
            return true;
        });

        //Schedule Cards
        for (ScheduleEntry entry : schedule.entries){
            ScheduleCard card = new ScheduleCard(0, 0, CardWidth, calculateHeight(entry), entry, schedule, this);
            scrollBar.max.set(scrollBar.max.get()+calculateHeight(entry));
            //card.layoutContraint.set(FlowLayout.FlowConstraint.START);
            leftPart.addComponent(card);
        }

        addInstructionButton();

        contentArea.addComponent(leftPart);
        contentArea.addComponent(scrollBar);

        // Right Container
        DLPanel rightContainer = new DLPanel(0, 0, 0, 0);
        rightContainer.layoutContraint.set(FlowLayout.FlowConstraint.FILL);
        FlowLayout rightLayout = new FlowLayout();
        rightLayout.flowDirection.set(FlowLayout.Direction.VERTICAL);
        rightLayout.fillCrossAxis.set(true);
        rightLayout.wrap.set(false);
        rightContainer.layout.set(rightLayout);
        contentArea.addComponent(rightContainer);

        // Right Top - Green
        DLPanel rightTop = new DLPanel(0, 0, 0, 0);
        rightTop.layoutContraint.set(FlowLayout.FlowConstraint.FILL);
        rightTop.backgroundTint.set(DLColor.GREEN.withAlpha(40));
        rightContainer.addComponent(rightTop);

        // Right Bottom - Yellow
        DLPanel rightBottom = new DLPanel(0, 0, 0, 0);
        rightBottom.layoutContraint.set(FlowLayout.FlowConstraint.FILL);
        rightBottom.backgroundTint.set(DLColor.YELLOW.withAlpha(40));
        rightContainer.addComponent(rightBottom);





    }

    @Override
    protected void updateScreenLayout() {
        scrollBar.screenSize.set(leftPart.height()-10);

    }


    @Override
    public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
        // This renders the rounded window background used by the ColorPicker
        DefaultGuiTextures.DRAGONLIB_UI.getSprite("window_rounded")
                .render(graphics, 0, 0, width(), height());

        // Optional: If you want to keep the background tints you set previously,
        // call super after rendering the texture (or don't call it if you want the texture only)
        super.renderMainLayer(graphics, mouseX, mouseY, renderBounds);
    }

    private int calculateHeight(ScheduleEntry entry) {
        if (!(entry.instruction.supportsConditions())) {
            return 26;
        }
        else {
            int maxRows = 0;
            for (List<ScheduleWaitCondition> list : entry.conditions)
                maxRows = Math.max(maxRows, list.size());
            if(maxRows==0) return 72;
            return 54+maxRows*18 ;
        }
    }

    public void resetCards(){
        leftPart.clearComponents();
        scrollBar.max.set(0);
        for (ScheduleEntry entry : schedule.entries){
            if (entry.instruction == null) {
                continue;}
            ScheduleCard card = new ScheduleCard(0, 0, CardWidth, calculateHeight(entry), entry, schedule,this);
            scrollBar.max.set(scrollBar.max.get()+calculateHeight(entry));
            //card.layoutContraint.set(FlowLayout.FlowConstraint.START);
            leftPart.addComponent(card);
        }
        addInstructionButton();


    }

    public void save() {
        CompoundTag tag = stack.getOrCreateTag();
        schedule.entries.removeIf(entryc -> entryc.instruction == null);
        tag.put("Schedule", schedule.write());
        stack.setTag(tag);
        RNetworking.sendToServer(new SaveAdvancedSchedule(stack));
    }

    public void addInstructionButton() {
        DLButton newInstructionButton = new DLButton(50, 0, 16, 16){
            @Override
            public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
                AllGuiTextures.SCHEDULE_CARD_NEW.render(graphics.graphics(), 0, 0);
            }
        };
        newInstructionButton.addEventListener(DLGuiStandardEvents.ClickEvent.class, (src,event) -> {
            schedule.entries.add(new ScheduleEntry());
            schedule.entries.get(schedule.entries.size()-1).instruction = new DestinationInstruction();
            getWindowManager().createWindow(manager ->
                    new EditWindow(manager, schedule.entries.get(schedule.entries.size()-1),null, Pair.of(1,1),null,this));
            return false;
        });
        newInstructionButton.layoutContraint.set(FlowLayout.FlowConstraint.START);
        leftPart.addComponent(newInstructionButton);
    }



}
