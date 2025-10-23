package com.kingpixel.ultraeconomy;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.ultraeconomy.mixins.UserCacheMixin;
import com.kingpixel.ultraeconomy.models.VaultService;
import net.fabricmc.loader.api.FabricLoader;
import net.milkbowl.vault.economy.Economy;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * @author Carlos Varas Alonso - 28/09/2025 4:37
 */
public class UltraEconomyMixinPlugin implements IMixinConfigPlugin {
  public static

  @Override
  public void onLoad(String mixinPackage) {
    // This is not needed for now
  }

  @Override
  public String getRefMapperConfig() {
    return "";
  }

  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    if (UserCacheMixin.class.getName().equals(targetClassName)) {
      return true;
    }
    if (FabricLoader.getInstance().isModLoaded("impactor") && mixinClassName.contains("Impactor")) {
      return true;
    }
    if (FabricLoader.getInstance().isModLoaded("beconomy") && mixinClassName.contains("Beconomy") || mixinClassName.contains("BlanketEconomyAPI")) {
      return true;
    }
    if (VaultService.isPresent() && mixinClassName.contains("Vault")) {
      Economy economy = VaultService.service;
      if (economy != null && economy.getName().equals("UltraEconomy")) {
        CobbleUtils.LOGGER.info("Applying Vault mixins for UltraEconomy");
        return true;
      }
    }

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
