package com.kingpixel.ultraeconomy.database;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.models.Account;
import com.kingpixel.ultraeconomy.models.Currency;
import com.kingpixel.ultraeconomy.models.Transaction;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoNamespace;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
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
  // Nombres de las colecciones
  private static final String TRANSACTIONS_COLLECTION = "transactions";
  private static final String ACCOUNTS_COLLECTION = "accounts";
  private static final String BACKUPS_COLLECTION = "backups";

  public static final String FIELD_UUID = "uuid";
  public static final String FIELD_PLAYER_NAME = "player_name";
  public static final String FIELD_BALANCES = "balances";
  private static final String FIELD_ACCOUNT_UUID = "account_uuid";
  private static final String FIELD_CURRENCY_ID = "currency_id";
  private static final String FIELD_AMOUNT = "amount";
  private static final String FIELD_TYPE = "type";
  private static final String FIELD_PROCESSED = "processed";
  private static final String FIELD_BACKUP_UUID = "uuid";

  private MongoDatabase database;
  private MongoClient mongoClient;
  private MongoCollection<Document> accountsCollection;
  private MongoCollection<Document> transactionsCollection;
  private MongoCollection<Document> backupsCollection;


  private ScheduledExecutorService transactionExecutor;
  private boolean runningTransactions = false;

  @Override
  public void connect(DataBaseConfig config) {
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
      backupsCollection = database.getCollection(BACKUPS_COLLECTION);
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
      account = Account.fromDocument(doc);
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
    Document accountDoc = account.toDocument();

    accountsCollection.replaceOne(
      Filters.eq(FIELD_UUID, account.getPlayerUUID().toString()),
      accountDoc,
      new ReplaceOptions().upsert(true)
    );
  }

  public void addTransaction(UUID uuid, Currency currency, BigDecimal amount, TransactionType type, boolean processed) {
    CompletableFuture.runAsync(() -> {
        Transaction transaction = new Transaction(uuid, currency.getId(), amount, type, processed, Instant.now());
        transactionsCollection.insertOne(transaction.toDocument());
      }, UltraEconomy.ULTRA_ECONOMY_EXECUTOR)
      .exceptionally(e -> {
        e.printStackTrace();
        return null;
      });
  }

  @Override
  public CompletableFuture<?> createBackUp() {
    return CompletableFuture.runAsync(() -> {
      try {
        List<Document> accounts = new ArrayList<>();
        for (Document doc : accountsCollection.find()) {
          accounts.add(doc);
        }

        List<Document> transactions = new ArrayList<>();
        for (Document doc : transactionsCollection.find()) {
          transactions.add(doc);
        }

        Document backup = new Document("created_at", Date.from(Instant.now()))
          .append("uuid", UUID.randomUUID().toString())
          .append("accounts", accounts)
          .append("transactions", transactions);

        backupsCollection.insertOne(backup);

        if (UltraEconomy.config.isDebug()) {
          CobbleUtils.LOGGER.info("📦 MongoDB backup created successfully");
        }

      } catch (Exception e) {
        CobbleUtils.LOGGER.error("❌ Error creating MongoDB backup");
        e.printStackTrace();
      }
      cleanOldBackUps();
    }, UltraEconomy.ULTRA_ECONOMY_EXECUTOR);
  }

  protected void cleanOldBackUps() {
    try {
      long millis = UltraEconomy.config.getRetentionBackUps().toMillis();

      Instant limit = Instant.now().minus(millis, TimeUnit.MILLISECONDS.toChronoUnit());
      Date limitDate = Date.from(limit);

      long deleted = backupsCollection.deleteMany(
        Filters.lt("created_at", limitDate)
      ).getDeletedCount();

      if (deleted > 0 && UltraEconomy.config.isDebug()) {
        CobbleUtils.LOGGER.info("🧹 Deleted " + deleted + " old backups");
      }
    } catch (Exception e) {
      CobbleUtils.LOGGER.error("❌ Error cleaning old backups");
      e.printStackTrace();
    }
  }

  @Override public List<Account> getAccounts(int limit, int page) {
    return accountsCollection.find().limit(limit)
      .skip((Math.max(page - 1, 0)) * limit)
      .map(Account::fromDocument)
      .into(new ArrayList<>());
  }

  @Override public List<Transaction> getTransactions(UUID uuid, int limit) {
    var filter = Filters.eq(FIELD_ACCOUNT_UUID, uuid.toString());
    return transactionsCollection.find(filter)
      .limit(limit)
      .map(Transaction::fromDocument)
      .into(new ArrayList<>());
  }

  @Override public Account getAccountByName(String name) {
    var filter = Filters.eq(FIELD_PLAYER_NAME, name);
    Document doc = accountsCollection.find(filter).first();
    if (doc != null) {
      return Account.fromDocument(doc);
    }
    return null;
  }


  @Override
  public void loadBackUp(UUID backupUUID) {
    CompletableFuture.runAsync(() -> {
      try {
        Document backup = backupsCollection.find(
          Filters.eq(FIELD_BACKUP_UUID, backupUUID.toString())
        ).first();

        if (backup == null) {
          CobbleUtils.LOGGER.warn("⚠ Backup not found: " + backupUUID);
          return;
        }

        List<Document> accounts = backup.getList("accounts", Document.class);
        List<Document> transactions = backup.getList("transactions", Document.class);

        if (accounts == null || transactions == null) {
          throw new IllegalStateException("Backup corrupted");
        }


        String accTmp = "accounts_restore_tmp";
        String txTmp = "transactions_restore_tmp";

        database.getCollection(accTmp).drop();
        database.getCollection(txTmp).drop();

        MongoCollection<Document> tmpAccounts = database.getCollection(accTmp);
        MongoCollection<Document> tmpTx = database.getCollection(txTmp);

        tmpAccounts.insertMany(accounts);
        tmpTx.insertMany(transactions);

        tmpAccounts.renameCollection(
          new MongoNamespace(database.getName(), ACCOUNTS_COLLECTION),
          new RenameCollectionOptions().dropTarget(true)
        );

        tmpTx.renameCollection(
          new MongoNamespace(database.getName(), TRANSACTIONS_COLLECTION),
          new RenameCollectionOptions().dropTarget(true)
        );

        DatabaseFactory.CACHE_ACCOUNTS.invalidateAll();

        CobbleUtils.LOGGER.info("♻ Backup restored successfully: " + backupUUID);

      } catch (Exception e) {
        CobbleUtils.LOGGER.error("❌ Error restoring backup: " + backupUUID);
        e.printStackTrace();
      }
    }, UltraEconomy.ULTRA_ECONOMY_EXECUTOR);
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
          transactionsCollection.updateOne(
            Filters.eq("_id", tx.getObjectId("_id")),
            Updates.set(FIELD_PROCESSED, false)
          );
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
          case DEPOSIT -> UltraEconomyApi.deposit(uuid, currency.getId(), amount);
          case WITHDRAW -> UltraEconomyApi.withdraw(uuid, currency.getId(), amount);
          case SET -> UltraEconomyApi.setBalance(uuid, currency.getId(), amount);
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
      if (UltraEconomy.config.isDebug()) {
        CobbleUtils.LOGGER.warn(UltraEconomy.MOD_ID, "Account not found in cache for UUID: " + uuid + ", queuing transaction.");
      }
      addTransaction(uuid, currency, amount, TransactionType.DEPOSIT, false);
    } else {
      if (UltraEconomy.config.isDebug()) {
        CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Account found in cache for UUID: " + uuid + ", adding balance.");
      }
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
      if (UltraEconomy.config.isDebug()) {
        CobbleUtils.LOGGER.warn(UltraEconomy.MOD_ID, "Account not found in cache for UUID: " + uuid + ", queuing transaction.");
      }
      addTransaction(uuid, currency, amount, TransactionType.WITHDRAW, false);
    } else {
      if (UltraEconomy.config.isDebug()) {
        CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Account found in cache for UUID: " + uuid + ", removing balance.");
      }
      result = account.removeBalance(currency, amount);
      addTransaction(uuid, currency, amount, TransactionType.WITHDRAW, true);
    }
    return result;
  }

  @Override
  public BigDecimal setBalance(UUID uuid, Currency currency, BigDecimal amount) {
    Account account = getCachedAccount(uuid);
    if (account == null) {
      if (UltraEconomy.config.isDebug()) {
        CobbleUtils.LOGGER.warn(UltraEconomy.MOD_ID, "Account not found in cache for UUID: " + uuid + ", queuing transaction.");
      }
      addTransaction(uuid, currency, amount, TransactionType.SET, false);
    } else {
      if (UltraEconomy.config.isDebug()) {
        CobbleUtils.LOGGER.info(UltraEconomy.MOD_ID, "Account found in cache for UUID: " + uuid + ", setting balance.");
      }
      account.setBalance(currency, amount);
      addTransaction(uuid, currency, amount, TransactionType.SET, true);
    }
    var filter = Filters.eq(FIELD_UUID, uuid.toString());
    var update = Updates.set(FIELD_BALANCES + "." + currency.getId(), new Decimal128(amount));
    accountsCollection.updateOne(filter, update);
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
      Account account = Account.fromDocument(doc);
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


  public Account getCachedAccount(UUID uuid) {
    return DatabaseFactory.CACHE_ACCOUNTS.getIfPresent(uuid);
  }
}
