package net.Realism.forge;

import net.minecraftforge.fml.ModList;

public class RealismExpectPlatformImpl {
	public static String platformName() {
		return "Forge";
	}

	public static boolean isForge() {
		return true;
	}

    public static boolean isModLoaded(String id){
        return ModList.get().isLoaded(id);
    }
}