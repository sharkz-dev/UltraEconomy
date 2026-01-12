package com.kingpixel.ultraeconomy.database;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.cobbleutils.util.Utils;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.models.Account;
import com.kingpixel.ultraeconomy.models.Currency;
import com.kingpixel.ultraeconomy.models.Transaction;
import com.mojang.authlib.GameProfile;
import net.minecraft.util.UserCache;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class JSONClient extends DatabaseClient {
  private static final String PATH = UltraEconomy.PATH + "/accounts/";
  private static final String FILE_SUFFIX = ".json";

  @Override
  public void connect(DataBaseConfig config) {
    Utils.getAbsolutePath(PATH).mkdirs();
    CobbleUtils.LOGGER.info("Using JSON database at " + PATH);
  }

  @Override
  public void disconnect() {
    CobbleUtils.LOGGER.info("JSON database does not require disconnection.");
  }

  @Override
  public void invalidate(UUID playerUUID) {
    DatabaseFactory.ACCOUNTS.invalidate(playerUUID);
  }

  @Override
  public boolean isConnected() {
    return true;
  }

  @Override
  public Account getAccount(UUID uuid) {
    if (uuid == null) return null;
    Account account = DatabaseFactory.ACCOUNTS.getIfPresent(uuid);
    if (account != null) return account;
    File accountFile = Utils.getAbsolutePath(PATH + uuid.toString() + FILE_SUFFIX);
    if (accountFile.exists()) {
      try {
        String data = Utils.readFileSync(accountFile);
        account = Utils.newWithoutSpacingGson().fromJson(data, Account.class);
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      var player = CobbleUtils.server.getPlayerManager().getPlayer(uuid);
      if (player != null) {
        CobbleUtils.LOGGER.info("Creating new account for " + player.getName().getString());
      } else {
        CobbleUtils.LOGGER.warn("Could not find player with UUID " + uuid + ", account creation failed.");
        return null;
      }
      account = new Account(player);
      saveOrUpdateAccount(account);
    }
    if (account == null) {
      CobbleUtils.LOGGER.warn("Could not load or create account for player with UUID " + uuid);
      return null;
    }
    DatabaseFactory.ACCOUNTS.put(uuid, account);
    return account;
  }

  @Override
  public void saveOrUpdateAccount(Account account) {
    UltraEconomy.runAsync(() -> saveAccount(account));
  }

  @Override
  public void saveOrUpdateAccountSync(Account account) {
    saveAccount(account);
  }

  @Override
  protected void addTransaction(UUID uuid, Currency currency, BigDecimal amount, TransactionType type, boolean processed) {

  }

  @Override
  public CompletableFuture<?> createBackUp() {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void loadBackUp(UUID uuid) {

  }

  @Override
  protected void cleanOldBackUps() {

  }

  @Override public List<Account> getAccounts(int limit, int page) {
    CobbleUtils.LOGGER.warn("getAccounts is not supported in JSON database.");
    return null;
  }

  @Override public List<Transaction> getTransactions(UUID uuid, int limit) {
    return List.of();
  }

  @Override public Account getAccountByName(String name) {
    UserCache userCache = CobbleUtils.server.getUserCache();
    if (userCache == null) return null;
    GameProfile gameProfile = userCache.findByName(name).orElse(null);
    return getAccount(gameProfile != null ? gameProfile.getId() : null);
  }

  private void saveAccount(Account account) {
    String data = Utils.newWithoutSpacingGson().toJson(account, Account.class);
    File accountFile = Utils.getAbsolutePath(PATH + account.getPlayerUUID().toString() + FILE_SUFFIX);
    Utils.writeFileAsync(accountFile, data);
  }

  @Override
  public boolean addBalance(UUID uuid, Currency currency, BigDecimal amount) {
    return getAccount(uuid).addBalance(currency, amount);
  }

  @Override
  public boolean removeBalance(UUID uuid, Currency currency, BigDecimal amount) {
    return getAccount(uuid).removeBalance(currency, amount);
  }

  @Override
  public BigDecimal getBalance(UUID uuid, Currency currency) {
    return getAccount(uuid).getBalance(currency);
  }

  @Override
  public BigDecimal setBalance(UUID uuid, Currency currency, BigDecimal amount) {
    return getAccount(uuid).setBalance(currency, amount);
  }

  @Override
  public boolean hasEnoughBalance(UUID uuid, Currency currency, BigDecimal amount) {
    return getAccount(uuid).hasEnoughBalance(currency, amount);
  }

  @Override
  public List<Account> getTopBalances(Currency currency, int page, int playersPerPage) {
    CobbleUtils.LOGGER.warn("getTopBalances is not supported in JSON database.");
    return List.of();
  }

  @Override
  public boolean existPlayerWithUUID(UUID uuid) {
    return Utils.getAbsolutePath(PATH + uuid.toString() + FILE_SUFFIX).exists();
  }


}
