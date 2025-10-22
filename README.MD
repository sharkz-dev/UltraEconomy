# 💰 UltraEconomy API

Minecraft Version: 1.21.1

![Minecraft](https://img.shields.io/badge/Minecraft-Mod-00aa00?style=for-the-badge&logo=minecraft)
![Java](https://img.shields.io/badge/Java-21+-red?style=for-the-badge&logo=openjdk)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)

**UltraEconomy** is an advanced economy system for Minecraft, developed by **Carlos Varas Alonso**, providing
multi-currency support, placeholders, database integration, notifications, and multilingual features.

This document covers the main API class:  
[`UltraEconomyApi`](src/main/java/com/kingpixel/ultraeconomy/api/UltraEconomyApi.java)

---

## ⚙️ Features

- Multi-currency support (`config/ultraeconomy/currencies/`)
- Multiple database support (MongoDB and SQL databases)
- Player accounts managed through a database
- Deposit, withdraw, and transfer operations
- Notification and language system
- Optimized caching using **Caffeine**
- Locale support based on client language
- Debug mode with timing logs
- Placeholder integration (`PlaceHoldersPrefix`)

---

## 🧩 Integration Example

To use this API, add **UltraEconomy** as a dependency in your mod or plugin and call the static methods directly from
your Java code.

### Basic Usage

```java
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;

import java.math.BigDecimal;
import java.util.UUID;

UUID playerId = player.getUuid();

// Get player balance
BigDecimal balance = UltraEconomyApi.getBalance(playerId, "coins");

// Deposit money
UltraEconomyApi.

deposit(playerId, "coins",new BigDecimal("100.0"));

// Withdraw money
    UltraEconomyApi.

withdraw(playerId, "coins",new BigDecimal("50.0"));

// Set player balance
    UltraEconomyApi.

setBalance(playerId, "coins",new BigDecimal("200.0"));

// Transfer money between players
    UltraEconomyApi.

transfer(playerId, targetId, "coins",new BigDecimal("25.0"));
```

### Pull Requests

Feel free to contribute to the project by submitting pull requests on GitHub. Ensure your code adheres to the project's
coding standards and includes appropriate tests.
