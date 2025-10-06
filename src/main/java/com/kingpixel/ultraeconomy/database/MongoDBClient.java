package com.kingpixel.ultraeconomy.database;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.models.Account;
import com.kingpixel.ultraeconomy.models.Currency;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import net.minecraft.entity.Entity;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.types.Decimal128;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MongoDBClient extends DatabaseClient {
  private static final String TRANSACTIONS_COLLECTION = "transactions";
  private static final String ACCOUNTS_COLLECTION = "accounts";
  private static final String FIELD_UUID = "uuid";
  private static final String FIELD_PLAYER_NAME = "player_name";
  private static final String FIELD_BALANCES = "balances";
  private static final String FIELD_ACCOUNT_UUID = "account_uuid";
  private static final String FIELD_CURRENCY_ID = "currency_id";
  private static final String FIELD_AMOUNT = "amount";
  private static final String FIELD_TYPE = "type";
  private static final String FIELD_PROCESSED = "processed";

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
          .uuidRepresentation(UuidRepresentation.STANDARD)
          .applicationName("UltraEconomy-MongoDB")
          .build()
      );
      database = mongoClient.getDatabase(config.getDatabase());

      accountsCollection = database.getCollection(ACCOUNTS_COLLECTION);
      transactionsCollection = database.getCollection(TRANSACTIONS_COLLECTION);

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
    }
  }

  private void ensureIndexes() {
    try {
      Set<String> existingIndexes = new HashSet<>();
      for (Document index : accountsCollection.listIndexes()) {
        existingIndexes.add(index.get("name", String.class));
      }

      if (!existingIndexes.contains("uuid_1")) {
        accountsCollection.createIndex(new Document(FIELD_UUID, 1));
      }

      existingIndexes.clear();
      for (Document index : transactionsCollection.listIndexes()) {
        existingIndexes.add(index.get("name", String.class));
      }

      if (!existingIndexes.contains("account_uuid_1")) {
        transactionsCollection.createIndex(new Document(FIELD_ACCOUNT_UUID, 1));
      }
      if (!existingIndexes.contains("currency_id_1")) {
        transactionsCollection.createIndex(new Document(FIELD_CURRENCY_ID, 1));
      }
      if (!existingIndexes.contains("processed_1")) {
        transactionsCollection.createIndex(new Document(FIELD_PROCESSED, 1));
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

    Document doc = accountsCollection.find(Filters.eq(FIELD_UUID, uuid.toString())).first();
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

  @Override
  public void saveOrUpdateAccountSync(Account account) {
    saveAccount(account);
  }

  private void saveAccount(Account account) {
    Document balances = new Document();
    account.getBalances().forEach((k, v) -> balances.append(k, new Decimal128(v)));
    Document doc = new Document(FIELD_UUID, account.getPlayerUUID().toString())
      .append(FIELD_PLAYER_NAME, account.getPlayerName())
      .append(FIELD_BALANCES, balances);

    accountsCollection.replaceOne(
      Filters.eq(FIELD_UUID, account.getPlayerUUID().toString()),
      doc,
      new ReplaceOptions().upsert(true)
    );
  }

  private void addTransaction(UUID uuid, Currency currency, BigDecimal amount, TransactionType type, boolean processed) {
    CompletableFuture.runAsync(() -> {
        Document tx = new Document(FIELD_ACCOUNT_UUID, uuid.toString())
          .append(FIELD_CURRENCY_ID, currency.getId())
          .append(FIELD_AMOUNT, new Decimal128(amount))
          .append(FIELD_TYPE, type.name())
          .append(FIELD_PROCESSED, processed)
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
      .map(Entity::getUuidAsString)
      .toList();

    if (uuids.isEmpty()) return;

    try {
      Document tx;
      while ((tx = transactionsCollection.findOneAndUpdate(
        Filters.and(
          Filters.eq(FIELD_PROCESSED, false),
          Filters.in(FIELD_ACCOUNT_UUID, uuids)
        ),
        Updates.set(FIELD_PROCESSED, true)
      )) != null) {

        UUID uuid = UUID.fromString(tx.getString(FIELD_ACCOUNT_UUID));
        Account account = getCachedAccount(uuid);
        if (account == null) {
          if (UltraEconomy.config.isDebug()) {
            CobbleUtils.LOGGER.warn("Account not found in cache for transaction: " + tx.toJson());
          }
          continue;
        }

        String currencyId = tx.getString(FIELD_CURRENCY_ID);
        Currency currency = Currencies.getCurrency(currencyId);
        BigDecimal amount;
        Object rawAmount = tx.get(FIELD_AMOUNT);
        switch (rawAmount) {
          case String s -> amount = new BigDecimal(s);
          case Decimal128 d -> amount = d.bigDecimalValue();
          case Integer i -> amount = BigDecimal.valueOf(i);
          case Long l -> amount = BigDecimal.valueOf(l);
          case Double d -> amount = BigDecimal.valueOf(d);
          case Float f -> amount = BigDecimal.valueOf(f);
          default -> {
            CobbleUtils.LOGGER.error("Unknown amount type in transaction: " + tx.toJson());
            continue;
          }
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
          default -> {
            CobbleUtils.LOGGER.error("Unknown transaction type: " + type);
            continue;
          }
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
  public boolean addBalance(UUID uuid, Currency currency, BigDecimal amount) {
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
  public boolean removeBalance(UUID uuid, Currency currency, BigDecimal amount) {
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
  public BigDecimal setBalance(UUID uuid, Currency currency, BigDecimal amount) {
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
  public BigDecimal getBalance(UUID uuid, Currency currency) {
    return getAccount(uuid).getBalance(currency);
  }

  @Override
  public boolean hasEnoughBalance(UUID uuid, Currency currency, BigDecimal amount) {
    return getAccount(uuid).hasEnoughBalance(currency, amount);
  }

  @Override
  public List<Account> getTopBalances(Currency currency, int page, int playersPerPage) {
    List<Account> topAccounts = new ArrayList<>();
    int skip = Math.max(page - 1, 0) * playersPerPage;
    int index = skip + 1;

    String currencyKey = currency.getId();

    FindIterable<Document> docs = accountsCollection.find(Filters.exists("balances." + currencyKey, true))
      .sort(Sorts.descending("balances." + currencyKey))
      .skip(skip)
      .limit(playersPerPage + 1); // To know if there's a next page

    for (Document doc : docs) {
      Account account = documentToAccount(doc);
      account.setRank(index++);
      topAccounts.add(account);
    }

    return topAccounts;
  }


  @Override
  public boolean existPlayerWithUUID(UUID uuid) {
    Document doc = accountsCollection.find(Filters.eq(FIELD_UUID, uuid.toString())).first();
    return doc != null;
  }


  public Account documentToAccount(Document doc) {
    UUID uuid = UUID.fromString(doc.getString(FIELD_UUID));
    String playerName = doc.getString(FIELD_PLAYER_NAME);

    Map<String, BigDecimal> balances = new HashMap<>();
    Document balanceDoc = doc.get(FIELD_BALANCES, Document.class);

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
