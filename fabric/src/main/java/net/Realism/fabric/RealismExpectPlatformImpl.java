package net.Realism.fabric;

import net.fabricmc.loader.api.FabricLoader;

public class RealismExpectPlatformImpl {
	public static String platformName() {
		return FabricLoader.getInstance().isModLoaded("quilt_loader") ? "Quilt" : "Fabric";
	}
}
