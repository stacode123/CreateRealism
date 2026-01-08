package net.Realism.content.gui.schedule;


import de.mrjulsen.mcdragonlib.client.gui.container.DLSlot;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLGuiComponent;
import de.mrjulsen.mcdragonlib.menu.PlayerInventoryContainerMenu;

public class DLHotbarComponent<T extends PlayerInventoryContainerMenu> extends DLGuiComponent {

    public DLHotbarComponent(int x, int y, T menu) {
        super(x, y, 18 * 9, 18);

        // Loop only through the hotbar slots (the last 9 slots)
        for (int i = 27; i < 36; i++) {
            int hotbarIndex = i - 27;
            DLSlot slot = new DLSlot(
                    hotbarIndex * 18, 0, // Position
                    18, 18,             // Size
                    menu.slots.get(i),   // Slot from menu
                    menu
            );
            addComponent(slot);
        }
    }
}