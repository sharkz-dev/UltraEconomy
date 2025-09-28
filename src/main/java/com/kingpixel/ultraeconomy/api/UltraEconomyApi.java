package com.kingpixel.ultraeconomy.api;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.command.suggests.CobbleUtilsSuggests;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import com.kingpixel.ultraeconomy.models.Account;
import com.kingpixel.ultraeconomy.models.Currency;
import net.minecraft.util.UserCache;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 23/09/2025 20:50
 */
public class UltraEconomyApi {
  /**
   * Get the account of a target by UUID
   *
   * @param playerUUID the target's UUID
   *
   * @return the account
   */
  public static Account getAccount(@NotNull UUID playerUUID) {
    //long start = System.currentTimeMillis();
    Account account = DatabaseFactory.INSTANCE.getAccount(playerUUID);
    //long end = System.currentTimeMillis();
    //if (UltraEconomy.config.isDebug()) {
    //  CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Get account with playerUUID took " + (end - start) + "ms");
    //}
    return account;
  }

  /**
   * Get the account of a target by name
   *
   * @param playerName the target's name
   *
   * @return the account
   */
  public static Account getAccount(@NotNull String playerName) {
    UserCache userCache = CobbleUtils.server.getUserCache();
    if (userCache == null) return null;
    var profile = userCache.findByName(playerName);
    return profile.map(gameProfile -> getAccount(gameProfile.getId())).orElse(null);
  }

  public static boolean withdraw(@NotNull UUID uuid, @NotNull String currency, @NotNull BigDecimal amount) {
    long start = System.currentTimeMillis();
    Currency c = getCurrency(currency);
    boolean result = DatabaseFactory.INSTANCE.withdraw(uuid, currency, amount);
    if (UltraEconomy.config.isNotifications()) {
      var message = UltraEconomy.lang.getMessageWithdraw();
      message.sendMessage(uuid, UltraEconomy.lang.getPrefix(), false, false, null,
        message.getRawMessage().replace("%amount%", c.format(amount)));
    }
    aggressiveSave(uuid);
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Withdraw took " + (end - start) + "ms");
    }
    return result;
  }

  private static void aggressiveSave(UUID playerUUID) {
    if (UltraEconomy.config.isAggressiveSave()) {
      var account = DatabaseFactory.CACHE_ACCOUNTS.getIfPresent(playerUUID);
      if (account != null) saveAccount(account);
    }
  }

  /**
   * Get a currency by its ID
   *
   * @param currency the currency ID
   *
   * @return the currency
   */
  private static Currency getCurrency(String currency) {
    Currency curr = Currencies.getCurrency(currency);
    if (curr == null) {
      throw new IllegalArgumentException("Invalid currency: " + currency);
    }
    return curr;
  }

  /**
   * Deposit an amount to a target's account
   *
   * @param uuid     the target's UUID
   * @param currency the currency
   * @param amount   the amount
   *
   * @return true if successful, false otherwise
   */
  public static boolean deposit(@NotNull UUID uuid, @NotNull String currency, @NotNull BigDecimal amount) {
    long start = System.currentTimeMillis();
    Currency c = getCurrency(currency);
    boolean result = DatabaseFactory.INSTANCE.deposit(uuid, currency, amount);
    if (UltraEconomy.config.isNotifications()) {
      var message = UltraEconomy.lang.getMessageDeposit();
      message.sendMessage(uuid, UltraEconomy.lang.getPrefix(), false, false, null,
        message.getRawMessage().replace("%amount%", c.format(amount)));
    }
    aggressiveSave(uuid);
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Deposit took " + (end - start) + "ms");
    }
    return result;
  }

  /**
   * Set a target's balance
   *
   * @param uuid     the target's UUID
   * @param currency the currency
   * @param amount   the amount
   *
   * @return the new balance, or null if the currency does not exist
   */
  public static BigDecimal setBalance(@NotNull UUID uuid, @NotNull String currency, BigDecimal amount) {
    long start = System.currentTimeMillis();
    Currency c = getCurrency(currency);
    BigDecimal result = DatabaseFactory.INSTANCE.setBalance(uuid, currency, amount);
    if (UltraEconomy.config.isNotifications()) {
      var message = UltraEconomy.lang.getMessageSetBalance();
      message.sendMessage(uuid, UltraEconomy.lang.getPrefix(), false, false, null,
        message.getRawMessage().replace("%amount%", c.format(amount)));
    }
    aggressiveSave(uuid);
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Get balance took " + (end - start) + "ms");
    }
    return result;
  }

  /**
   * Get a target's balance
   *
   * @param uuid     the target's UUID
   * @param currency the currency
   *
   * @return the balance, or null if the currency does not exist
   */
  public static @Nullable BigDecimal getBalance(@NotNull UUID uuid, @NotNull String currency) {
    //long start = System.currentTimeMillis();
    BigDecimal result = DatabaseFactory.INSTANCE.getBalance(uuid, currency);
    //long end = System.currentTimeMillis();
    //if (UltraEconomy.config.isDebug()) {
    //  CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Get balance took " + (end - start) + "ms");
    //}
    return result;
  }

  /**
   * Check if a target has enough balance
   *
   * @param uuid     the target's UUID
   * @param currency the currency
   * @param amount   the amount
   *
   * @return true if the target has enough balance
   */
  public static boolean hasEnoughBalance(@NotNull UUID uuid, @NotNull String currency, @NotNull BigDecimal amount) {
    long start = System.currentTimeMillis();
    Currency c = getCurrency(currency);
    var result = DatabaseFactory.INSTANCE.hasEnoughBalance(uuid, currency, amount);
    if (UltraEconomy.config.isNotifications() && !result) {
      var message = UltraEconomy.lang.getMessageNoMoney();
      message.sendMessage(uuid, UltraEconomy.lang.getPrefix(), false, false, null,
        message.getRawMessage().replace("%amount%", c.format(amount)));
    }
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "hasEnoughBalance took " + (end - start) + "ms");
    }
    return result;
  }


  public static boolean transfer(UUID executor, UUID target, String currency, BigDecimal amount) {
    long start = System.currentTimeMillis();
    Currency curr = getCurrency(currency);
    String nameTarget = CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.getPlayerNameWithUUID(target);
    String nameExecutor = CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.getPlayerNameWithUUID(executor);
    if (nameExecutor == null || nameExecutor.isEmpty() || nameTarget == null || nameTarget.isEmpty()) {
      if (UltraEconomy.config.isDebug()) {
        CobbleUtils.LOGGER.error(UltraEconomy.MOD_ID, "Executor or target name is null in transfer");
      }
      return false;
    }
    if (!curr.isTransferable()) {
      UltraEconomy.lang.getMessageCurrencyNotTransferable().sendMessage(
        executor,
        UltraEconomy.lang.getPrefix(),
        false
      );
      return false;
    }
    if (!hasEnoughBalance(executor, currency, amount)) {
      if (UltraEconomy.config.isDebug()) {
        CobbleUtils.LOGGER.error(UltraEconomy.MOD_ID, "Not enough balance for executor in transfer");
      }
      return false;
    }
    if (!withdraw(executor, currency, amount)) {
      deposit(executor, currency, amount);
      if (UltraEconomy.config.isDebug()) {
        CobbleUtils.LOGGER.error(UltraEconomy.MOD_ID, "Failed to withdraw from executor in transfer");
      }
      return false;
    }
    if (!deposit(target, currency, amount)) {
      deposit(executor, currency, amount);
      return false;
    }
    if (UltraEconomy.config.isNotifications()) {

      var messageSender = UltraEconomy.lang.getMessagePaySuccessSender();
      messageSender.sendMessage(executor, UltraEconomy.lang.getPrefix(), false, false, null,
        messageSender.getRawMessage()
          .replace("%amount%", curr.format(amount))
          .replace("%player%", nameTarget)
      );
      var messageReceiver = UltraEconomy.lang.getMessagePaySuccessReceiver();
      messageReceiver.sendMessage(target, UltraEconomy.lang.getPrefix(), false, false, null,
        messageReceiver.getRawMessage()
          .replace("%amount%", curr.format(amount))
          .replace("%player%", nameExecutor)
      );
    }
    aggressiveSave(executor);
    aggressiveSave(target);
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Pay took " + (end - start) + "ms");
    }
    return true;
  }

  /**
   * Save an account to the database (This is done automatically when modifying the account)
   *
   * @param account the account
   */
  public static void saveAccount(Account account) {
    long start = System.currentTimeMillis();
    DatabaseFactory.INSTANCE.saveOrUpdateAccount(account);
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Save account took " + (end - start) + "ms");
    }
  }

  public static void saveAccountSync(Account account) {
    long start = System.currentTimeMillis();
    DatabaseFactory.INSTANCE.saveOrUpdateAccountSync(account);
    long end = System.currentTimeMillis();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Save account sync took " + (end - start) + "ms");
    }
  }

  public static boolean existPlayerWithName(String target) {
    return DatabaseFactory.INSTANCE.existPlayerWithName(target);
  }
}
