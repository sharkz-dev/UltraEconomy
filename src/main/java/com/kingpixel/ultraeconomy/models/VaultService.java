package com.kingpixel.ultraeconomy.models;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.ultraeconomy.UltraEconomy;
import lombok.Data;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.List;

@Data
public class VaultService {
  public static Economy service;
  private static Boolean present;

  public static boolean isPresent() {
    if (present != null) return present;
    present = false;
    try {
      if (Bukkit.getServer().getPluginManager().getPlugin("Vault") == null) {
        CobbleUtils.LOGGER.info("Cannot find Vault!");
        List<String> plugins = new ArrayList<>();
        for (Plugin plugin : Bukkit.getServer().getPluginManager().getPlugins()) {
          plugins.add(plugin.getName());
        }
        CobbleUtils.LOGGER.info("Report this to zonary123 Plugins to Vault -> " + plugins);
      } else {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
          CobbleUtils.LOGGER.info("Registered Service Provider for Economy.class not found");
        } else {
          service = rsp.getProvider();
          CobbleUtils.LOGGER.info("Economy successfully hooked up");
          CobbleUtils.LOGGER.info("Economy: " + service.getName());
          present = true;
        }
      }
    } catch (Exception | NoClassDefFoundError | NoSuchMethodError e) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "This error can be ignored if you are not using Vault:");
      e.printStackTrace();
    }
    return present;
  }

  public static Economy getEconomy() {
    RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
    if (rsp == null) return null;
    return rsp.getProvider();
  }
}
