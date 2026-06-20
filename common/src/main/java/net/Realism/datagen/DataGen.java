package net.Realism.datagen;

import com.tterrag.registrate.providers.ProviderType;
import net.Realism.RealismMod;

public class DataGen {
  public static void register() {

     RealismMod.REGISTRATE.addDataGenerator(ProviderType.LANG, GenLang::generator);
  }

}
