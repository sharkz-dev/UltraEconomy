package com.kingpixel.ultraeconomy.config;

import com.kingpixel.cobbleutils.util.Utils;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.exceptions.UnknownCurrencyException;
import com.kingpixel.ultraeconomy.models.Currency;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Carlos Varas Alonso - 23/09/2025 21:37
 */
public class Currencies {
  private static String PATH = UltraEconomy.PATH + "/currencys/";
  private static final Map<String, Currency> CURRENCY_MAP = new HashMap<>();
  public static String[] CURRENCY_IDS;
  public static Currency DEFAULT_CURRENCY;

  public static void init() {
    CURRENCY_MAP.clear();
    var folder = Utils.getAbsolutePath(PATH);
    folder.mkdirs();
    var files = Utils.getFiles(folder);
    if (files.isEmpty()) {
      Currency currency = new Currency(true, (byte) 2, "$");
      currency.setId("dollar");
      CURRENCY_MAP.put(currency.getId(), currency);
      writeCurrency(currency);
      Currency currency2 = new Currency(false, (byte) 2, "€");
      currency2.setId("euro");
      CURRENCY_MAP.put(currency2.getId(), currency2);
      writeCurrency(currency2);
    } else {
      for (var file : files) {
        try {
          var currency = Utils.newGson().fromJson(
            Utils.readFileSync(file), Currency.class
          );
          currency.setId(file.getName().replace(".json", ""));
          CURRENCY_MAP.put(currency.getId(), currency);
          writeCurrency(currency);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }

    Map<String, Currency> aliases = new HashMap<>();
    for (Currency currency : CURRENCY_MAP.values()) {
      if (currency.getCurrencyIds() != null) {
        for (String alias : currency.getCurrencyIds()) {
          aliases.put(alias, currency);
        }
      }
    }

    CURRENCY_MAP.putAll(aliases);


    CURRENCY_MAP.forEach((k, v) -> {
      v.init();
      if (v.isPrimary()) {
        DEFAULT_CURRENCY = v;
      }
    });
    CURRENCY_IDS = CURRENCY_MAP.keySet().toArray(new String[0]);
  }

  public static Map<String, Currency> getCurrencyMap() {
    return CURRENCY_MAP;
  }

  private static void writeCurrency(Currency currency) {
    String data = Utils.newGson().toJson(currency);
    Utils.writeFileAsync(PATH, currency.getId() + ".json", data);
  }

  public static Currency getCurrency(String currency) throws UnknownCurrencyException {
    var curr = CURRENCY_MAP.get(currency);
    if (curr == null && UltraEconomy.config.isUseCurrencyDefaultWhenNotFound()) {
      curr = DEFAULT_CURRENCY;
    }
    if (curr == null) {
      throw new UnknownCurrencyException(currency);
    }
    return curr;
  }


}
