package com.kingpixel.ultraeconomy.models;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.ultraeconomy.UltraEconomy;
import lombok.Data;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Optimized Currency model with support for short amounts and cached formatting.
 * Improved for multi-threaded environments.
 * <p>
 * Author: Carlos
 */

@Data
public class Currency {
  transient
  private String id;
  private boolean primary;
  private boolean transferable;
  private BigDecimal defaultBalance;
  private byte decimals;
  private String symbol;
  private String format;
  private String singular;
  private String plural;
  private String[] suffixes;
  private String[] currencyIds;

  // Thread-safe caches for formatted strings and Text objects
  transient
  private Map<Locale, Cache<@NotNull BigDecimal, String>> formatCache;
  transient
  private Map<Locale, Cache<@NotNull BigDecimal, Text>> formatTextCache;
  transient
  private Map<Locale, Cache<@NotNull BigDecimal, Text>> formatSimpleTextCache;
  transient
  private Map<Locale, Cache<@NotNull BigDecimal, Text>> formatAmountTextCache;

  // Symbol is invariant -> cache once
  transient
  private Text symbolText;

  public Text getSymbolText() {
    return symbolText == null ? (symbolText = AdventureTranslator.toNative(this.symbol)) : symbolText;
  }

  public Currency() {
    this.format = "<symbol>&6<amount> <name>";
    this.singular = "Dollar";
    this.plural = "Dollars";
    this.suffixes = new String[]{"", "K", "M", "B", "T"};
    this.currencyIds = new String[]{};
  }

  public Currency(boolean primary, byte decimals, String symbol, String[] currencyIds) {
    super();
    this.primary = primary;
    this.transferable = true;
    this.decimals = decimals;
    this.defaultBalance = BigDecimal.ZERO;
    this.symbol = symbol;
    this.currencyIds = currencyIds;
  }

  /**
   * Initialize all caches for currency formatting.
   * Should be called once after constructing the currency object.
   */
  public void init() {
    formatCache = new ConcurrentHashMap<>();
    formatTextCache = new ConcurrentHashMap<>();
    formatSimpleTextCache = new ConcurrentHashMap<>();
    formatAmountTextCache = new ConcurrentHashMap<>();

  }

  // ================== FORMAT STRING CACHES ==================

  private Cache<BigDecimal, String> getFormatCacheForLocale(Locale locale) {
    return formatCache.computeIfAbsent(locale, loc -> Caffeine.newBuilder()
      .expireAfterAccess(1, TimeUnit.MINUTES)
      .maximumSize(5_000)
      .build());
  }

  public String format(BigDecimal value) {
    return getFormatCacheForLocale(Locale.US).get(value, v -> replace(v, Locale.US));
  }

  public String format(BigDecimal value, Locale locale) {
    return getFormatCacheForLocale(locale).get(value, v -> replace(v, locale));
  }

  /**
   * Replace placeholders in the format string with actual values.
   * Supported placeholders: <symbol>, <amount>, <short_amount>, <name>
   */
  private String replace(BigDecimal value, Locale locale) {
    StringBuilder sb = new StringBuilder();
    int length = format.length();
    for (int i = 0; i < length; i++) {
      char c = format.charAt(i);

      if (c == '<') {
        if (format.startsWith("<symbol>", i)) {
          sb.append(this.symbol);
          i += "<symbol>".length() - 1;
          continue;
        }
        if (format.startsWith("<amount>", i)) {
          sb.append(formatSimpleAmount(value, locale));
          i += "<amount>".length() - 1;
          continue;
        }
        if (format.startsWith("<short_amount>", i)) {
          sb.append(formatAmount(value, locale));
          i += "<short_amount>".length() - 1;
          continue;
        }
        if (format.startsWith("<name>", i)) {
          sb.append(value.compareTo(BigDecimal.ONE) == 0 ? singular : plural);
          i += "<name>".length() - 1;
          continue;
        }
      }
      sb.append(c);
    }
    return sb.toString();
  }

  // ================== STRING FORMAT HELPERS ==================

  /**
   * Format a number with grouping separators and configured decimals.
   */
  public String formatSimpleAmount(BigDecimal value, Locale locale) {
    NumberFormat nf = NumberFormat.getNumberInstance(locale);
    nf.setMaximumFractionDigits(decimals);
    nf.setMinimumFractionDigits(0);
    nf.setGroupingUsed(true);

    return nf.format(value);
  }

  /**
   * Format amount with suffixes (K, M, B, T, etc.)
   */
  public String formatAmount(BigDecimal value) {
    return formatAmount(value, Locale.US);
  }

  /**
   * Format amount with suffixes (K, M, B, T, etc.) for a given locale.
   */
  public String formatAmount(BigDecimal value, Locale locale) {
    if (value == null) return "0";
    BigDecimal thousand = BigDecimal.valueOf(1000);
    int suffixIndex = 0;

    // Reduce the number hasta que sea menor a 1000
    while (value.compareTo(thousand) >= 0 && suffixIndex < suffixes.length - 1) {
      value = value.divide(thousand, 2, RoundingMode.DOWN);
      suffixIndex++;
    }

    // Usar DecimalFormat para controlar el redondeo
    DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
    DecimalFormat df = new DecimalFormat();
    df.setDecimalFormatSymbols(symbols);
    df.setMaximumFractionDigits(Math.max(decimals, UltraEconomy.config.getAdjustmentShortName()));
    df.setMinimumFractionDigits(0);
    df.setGroupingUsed(false);
    df.setRoundingMode(RoundingMode.DOWN); // Trunca siempre hacia abajo

    return df.format(value) + suffixes[suffixIndex];
  }


  // ================== FORMAT TEXT CACHES ==================

  public Text formatText(BigDecimal value) {
    return formatText(value, Locale.US);
  }

  public Text formatText(BigDecimal value, Locale locale) {
    return getFormatTextCacheForLocale(locale).get(value, v -> AdventureTranslator.toNative(format(v, locale)));
  }

  private Cache<@NotNull BigDecimal, Text> getFormatTextCacheForLocale(Locale locale) {
    return formatTextCache.computeIfAbsent(locale, loc -> Caffeine.newBuilder()
      .expireAfterAccess(1, TimeUnit.MINUTES)
      .maximumSize(5_000)
      .build());
  }

  // ================== SIMPLE AMOUNT TEXT ==================

  public Text formatSimpleAmountText(BigDecimal balance, Locale locale) {
    return getFormatSimpleTextCacheForLocale(locale)
      .get(balance, v -> AdventureTranslator.toNative(formatSimpleAmount(v, locale)));
  }

  private Cache<@NotNull BigDecimal, Text> getFormatSimpleTextCacheForLocale(Locale locale) {
    return formatSimpleTextCache.computeIfAbsent(locale, loc -> Caffeine.newBuilder()
      .expireAfterAccess(1, TimeUnit.MINUTES)
      .maximumSize(5_000)
      .build());
  }

  // ================== SHORT AMOUNT TEXT ==================

  public Text formatAmountText(BigDecimal balance, Locale locale) {
    return getFormatAmountTextCacheForLocale(locale)
      .get(balance, v -> AdventureTranslator.toNative(formatAmount(v, locale)));
  }

  private Cache<@NotNull BigDecimal, Text> getFormatAmountTextCacheForLocale(Locale locale) {
    return formatAmountTextCache.computeIfAbsent(locale, loc -> Caffeine.newBuilder()
      .expireAfterAccess(1, TimeUnit.MINUTES)
      .maximumSize(5_000)
      .build());
  }
}