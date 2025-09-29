package com.kingpixel.ultraeconomy.models;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.ultraeconomy.UltraEconomy;
import lombok.Data;
import net.minecraft.text.Text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Carlos
 * Optimized Currency model with short amount support
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
  private String[] SUFFIXES;
  private String[] currencyIds;

  transient
  private Map<Locale, Cache<BigDecimal, String>> formatCache;
  transient
  private Map<Locale, Cache<BigDecimal, Text>> formatTextCache;

  public Currency() {
    this.format = "<symbol>&6<amount> <name>";
    this.singular = "Dollar";
    this.plural = "Dollars";
    this.SUFFIXES = new String[]{"", "K", "M", "B", "T"};
    this.currencyIds = new String[]{};
  }

  public Currency(boolean primary, byte decimals, String symbol) {
    super();
    this.primary = primary;
    this.transferable = true;
    this.decimals = decimals;
    this.defaultBalance = BigDecimal.ZERO;
    this.symbol = symbol;

  }


  public void init() {
    formatCache = new HashMap<>();
    formatTextCache = new HashMap<>();
  }

  private Cache<BigDecimal, String> getFormatCacheForLocale(Locale locale) {
    return formatCache.computeIfAbsent(locale, loc -> Caffeine.newBuilder()
      .expireAfterAccess(1, TimeUnit.MINUTES)
      .maximumSize(5_000)
      .removalListener((key, value, cause) -> {
        if (UltraEconomy.config.isDebug()) {
          CobbleUtils.LOGGER.info("Currency format cache removed key: " + key + ", cause: " + cause);
        }
      })
      .build());
  }

  public String format(BigDecimal value) {
    return getFormatCacheForLocale(Locale.US).get(value, v -> replace(v, Locale.US));
  }

  public String format(BigDecimal value, Locale locale) {
    return getFormatCacheForLocale(locale).get(value, v -> replace(v, locale));
  }


  /**
   * Replace placeholders in the format string with actual values
   *
   * @param value  the amount to format
   * @param locale the locale to use for formatting
   *
   * @return the formatted string
   */
  private String replace(BigDecimal value, Locale locale) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < format.length(); i++) {
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
    String result = sb.toString();
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info("Formatted currency: " + result);
    }
    return result;
  }

  public String formatSimpleAmount(BigDecimal value, Locale locale) {
    NumberFormat nf = NumberFormat.getNumberInstance(locale);
    nf.setMaximumFractionDigits(decimals);
    nf.setMinimumFractionDigits(0);
    nf.setGroupingUsed(true); // con separadores de miles

    return nf.format(value);
  }

  /**
   * Format amount with suffixes (K, M, B, T, etc.)
   *
   * @param value the amount to format
   *
   * @return the formatted amount with suffix
   */
  public String formatAmount(BigDecimal value) {
    return formatAmount(value, Locale.US);
  }

  /**
   * Format amount with suffixes (K, M, B, T, etc.) using a specific locale
   *
   * @param value  the amount to format
   * @param locale the locale to use for formatting
   *
   * @return the formatted amount with suffix
   */
  public String formatAmount(BigDecimal value, Locale locale) {
    if (value == null) return "0";
    BigDecimal thousand = BigDecimal.valueOf(1000);
    int suffixIndex = 0;

    // Reducir el número hasta que sea menor que 1000 o lleguemos al último sufijo
    while (value.compareTo(thousand) >= 0 && suffixIndex < SUFFIXES.length - 1) {
      value = value.divide(thousand, 2, RoundingMode.DOWN); // división con redondeo inmediato
      suffixIndex++;
    }

    // Usar NumberFormat para formatear decimales de forma segura y con separador de miles
    NumberFormat nf = NumberFormat.getNumberInstance(locale);
    nf.setMaximumFractionDigits(Math.max(decimals, UltraEconomy.config.getAdjustmentShortName()));
    nf.setMinimumFractionDigits(0);
    nf.setGroupingUsed(false); // sin separadores de miles, ya que es un valor reducido

    return nf.format(value) + SUFFIXES[suffixIndex];
  }


  /**
   * Format the value and return it as a Text component
   *
   * @param value the value to format
   *
   * @return the formatted value as Text
   */
  public Text formatText(BigDecimal value) {
    return formatText(value, Locale.US);
  }

  /**
   * Format the value with a specific locale and return it as a Text component
   *
   * @param value  the value to format
   * @param locale the locale to use for formatting
   *
   * @return the formatted value as Text
   */
  public Text formatText(BigDecimal value, Locale locale) {
    return getFormatTextCacheForLocale(locale).get(value, v -> AdventureTranslator.toNative(format(v, locale)));
  }

  private Cache<BigDecimal, Text> getFormatTextCacheForLocale(Locale locale) {
    return formatTextCache.computeIfAbsent(locale, loc -> Caffeine.newBuilder()
      .expireAfterAccess(1, TimeUnit.MINUTES)
      .maximumSize(5_000)
      .removalListener((key, value, cause) -> {
        if (UltraEconomy.config.isDebug()) {
          CobbleUtils.LOGGER.info("Currency format text cache removed key: " + key + ", cause: " + cause);
        }
      })
      .build());
  }
}
