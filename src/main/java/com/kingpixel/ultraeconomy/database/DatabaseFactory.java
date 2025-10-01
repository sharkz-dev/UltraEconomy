package com.kingpixel.ultraeconomy.database;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.cobbleutils.Model.DataBaseType;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.exceptions.DatabaseConnectionException;
import com.kingpixel.ultraeconomy.models.Account;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DatabaseFactory {
  /**
   * Only use cache accounts for local database operations
   * 1 minute expiration after last access
   * Maximum size of 1000 accounts
   * On removal, save or update the account in the database
   */
  public static final Cache<@NotNull UUID, Account> CACHE_ACCOUNTS = Caffeine
    .newBuilder()
    .expireAfterAccess(1, TimeUnit.MINUTES)
    .maximumSize(1000)
    .removalListener((key, value, cause) -> {
      if (cause.equals(RemovalCause.REPLACED) || UltraEconomy.server.isStopping() || UltraEconomy.server.isStopped())
        return;
      if (value != null) {
        Account account = (Account) value;
        CobbleUtils.LOGGER.info("Saving account " + account.getPlayerUUID() + " to database (cause: " + cause + ")");
        DatabaseFactory.INSTANCE.saveOrUpdateAccount((Account) value);
      } else {
        CobbleUtils.LOGGER.warn("Tried to save null account to database (cause: " + cause + ")");
      }
    })
    .build();

  public static DatabaseClient INSTANCE;

  public static void init(DataBaseConfig config) {
    if (INSTANCE != null) INSTANCE.disconnect();
    switch (config.getType()) {
      case JSON -> INSTANCE = new JSONClient();
      case SQLITE, MYSQL, MARIADB, H2 -> INSTANCE = new SQLClient();
      case MONGODB -> INSTANCE = new MongoDBClient();
      default ->
        throw new DatabaseConnectionException("Unknown database type " + Arrays.toString(DataBaseType.values()));
    }
    INSTANCE.connect(config);
  }
}
