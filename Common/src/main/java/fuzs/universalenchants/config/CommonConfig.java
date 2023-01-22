package fuzs.universalenchants.config;

import fuzs.puzzleslib.config.ConfigCore;
import fuzs.puzzleslib.config.annotation.Config;

public class CommonConfig implements ConfigCore {
    @Config(description = "To return to the vanilla /enchant command (in case of mod incompatibilities), make sure all settings in this category are disabled.")
    public final EnchantCommandConfig enchantCommand = new EnchantCommandConfig();
    @Config(description = {"When generating new enchantment config files, also generate files for modded enchantments.", "This feature is EXPERIMENTAL at the moment and very much untested.", "Regardless of this option, config files for modded enchantments can still be read if present."})
    public boolean generateModdedEnchantmentConfigs = false;
    
    public static class EnchantCommandConfig implements ConfigCore {
        @Config(description = {"Allow overriding and removing (by setting the level to 0) existing enchantment levels via the /enchant command.", "Additionally makes enchanting books work via the command."})
        public boolean fixEnchantCommand = true;
        @Config(description = "Remove the max level limit from the /enchant command, this allows any enchantment to be applied with a level of 1-255.")
        public boolean removeMaxLevelLimit = true;
    }
}
