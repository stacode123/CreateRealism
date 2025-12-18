package net.Realism.mixin.mixinaccesors;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = Screen.class)
public interface ScreenAccessor {
    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & NarratableEntry> T realism$invokeAddRenderableWidget(T widget);

    @Invoker("addRenderableOnly")
    Renderable realism$invokeAddRenderableOnly(Renderable renderable);
}
