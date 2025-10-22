---
lang: en
---

# 💰 UltraEconomy API

### 🧱 Compatible with Minecraft 1.21.1

![Minecraft](https://img.shields.io/badge/Minecraft-Mod-00aa00?style=for-the-badge&logo=minecraft)
![Java](https://img.shields.io/badge/Java-21+-red?style=for-the-badge&logo=openjdk)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)

**UltraEconomy** is an advanced economy system for Minecraft, developed by **zonary123**.  
It provides multi-currency support, database integration, notifications, localization, and optimized caching — designed for high performance and flexibility.

---

## ⚙️ Key Features

- 🏦 **Overwrite existing economy systems**  
  Fully compatible with other economy mods or plugins, allowing complete replacement.

- 💱 **Multi-currency support**  
  Define and configure currencies in `config/ultraeconomy/currencies/`.

- 🔁 **Economy migration support**  
  Ability to migrate from existing economy systems and transfer your current balances into UltraEconomy.

- 🗄️ **Multi-database compatibility**  
  Supports **MongoDB** and various **SQL** databases (MySQL, MariaDB, SQLite, etc.).

- 👤 **Advanced player account management**  
  Player accounts are stored and synchronized through a database for cross-server consistency.

- 💸 **Full economy operations**  
  Deposit, withdraw, transfer, and set balances with high-precision decimal values.

- 🌐 **Cross-server compatibility**  
  Ideal for networked environments and distributed setups.

- 🔔 **Notification and localization system**  
  Messages automatically adapt to the player's client language.

- ⚡ **Optimized caching using Caffeine**  
  Increases performance and reduces database calls.

- 🧩 **Placeholder and API integration**  
  Works seamlessly with external placeholder systems and statistic trackers.

- 🪛 **Debug mode**  
  Detailed logs and execution timing for performance tracking.

---

## 🧩 Integration Example

To use the API, add **UltraEconomy** as a dependency in your mod .  
You can directly call the static methods from your Java code.

### 🧠 Basic Usage

```java
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import java.math.BigDecimal;
import java.util.UUID;

UUID playerId = player.getUuid();

// Get balance
BigDecimal balance = UltraEconomyApi.getBalance(playerId, "coins");

// Deposit money
UltraEconomyApi.deposit(playerId, "coins", new BigDecimal("100.0"));

// Withdraw money
UltraEconomyApi.withdraw(playerId, "coins", new BigDecimal("50.0"));

// Set balance
UltraEconomyApi.setBalance(playerId, "coins", new BigDecimal("200.0"));

// Transfer between players
UltraEconomyApi.transfer(playerId, targetId, "coins", new BigDecimal("25.0"));

// Format currency
Currency currency = UltraEconomyApi.getCurrency("coins");
String formatted = currency.format(new BigDecimal("1234.56")); Or currency.format(new BigDecimal("1234.56"), UltraEconomyApi.getLocale(playerId));

// Check if the player has enough balance
boolean hasEnough = UltraEconomyApi.hasEnoughBalance(playerId, "coins", new BigDecimal("150.0"));
```
