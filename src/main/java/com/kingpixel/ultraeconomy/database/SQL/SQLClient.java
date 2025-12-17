package com.kingpixel.ultraeconomy.database.SQL;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.cobbleutils.Model.DataBaseType;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.database.DatabaseClient;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import com.kingpixel.ultraeconomy.database.TransactionType;
import com.kingpixel.ultraeconomy.exceptions.DatabaseConnectionException;
import com.kingpixel.ultraeconomy.exceptions.UnknownAccountException;
import com.kingpixel.ultraeconomy.models.Account;
import com.kingpixel.ultraeconomy.models.Currency;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@EqualsAndHashCode(callSuper = true)
@Data
public class SQLClient extends DatabaseClient {
  private static final String KEY_AMOUNT = "amount";
  private DataBaseType dbType;
  private HikariDataSource dataSource;
  private ScheduledExecutorService transactionExecutor;
  private ExecutorService asyncExecutor;
  private boolean runningTransactions = false;


  @Override
  public void connect(DataBaseConfig config) {
    try {
      dbType = config.getType();
      SQLSentences.Data data = SQLSentences.configure();
      asyncExecutor = data.getService();
      dataSource = data.getDataSource();
      CobbleUtils.LOGGER.info("Connected to " + config.getType() + " database at " + config.getUrl());

      // Inicialización
      initTables(config.getType());
      createIndexes(); // Ya no necesitas ensureProcessedColumnExists() si lo incluyes en initTables

      transactionExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Transaction-Worker-UltraEconomy");
        t.setDaemon(true);
        return t;
      });

      runningTransactions = true;
      transactionExecutor.scheduleAtFixedRate(this::checkAndApplyTransactions, 0, 2, TimeUnit.SECONDS);

    } catch (Exception e) {
      throw new DatabaseConnectionException(config.getType().name());
    }
  }


  @Override
  public void disconnect() {
    runningTransactions = false;
    if (transactionExecutor != null) CobbleUtils.shutdownAndAwait(transactionExecutor);
    if (asyncExecutor != null) CobbleUtils.shutdownAndAwait(asyncExecutor);
    if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    CobbleUtils.LOGGER.info("Disconnected from database.");
  }

  @Override
  public void invalidate(UUID playerUUID) {
    DatabaseFactory.CACHE_ACCOUNTS.invalidate(playerUUID);
  }

  @Override
  public Account getAccount(UUID uuid) {
    Account cached = DatabaseFactory.CACHE_ACCOUNTS.getIfPresent(uuid);
    if (cached != null) return cached;

    try (Connection conn = dataSource.getConnection()) {
      Account account;
      try (PreparedStatement stmt = conn.prepareStatement(SQLSentences.selectAccountByUUID())) {
        stmt.setString(1, uuid.toString());
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
          Map<String, BigDecimal> balances = new HashMap<>();
          try (PreparedStatement balStmt = conn.prepareStatement(SQLSentences.selectBalancesByUUID())) {
            balStmt.setString(1, uuid.toString());
            ResultSet balRs = balStmt.executeQuery();
            while (balRs.next())
              balances.put(balRs.getString("currency_id"), balRs.getBigDecimal(KEY_AMOUNT));
          }
          account = new Account(uuid, rs.getString("player_name"), balances);
        } else {
          var player = CobbleUtils.server.getPlayerManager().getPlayer(uuid);
          if (player != null) {
            account = new Account(player);
            saveOrUpdateAccount(account);
          } else return null;
        }
      }
      DatabaseFactory.CACHE_ACCOUNTS.put(uuid, account);
      return account;
    } catch (SQLException e) {
      throw new UnknownAccountException(uuid);
    }
  }

  public void getAccountAsync(UUID uuid, Consumer<Account> callback) {
    asyncExecutor.submit(() -> callback.accept(getAccount(uuid)));
  }

  @Override
  public void saveOrUpdateAccount(Account account) {
    asyncExecutor.submit(() -> saveAccount(account));
  }

  @Override
  public void saveOrUpdateAccountSync(Account account) {
    saveAccount(account);
  }

  private void saveAccount(Account account) {
    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);
      try (PreparedStatement stmt = conn.prepareStatement(SQLSentences.insertAccount())) {
        stmt.setString(1, account.getPlayerUUID().toString());
        stmt.setString(2, account.getPlayerName());
        stmt.executeUpdate();
      }
      for (Map.Entry<String, BigDecimal> entry : account.getBalances().entrySet()) {
        try (PreparedStatement balStmt = conn.prepareStatement(SQLSentences.insertBalance())) {
          balStmt.setString(1, account.getPlayerUUID().toString());
          balStmt.setString(2, entry.getKey());
          balStmt.setBigDecimal(3, entry.getValue());
          balStmt.executeUpdate();
        }
      }
      conn.commit();
    } catch (SQLException e) {
      CobbleUtils.LOGGER.error("Error saving account " + account.getPlayerUUID());
      e.printStackTrace();
    }
  }

  @Override
  public boolean addBalance(UUID uuid, Currency currency, BigDecimal amount) {
    Account account = getCachedAccount(uuid);
    boolean result = false;
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
    boolean result = false;
    if (account == null) {
      addTransaction(uuid, currency, amount, TransactionType.WITHDRAW, false);
    } else {
      result = account.removeBalance(currency, amount);
      if (result) addTransaction(uuid, currency, amount, TransactionType.WITHDRAW, true);
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
      saveBalanceSafe(uuid, currency, amount);
    }
    return amount;
  }

  private void saveBalanceSafe(UUID uuid, Currency currency, BigDecimal amount) {
    asyncExecutor.submit(() -> {
      try {
        saveBalance(uuid, currency, amount);
        DatabaseFactory.CACHE_ACCOUNTS.invalidate(uuid);
      } catch (SQLException e) {
        CobbleUtils.LOGGER.error("Error saving balance for " + uuid);
        e.printStackTrace();
      }
    });
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
    int offset = (page - 1) * playersPerPage;

    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(SQLSentences.selectTopBalances())) {
      stmt.setString(1, currency.getId());
      stmt.setInt(2, playersPerPage);
      stmt.setInt(3, offset);
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        String playerName = rs.getString("player_name");
        BigDecimal amount = rs.getBigDecimal(KEY_AMOUNT);

        Map<String, BigDecimal> balances = new HashMap<>();
        balances.put(currency.getId(), amount);
        Account account = new Account(uuid, playerName, balances);
        topAccounts.add(account);
      }
    } catch (SQLException e) {
      CobbleUtils.LOGGER.error("Error fetching top balances");
      e.printStackTrace();
    }

    return topAccounts;
  }

  @Override
  public boolean existPlayerWithUUID(UUID uuid) {
    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(SQLSentences.selectAccountByUUID())) {
      stmt.setString(1, uuid.toString());
      ResultSet rs = stmt.executeQuery();
      return rs.next();
    } catch (SQLException e) {
      CobbleUtils.LOGGER.error("Error checking existence of player with UUID " + uuid);
      e.printStackTrace();
      return false;
    }
  }


  public void addTransaction(UUID uuid, Currency currency, BigDecimal amount, TransactionType type, boolean processed) {
    asyncExecutor.submit(() -> {
      String query = SQLSentences.insertTransaction();
      try (Connection conn = dataSource.getConnection();
           PreparedStatement stmt = conn.prepareStatement(query)) {
        stmt.setString(1, uuid.toString());
        stmt.setString(2, currency.getId());
        stmt.setBigDecimal(3, amount);
        stmt.setString(4, type.name());
        stmt.setBoolean(5, processed);
        stmt.executeUpdate();
      } catch (SQLException e) {
        CobbleUtils.LOGGER.error("Error adding transaction for " + uuid);
        e.printStackTrace();
      }
    });
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

  @Override public List<Account> getAccounts(int limit) {
    return List.of();
  }

  private void checkAndApplyTransactions() {
    if (!runningTransactions) return;

    asyncExecutor.submit(() -> {
      try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(SQLSentences.selectPendingTransactions())) {

        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
          UUID uuid = UUID.fromString(rs.getString("account_uuid"));
          Account account = DatabaseFactory.CACHE_ACCOUNTS.getIfPresent(uuid);
          if (account == null) continue;

          long id = rs.getLong("id");
          String currencyId = rs.getString("currency_id");
          Currency currency = Currencies.getCurrency(currencyId);

          BigDecimal amount = rs.getBigDecimal(KEY_AMOUNT);
          TransactionType type = rs.getString("type") != null ? TransactionType.valueOf(rs.getString("type")) : TransactionType.DEPOSIT;

          switch (type) {
            case DEPOSIT -> UltraEconomyApi.deposit(uuid, currency.getId(), amount);
            case WITHDRAW -> UltraEconomyApi.withdraw(uuid, currency.getId(), amount);
            case SET -> UltraEconomyApi.setBalance(uuid, currency.getId(), amount);
            default -> {
              CobbleUtils.LOGGER.warn("Unknown transaction type for transaction ID " + id);
              continue;
            }
          }
          saveBalanceSafe(uuid, currency, account.getBalance(currency));

          try (PreparedStatement update = conn.prepareStatement(SQLSentences.markTransactionProcessed())) {
            update.setLong(1, id);
            update.executeUpdate();
          }

          DatabaseFactory.CACHE_ACCOUNTS.put(uuid, account);
        }
      } catch (SQLException e) {
        CobbleUtils.LOGGER.error("Error processing transactions");
        e.printStackTrace();
      }
    });
  }

  // Métodos privados para inicialización de tablas, índices y columna processed
  private void initTables(DataBaseType type) throws SQLException {
    try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {

      // Accounts table
      String accountTable = switch (type) {
        case SQLITE -> """
          CREATE TABLE IF NOT EXISTS accounts (
              uuid TEXT PRIMARY KEY,
              player_name TEXT NOT NULL
          )
          """;
        case MYSQL, MARIADB, H2 -> """
          CREATE TABLE IF NOT EXISTS accounts (
              uuid VARCHAR(36) PRIMARY KEY,
              player_name VARCHAR(64) NOT NULL
          )
          """;
        default -> throw new IllegalArgumentException("Unsupported database type for table creation: " + type);
      };
      stmt.executeUpdate(accountTable);

      // Balances table
      String balanceTable = switch (type) {
        case SQLITE -> """
          CREATE TABLE IF NOT EXISTS balances (
              account_uuid TEXT NOT NULL,
              currency_id TEXT NOT NULL,
              amount TEXT NOT NULL,
              PRIMARY KEY(account_uuid, currency_id),
              FOREIGN KEY(account_uuid) REFERENCES accounts(uuid) ON DELETE CASCADE
          )
          """;
        case MYSQL, MARIADB, H2 -> """
          CREATE TABLE IF NOT EXISTS balances (
              account_uuid VARCHAR(36) NOT NULL,
              currency_id VARCHAR(64) NOT NULL,
              amount DECIMAL(36,18) NOT NULL,
              PRIMARY KEY(account_uuid, currency_id),
              FOREIGN KEY(account_uuid) REFERENCES accounts(uuid) ON DELETE CASCADE
          )
          """;
        default -> throw new IllegalArgumentException("Unsupported database type for table creation: " + type);
      };
      stmt.executeUpdate(balanceTable);

      // Transactions table
      String transactionTable = switch (type) {
        case SQLITE -> """
          CREATE TABLE IF NOT EXISTS transactions (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              account_uuid TEXT NOT NULL,
              currency_id TEXT NOT NULL,
              amount TEXT NOT NULL,
              type TEXT NOT NULL,
              timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
              processed INTEGER DEFAULT 0,
              FOREIGN KEY(account_uuid) REFERENCES accounts(uuid) ON DELETE CASCADE
          )
          """;
        case MYSQL, MARIADB, H2 -> """
          CREATE TABLE IF NOT EXISTS transactions (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              account_uuid VARCHAR(36) NOT NULL,
              currency_id VARCHAR(64) NOT NULL,
              amount DECIMAL(36,18) NOT NULL,
              type VARCHAR(10) NOT NULL,
              timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              processed BOOLEAN DEFAULT FALSE,
              FOREIGN KEY(account_uuid) REFERENCES accounts(uuid) ON DELETE CASCADE
          )
          """;
        default -> throw new IllegalArgumentException("Unsupported database type for table creation: " + type);
      };
      stmt.executeUpdate(transactionTable);
    }
  }


  private void createIndexes() {
    asyncExecutor.submit(() -> {
      try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
        stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_balances_currency_amount ON balances(currency_id, amount DESC)");
        stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_transactions_account_processed ON transactions(account_uuid, processed)");
        stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_transactions_account_currency ON transactions(account_uuid, currency_id)");
        stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_transactions_type_account ON transactions(\"type\", " +
          "account_uuid)");
        stmt.executeUpdate("CREATE INDEX if NOT EXISTS idx_transactions_timestamp ON transactions(\"timestamp\")");
      } catch (SQLException e) {
        e.printStackTrace();
      }
    });
  }


  private void saveBalance(UUID uuid, Currency currency, BigDecimal amount) throws SQLException {
    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(SQLSentences.insertBalance())) {
      stmt.setString(1, uuid.toString());
      stmt.setString(2, currency.getId());
      stmt.setBigDecimal(3, amount);
      stmt.executeUpdate();
    }
  }

  public Account getCachedAccount(UUID uuid) {
    return DatabaseFactory.CACHE_ACCOUNTS.getIfPresent(uuid);
  }

  @Override
  public boolean isConnected() {
    try (Connection conn = dataSource.getConnection()) {
      return conn != null && !conn.isClosed();
    } catch (SQLException e) {
      return false;
    }
  }
}
