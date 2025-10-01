package com.kingpixel.ultraeconomy.models;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.EconomyUse;
import com.kingpixel.cobbleutils.api.EconomyApi;
import com.kingpixel.cobbleutils.command.suggests.CobbleUtilsSuggests;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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

        var playerUUIDs = CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.getPlayerNames();

        try {
          CobbleUtils.LOGGER.info("Waiting 10s before starting migration to let the server breathe...");
          Thread.sleep(TimeUnit.SECONDS.toMillis(10));
        } catch (InterruptedException e) {
          CobbleUtils.LOGGER.error("Migration sleep interrupted");
          e.printStackTrace();
          Thread.currentThread().interrupt();
          return;
        }

        for (var uuid : playerUUIDs) {
          var dataOpt = CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.getPlayer(uuid);

          if (dataOpt.isEmpty()) {
            CobbleUtils.LOGGER.warn("Could not find player with UUID " + uuid + ", skipping");
            continue;
          }

          var dataResultPlayer = dataOpt.get();
          var userModel = dataResultPlayer.user();

          // Obtener o crear cuenta
          var account = UltraEconomyApi.getAccount(userModel.getPlayerUUID());
          if (account == null) {
            account = new Account(userModel.getPlayerUUID(), userModel.getPlayerName());
            UltraEconomyApi.saveAccount(account);
            CobbleUtils.LOGGER.info("Created new account for player " + userModel.getPlayerName());
          }
          UltraEconomyApi.getAccount(account.getPlayerUUID());
          // Migrar balances
          for (int i = 0; i < economyUses.size(); i++) {
            var economyUse = economyUses.get(i);
            var currencyId = currencyIds.get(Math.min(i, currencyIds.size() - 1)); // evita IndexOutOfBounds

            BigDecimal balance = EconomyApi.getBalance(userModel.getPlayerUUID(), economyUse);

            if (balance == null) {
              CobbleUtils.LOGGER.warn("Balance for player " + userModel.getPlayerName() + " is null, skipping");
              continue;
            }
            if (balance.compareTo(BigDecimal.ZERO) <= 0) {
              CobbleUtils.LOGGER.info("Balance for player " + userModel.getPlayerName() + " is zero or negative, skipping");
              continue;
            }

            UltraEconomyApi.setBalance(userModel.getPlayerUUID(), currencyId, balance);
            CobbleUtils.LOGGER.info("Migrated " + balance + " " + currencyId + " for player " + userModel.getPlayerName());
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
