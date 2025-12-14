package com.kingpixel.ultraeconomy.models;

import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.ultraeconomy.config.Currencies;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import org.bson.Document;
import org.bson.types.Decimal128;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.kingpixel.ultraeconomy.database.MongoDBClient.*;

@Data
@EqualsAndHashCode
@ToString
public class Account {
  private long rank;
  private UUID playerUUID;
  private String playerName;
  private final ConcurrentHashMap<String, BigDecimal> balances;

  public Account(ServerPlayerEntity player) {
    this.playerUUID = player.getUuid();
    this.playerName = player.getGameProfile().getName();
    this.balances = defaultBalances();
  }

  public Account(UUID playerUUID) {
    ServerPlayerEntity player = CobbleUtils.server.getPlayerManager().getPlayer(playerUUID);
    if (player != null) this.playerName = player.getGameProfile().getName();
    this.playerUUID = playerUUID;
    this.balances = defaultBalances();
  }

  public Account(UUID uuid, Map<String, BigDecimal> balances) {
    ServerPlayerEntity player = CobbleUtils.server.getPlayerManager().getPlayer(uuid);
    if (player != null) this.playerName = player.getGameProfile().getName();
    this.playerUUID = uuid;
    this.balances = new ConcurrentHashMap<>(balances);
    fix();
  }

  public Account(UUID uuid, String playerName, Map<String, BigDecimal> balances) {
    this.playerUUID = uuid;
    this.playerName = playerName;
    this.balances = new ConcurrentHashMap<>(balances);
    fix();
  }

  public Account(UUID playerUUID, String playerName) {
    this.playerUUID = playerUUID;
    this.playerName = playerName;
    this.balances = defaultBalances();
  }

  public static Account fromDocument(Document doc) {
    UUID uuid = UUID.fromString(doc.getString(FIELD_UUID));
    String playerName = doc.getString(FIELD_PLAYER_NAME);

    Map<String, BigDecimal> balances = new HashMap<>();
    Document balanceDoc = doc.get(FIELD_BALANCES, Document.class);

    if (balanceDoc != null) {
      for (Map.Entry<String, Object> entry : balanceDoc.entrySet()) {
        String key = entry.getKey();
        Object rawValue = entry.getValue();
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

  public Document toDocument() {
    Document doc = new Document();
    doc.append(FIELD_UUID, playerUUID.toString());
    doc.append(FIELD_PLAYER_NAME, playerName);

    Document balanceDoc = new Document();
    for (Map.Entry<String, BigDecimal> entry : balances.entrySet()) {
      balanceDoc.append(entry.getKey(), new Decimal128(entry.getValue()));
    }
    doc.append(FIELD_BALANCES, balanceDoc);

    return doc;
  }

  public synchronized BigDecimal getBalance(Currency currency) {
    return balances.getOrDefault(currency.getId(), null);
  }

  public synchronized boolean addBalance(Currency currency, BigDecimal amount) {
    balances.merge(currency.getId(), amount, BigDecimal::add);
    return true;
  }

  public synchronized boolean removeBalance(Currency currency, BigDecimal amount) {
    balances.merge(currency.getId(), amount, BigDecimal::subtract);
    return true;
  }

  public synchronized BigDecimal setBalance(Currency currency, BigDecimal amount) {
    balances.put(currency.getId(), amount);
    return amount;
  }

  public synchronized boolean hasEnoughBalance(Currency currency, BigDecimal amount) {
    return getBalance(currency).compareTo(amount) >= 0;
  }

  public void fix() {
    Currencies.getCurrencyMap().forEach((k, v) ->
      balances.putIfAbsent(v.getId(), v.getDefaultBalance()));
  }

  private ConcurrentHashMap<String, BigDecimal> defaultBalances() {
    ConcurrentHashMap<String, BigDecimal> defaults = new ConcurrentHashMap<>();
    Currencies.getCurrencyMap().forEach((k, v) -> defaults.put(v.getId(), v.getDefaultBalance()));
    return defaults;
  }

  public GooeyButton getButton(Currency currency) {
    List<String> lore = List.of(
      "§7Balance: §e" + currency.format(getBalance(currency))
    );
    return GooeyButton.builder()
      .display(PlayerUtils.getHeadItem(playerUUID))
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(rank + ". " + playerName))
      .with(DataComponentTypes.LORE, new LoreComponent(
        AdventureTranslator.toNativeL(lore)
      ))
      .build();
  }
}
