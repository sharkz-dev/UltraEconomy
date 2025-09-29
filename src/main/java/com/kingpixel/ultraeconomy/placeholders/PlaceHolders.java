package com.kingpixel.ultraeconomy.placeholders;

import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.models.Currency;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * @author Carlos Varas Alonso - 28/09/2025 5:35
 */
public class PlaceHolders {
  public static void register() {
    try {
      // %ultraeconomy:balance dollars%
      Placeholders.register(
        Identifier.of("ultraeconomy", "balance"), (ctx, arg) -> {
          if (!ctx.hasPlayer()) return PlaceholderResult.invalid();
          ServerPlayerEntity player = ctx.player();
          if (player == null) return PlaceholderResult.invalid();
          if (arg == null || arg.isEmpty()) return PlaceholderResult.invalid();
          Currency currency = Currencies.getCurrency(arg);
          if (currency == null) return PlaceholderResult.invalid();
          return PlaceholderResult.value(
            currency.formatText(
              UltraEconomyApi.getBalance(player.getUuid(), currency.getId()),
              UltraEconomyApi.getLocale(player)
            )
          );
        }
      );

      // %ultraeconomy:symbol dollars%
      Placeholders.register(
        Identifier.of("ultraeconomy", "symbol"), (ctx, arg) -> {
          if (arg == null || arg.isEmpty()) return PlaceholderResult.invalid();
          Currency currency = Currencies.getCurrency(arg);
          if (currency == null) return PlaceholderResult.invalid();
          return PlaceholderResult.value(currency.getSymbol());
        }
      );

      // %ultraeconomy:amount dollars%
      Placeholders.register(
        Identifier.of("ultraeconomy", "amount"), (ctx, arg) -> {
          if (!ctx.hasPlayer()) return PlaceholderResult.invalid();
          ServerPlayerEntity player = ctx.player();
          if (player == null) return PlaceholderResult.invalid();
          if (arg == null || arg.isEmpty()) return PlaceholderResult.invalid();
          Currency currency = Currencies.getCurrency(arg);
          if (currency == null) return PlaceholderResult.invalid();
          return PlaceholderResult.value(
            currency.formatSimpleAmount(
              UltraEconomyApi.getBalance(player.getUuid(), currency.getId()),
              UltraEconomyApi.getLocale(player)
            )
          );
        }
      );

      // %ultraeconomy:short_amount dollars%
      Placeholders.register(
        Identifier.of("ultraeconomy", "short_amount"), (ctx, arg) -> {
          if (!ctx.hasPlayer()) return PlaceholderResult.invalid();
          ServerPlayerEntity player = ctx.player();
          if (player == null) return PlaceholderResult.invalid();
          if (arg == null || arg.isEmpty()) return PlaceholderResult.invalid();
          Currency currency = Currencies.getCurrency(arg);
          if (currency == null) return PlaceholderResult.invalid();
          return PlaceholderResult.value(
            currency.formatAmount(
              UltraEconomyApi.getBalance(player.getUuid(), currency.getId()),
              UltraEconomyApi.getLocale(player)
            )
          );
        }
      );
    } catch (Exception | NoClassDefFoundError ignored) {

    }
  }
}
