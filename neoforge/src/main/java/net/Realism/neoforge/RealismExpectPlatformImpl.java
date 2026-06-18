package net.Realism.neoforge;

import net.neoforged.fml.ModList;

public class RealismExpectPlatformImpl {
	public static String platformName() {
		return "NeoForge";
	}

	public static boolean isForge() {
		return true;
	}

    public static boolean isModLoaded(String id){
        return ModList.get().isLoaded(id);
    }
}