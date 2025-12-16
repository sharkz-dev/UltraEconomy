package com.kingpixel.ultraeconomy.models;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.EconomyUse;
import com.kingpixel.cobbleutils.api.EconomyApi;
import com.kingpixel.cobbleutils.command.suggests.CobbleUtilsSuggests;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.mixins.UserCacheMixin;
import lombok.Data;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Data
public class MigrationConfig {
  private boolean active;
  private String economyId;
  private List<EconomyUse> economyUses;
  private List<String> currencyIds;

  public MigrationConfig() {
    active = false;
    economyId = "IMPACTOR";
    economyUses = List.of(
      new EconomyUse(
        "IMPACTOR",
        "impactor:dollars"
      )
    );
    currencyIds = List.of(
      "dollars"
    );
  }

  public void startMigration() {
    if (!active) {
      UltraEconomy.migrationDone = true;
      return;
    }

    CompletableFuture.runAsync(() -> {
        long start = System.currentTimeMillis();
        CobbleUtils.LOGGER.info("Migration started ->");
        CobbleUtils.LOGGER.info("Order of economy uses:");
        for (var use : economyUses) {
          CobbleUtils.LOGGER.info("- " + use.getEconomyId() + " -> " + use.getCurrency());
        }
        CobbleUtils.LOGGER.info("Order of UltraEconomy currencies:");
        for (var currencyId : currencyIds) {
          CobbleUtils.LOGGER.info("- " + currencyId);
        }

        var playerUUIDs = CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.getPlayerUUIDs();
        var userCache = UltraEconomy.server.getUserCache();
        Set<UUID> missingUUIDs = new HashSet<>();
        if (userCache != null) missingUUIDs = ((UserCacheMixin) userCache).getByUuid().keySet();
        Set<UUID> fusionUUIDs = new HashSet<>();
        fusionUUIDs.addAll(missingUUIDs);
        fusionUUIDs.addAll(playerUUIDs);

        for (var uuid : playerUUIDs) {
          // Obtener o crear cuenta
          var account = UltraEconomyApi.getAccount(uuid);
          if (account == null) {
            account = new Account(uuid);
            UltraEconomyApi.saveAccount(account);
          }
          UltraEconomyApi.getAccount(account.getPlayerUUID());
          // Migrar balances
          int size = economyUses.size();
          for (int i = 0; i < size; i++) {
            var economyUse = economyUses.get(i);
            var currencyId = currencyIds.get(Math.min(i, currencyIds.size() - 1)); // evita IndexOutOfBounds

            BigDecimal balance = EconomyApi.getBalance(uuid, economyUse);

            if (balance == null) {
              CobbleUtils.LOGGER.warn("Balance for player " + uuid + " is null, skipping");
              continue;
            }

            if (balance.compareTo(BigDecimal.ZERO) <= 0) {
              CobbleUtils.LOGGER.info("Balance for player " + uuid + " is zero or negative, skipping");
              continue;
            }

            UltraEconomyApi.setBalance(uuid, currencyId, balance);
            CobbleUtils.LOGGER.info("Migrated " + balance + " " + currencyId + " for player " + uuid);
          }

          UltraEconomyApi.saveAccount(account);
        }

        long end = System.currentTimeMillis();
        CobbleUtils.LOGGER.info("Migration took " + (end - start) + "ms. Migration finished.");
        active = false;

        try {
          UltraEconomy.config.writeConfig();
        } catch (Exception e) {
          CobbleUtils.LOGGER.error("Failed to write UltraEconomy config after migration");
          e.printStackTrace();
        }
        UltraEconomy.migrationDone = true;
      }, UltraEconomy.ULTRA_ECONOMY_EXECUTOR)
      .exceptionally(e -> {
        CobbleUtils.LOGGER.error("Migration failed with exception:");
        e.printStackTrace();
        UltraEconomy.migrationDone = true;
        return null;
      });
  }
}
