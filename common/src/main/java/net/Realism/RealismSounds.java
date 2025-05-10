package net.Realism;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import static net.Realism.RealismMod.MOD_ID;

public class RealismSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(MOD_ID, Registries.SOUND_EVENT);

    public static final RegistrySupplier<SoundEvent> ETCS_BEEP = registerSoundEvents("info");
    public static final RegistrySupplier<SoundEvent> ETCS_WARNING = registerSoundEvents("warning");


    private static RegistrySupplier<SoundEvent> registerSoundEvents(String name) {
        return SOUND_EVENTS.register(new ResourceLocation(MOD_ID, name), () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MOD_ID, name)));
    }
}