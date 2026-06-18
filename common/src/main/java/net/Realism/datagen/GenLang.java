package net.Realism.datagen;

import com.google.gson.JsonElement;
import com.simibubi.create.foundation.utility.FilesHelper;
import net.Realism.RealismMod;

public class GenLang {
  public static void generator(Object provider) {
    // TODO: Fix for 1.21 ProviderType API - commented out for now
    /*
    PonderIndex.getLangAccess().provideLang(RealismMod.MOD_ID, provider::add);

    JsonElement elem = FilesHelper.loadJsonResource("assets/realism/lang/defaults.json");

    if (elem == null) {
      throw new RuntimeException("Couldn't find lang/defaults.json");
    }

    for (Map.Entry<String, JsonElement> entry : elem.getAsJsonObject().entrySet()) {
      ((java.util.function.BiConsumer<String, String>) provider).accept(entry.getKey(), entry.getValue().getAsString());
    }
    */
  }
}
