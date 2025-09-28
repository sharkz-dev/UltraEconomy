package com.kingpixel.ultraeconomy.database;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.models.Account;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.Decimal128;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MongoDBClient extends DatabaseClient {

  private MongoClient mongoClient;
  private MongoCollection<Document> accountsCollection;
  private MongoCollection<Document> transactionsCollection;

  private ScheduledExecutorService transactionExecutor;
  private boolean runningTransactions = false;

  @Override
  public void connect(DataBaseConfig config) {
    MongoDatabase database;
    try {
      mongoClient = MongoClients.create(
        MongoClientSettings.builder()
          .applyConnectionString(new ConnectionString(config.getUrl()))
          .applicationName("UltraEconomy-MongoDB")
          .build()
      );
      database = mongoClient.getDatabase(config.getDatabase());

      accountsCollection = database.getCollection("accounts");
      transactionsCollection = database.getCollection("transactions");

      // asegurar índices
      ensureIndexes();

      // iniciar executor
      transactionExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Mongo-Transaction-Worker");
        t.setDaemon(true);
        return t;
      });
      runningTransactions = true;
      transactionExecutor.scheduleAtFixedRate(this::checkAndApplyTransactions, 0, 2, TimeUnit.SECONDS);

      CobbleUtils.LOGGER.info("Connected to MongoDB at " + config.getUrl());
    } catch (Exception e) {
      CobbleUtils.LOGGER.error("❌ Could not connect to MongoDB: " + e.getMessage());
      mongoClient = null;
      database = null;
    }
  }

  private void ensureIndexes() {
    try {
      Set<String> existingIndexes = new HashSet<>();
      for (Document index : accountsCollection.listIndexes()) {
        existingIndexes.add(index.get("name", String.class));
      }

      if (!existingIndexes.contains("uuid_1")) {
        accountsCollection.createIndex(new Document("uuid", 1));
      }

      existingIndexes.clear();
      for (Document index : transactionsCollection.listIndexes()) {
        existingIndexes.add(index.get("name", String.class));
      }

      if (!existingIndexes.contains("account_uuid_1")) {
        transactionsCollection.createIndex(new Document("account_uuid", 1));
      }
      if (!existingIndexes.contains("currency_id_1")) {
        transactionsCollection.createIndex(new Document("currency_id", 1));
      }
      if (!existingIndexes.contains("processed_1")) {
        transactionsCollection.createIndex(new Document("processed", 1));
      }

      CobbleUtils.LOGGER.info("Indexes verified/created successfully.");
    } catch (Exception e) {
      CobbleUtils.LOGGER.error("Error ensuring MongoDB indexes: " + e.getMessage());
    }
  }

  @Override
  public void disconnect() {
    DatabaseFactory.CACHE_ACCOUNTS.invalidateAll();
    if (transactionExecutor != null) {
      runningTransactions = false;
      CobbleUtils.shutdownAndAwait(transactionExecutor);
    }
    if (mongoClient != null) {
      mongoClient.close();
      CobbleUtils.LOGGER.info("Disconnected from MongoDB.");
    }
  }

  @Override
  public void invalidate(UUID playerUUID) {
    DatabaseFactory.CACHE_ACCOUNTS.invalidate(playerUUID);
  }

  @Override
  public boolean isConnected() {
    return mongoClient != null;
  }

  @Override
  public Account getAccount(UUID uuid) {
    Account cached = DatabaseFactory.CACHE_ACCOUNTS.getIfPresent(uuid);
    if (cached != null) return cached;

    Document doc = accountsCollection.find(Filters.eq("uuid", uuid.toString())).first();
    Account account;
    if (doc != null) {
      account = documentToAccount(doc);
    } else {
      var player = CobbleUtils.server.getPlayerManager().getPlayer(uuid);
      if (player != null) {
        account = new Account(player);
        saveOrUpdateAccount(account);
      } else {
        CobbleUtils.LOGGER.warn("Could not find player with UUID " + uuid);
        return null;
      }
    }

    DatabaseFactory.CACHE_ACCOUNTS.put(uuid, account);
    return account;
  }

  @Override
  public void saveOrUpdateAccount(Account account) {
    CompletableFuture.runAsync(() -> saveAccount(account), UltraEconomy.ULTRA_ECONOMY_EXECUTOR)
      .exceptionally(e -> {
        e.printStackTrace();
        return null;
      });
  }

  @Override public void saveOrUpdateAccountSync(Account account) {
    saveAccount(account);
  }

  private void saveAccount(Account account) {
    Document balances = new Document();
    account.getBalances().forEach((k, v) -> {
      balances.append(k, new Decimal128(v)); // ✅ siempre Decimal128
    });
    Document doc = new Document("uuid", account.getPlayerUUID().toString())
      .append("player_name", account.getPlayerName())
      .append("balances", balances);

    accountsCollection.replaceOne(
      Filters.eq("uuid", account.getPlayerUUID().toString()),
      doc,
      new ReplaceOptions().upsert(true)
    );
  }

  private void addTransaction(UUID uuid, String currency, BigDecimal amount, TransactionType type, boolean processed) {
    CompletableFuture.runAsync(() -> {
        Document tx = new Document("account_uuid", uuid.toString())
          .append("currency_id", currency)
          .append("amount", new Decimal128(amount)) // ✅ siempre Decimal128
          .append("type", type.name())
          .append("processed", processed)
          .append("timestamp", Date.from(Instant.now()));
        transactionsCollection.insertOne(tx);
      }, UltraEconomy.ULTRA_ECONOMY_EXECUTOR)
      .exceptionally(e -> {
        e.printStackTrace();
        return null;
      });
  }

  private void checkAndApplyTransactions() {
    if (!runningTransactions || UltraEconomy.server == null) return;

    var players = UltraEconomy.server.getPlayerManager().getPlayerList();
    List<String> uuids = players.stream()
      .map(p -> p.getUuidAsString())
      .toList();

    if (uuids.isEmpty()) return;

    try {
      Document tx;
      // Continuar procesando mientras existan transacciones no procesadas
      while ((tx = transactionsCollection.findOneAndUpdate(
        Filters.and(
          Filters.eq("processed", false),
          Filters.in("account_uuid", uuids)
        ),
        Updates.set("processed", true)
      )) != null) {

        UUID uuid = UUID.fromString(tx.getString("account_uuid"));
        Account account = getCachedAccount(uuid);
        if (account == null) {
          if (UltraEconomy.config.isDebug()) {
            CobbleUtils.LOGGER.warn("Account not found in cache for transaction: " + tx.toJson());
          }
          continue;
        }

        String currency = tx.getString("currency_id");

        BigDecimal amount;
        Object rawAmount = tx.get("amount");
        if (rawAmount instanceof Decimal128 dec) {
          amount = dec.bigDecimalValue();
        } else if (rawAmount instanceof String str) {
          amount = new BigDecimal(str);
        } else {
          CobbleUtils.LOGGER.error("Unexpected amount type: " + rawAmount);
          continue;
        }

        TransactionType type;
        try {
          type = TransactionType.valueOf(tx.getString("type"));
        } catch (IllegalArgumentException ex) {
          CobbleUtils.LOGGER.error("Invalid transaction type: " + tx.toJson());
          ex.printStackTrace();
          continue;
        }

        switch (type) {
          case DEPOSIT -> account.addBalance(currency, amount);
          case WITHDRAW -> account.removeBalance(currency, amount);
          case SET -> account.setBalance(currency, amount);
        }

        saveOrUpdateAccount(account);
        DatabaseFactory.CACHE_ACCOUNTS.put(uuid, account);

        if (CobbleUtils.config.isDebug()) {
          CobbleUtils.LOGGER.info("Processed transaction: " + tx.toJson());
        }
      }
    } catch (Exception e) {
      CobbleUtils.LOGGER.error("Error processing transactions");
      e.printStackTrace();
    }
  }


  @Override
  public boolean addBalance(UUID uuid, String currency, BigDecimal amount) {
    Account account = getCachedAccount(uuid);
    boolean result = true;
    if (account == null) {
      addTransaction(uuid, currency, amount, TransactionType.DEPOSIT, false);
    } else {
      result = account.addBalance(currency, amount);
      if (result) addTransaction(uuid, currency, amount, TransactionType.DEPOSIT, true);
    }
    return result;
  }

  @Override
  public boolean removeBalance(UUID uuid, String currency, BigDecimal amount) {
    Account account = getCachedAccount(uuid);
    boolean result = true;
    if (account == null) {
      addTransaction(uuid, currency, amount, TransactionType.WITHDRAW, false);
    } else {
      result = account.removeBalance(currency, amount);
      addTransaction(uuid, currency, amount, TransactionType.WITHDRAW, true);
    }
    return result;
  }

  @Override
  public BigDecimal setBalance(UUID uuid, String currency, BigDecimal amount) {
    Account account = getCachedAccount(uuid);
    if (account == null) {
      addTransaction(uuid, currency, amount, TransactionType.SET, false);
    } else {
      account.setBalance(currency, amount);
      addTransaction(uuid, currency, amount, TransactionType.SET, true);
    }
    return amount;
  }

  @Override
  public BigDecimal getBalance(UUID uuid, String currency) {
    return getAccount(uuid).getBalance(currency);
  }

  @Override
  public boolean hasEnoughBalance(UUID uuid, String currency, BigDecimal amount) {
    return getAccount(uuid).hasEnoughBalance(currency, amount);
  }

  @Override
  public List<Account> getTopBalances(String currency, int page, int playersPerPage) {
    List<Account> topAccounts = new ArrayList<>();
    int skip = (page - 1) * playersPerPage;
    int index = skip + 1;

    FindIterable<Document> docs = accountsCollection.find()
      .sort(new Document("balances." + currency, -1)) // ✅ ordena por Decimal128
      .skip(skip)
      .limit(playersPerPage + 1);

    for (Document doc : docs) {
      Account account = documentToAccount(doc);
      account.setRank(index);
      index++;
      topAccounts.add(account);

      DatabaseFactory.CACHE_ACCOUNTS.put(account.getPlayerUUID(), account);
    }

    return topAccounts;
  }

  @Override public boolean existPlayerWithUUID(UUID uuid) {
    Document doc = accountsCollection.find(Filters.eq("uuid", uuid.toString())).first();
    return doc != null;
  }


  public Account documentToAccount(Document doc) {
    UUID uuid = UUID.fromString(doc.getString("uuid"));
    String playerName = doc.getString("player_name");

    Map<String, BigDecimal> balances = new HashMap<>();
    Document balanceDoc = doc.get("balances", Document.class);

    if (balanceDoc != null) {
      for (String key : balanceDoc.keySet()) {
        Object rawValue = balanceDoc.get(key);

        if (rawValue instanceof Decimal128 dec) {
          balances.put(key, dec.bigDecimalValue());
        } else if (rawValue instanceof String str) {
          try {
            balances.put(key, new BigDecimal(str));
          } catch (NumberFormatException e) {
            CobbleUtils.LOGGER.warn("Invalid balance format for " + key + " in account " + uuid + ": " + str);
          }
        }
      }
    }

    return new Account(uuid, playerName, balances);
  }

  public Account getCachedAccount(UUID uuid) {
    var account = DatabaseFactory.CACHE_ACCOUNTS.getIfPresent(uuid);
    if (account == null) {
      if (UltraEconomy.config.isDebug()) {
        CobbleUtils.LOGGER.warn("Account not found in cache for UUID: " + uuid);
      }
    } else {
      if (UltraEconomy.config.isDebug()) {
        CobbleUtils.LOGGER.info("Account found in cache for UUID: " + uuid);
      }
    }
    return account;
  }
}
