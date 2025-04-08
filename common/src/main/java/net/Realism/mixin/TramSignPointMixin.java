package net.Realism.mixin;

import com.simibubi.create.foundation.utility.Couple;
import net.Realism.Interfaces.ITramSignPoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import purplecreate.tramways.content.signs.TramSignPoint;

import java.util.Set;

@Mixin(value = TramSignPoint.class, remap = false)
public class TramSignPointMixin implements ITramSignPoint {
    @Shadow
    private Couple<Set<TramSignPoint.SignData>> sides;

    @Override
    public Couple<Set<TramSignPoint.SignData>> getSides(){
        return sides;
    }
}