package com.kingpixel.ultraeconomy;

import com.kingpixel.ultraeconomy.mixins.UserCacheMixin;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * @author Carlos Varas Alonso - 28/09/2025 4:37
 */
public class UltraEconomyMixinPlugin implements IMixinConfigPlugin {

  @Override
  public void onLoad(String mixinPackage) {
    // This is not needed for now
  }

  @Override
  public String getRefMapperConfig() {
    return null;
  }


  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {

    if (mixinClassName.equals(UserCacheMixin.class.getName())) {
      return true;
    }

    if (mixinClassName.contains("Impactor")) {
      return FabricLoader.getInstance().isModLoaded("impactor");
    }

    if (mixinClassName.contains("Beconomy")
      || mixinClassName.contains("BlanketEconomyAPI")) {
      return FabricLoader.getInstance().isModLoaded("beconomy");
    }

    if (mixinClassName.contains("CobbleDollars")) {
      return FabricLoader.getInstance().isModLoaded("cobbledollars");
    }

    // Por defecto NO aplicar
    return false;
  }


  @Override
  public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    // This is not needed for now
  }


  @Override
  public List<String> getMixins() {
    return List.of();
  }

  @Override
  public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    // This is not needed for now
  }

  @Override
  public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    // This is not needed for now
  }
}
