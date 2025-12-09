package net.Realism.datagen;

import com.google.gson.JsonElement;
import com.simibubi.create.foundation.utility.FilesHelper;
import com.tterrag.registrate.providers.RegistrateLangProvider;
import net.Realism.RealismMod;
import net.createmod.ponder.foundation.PonderIndex;

import java.util.Map;

public class GenLang {
  public static void generator(RegistrateLangProvider provider) {
    PonderIndex.getLangAccess().provideLang(RealismMod.MOD_ID, provider::add);

    // defaults
    JsonElement elem = FilesHelper.loadJsonResource("assets/realism/lang/defaults.json");

    if (elem == null) {
      throw new RuntimeException("Couldn't find lang/defaults.json");
    }

    for (Map.Entry<String, JsonElement> entry : elem.getAsJsonObject().entrySet()) {
      provider.add(entry.getKey(), entry.getValue().getAsString());
    }
  }
}
