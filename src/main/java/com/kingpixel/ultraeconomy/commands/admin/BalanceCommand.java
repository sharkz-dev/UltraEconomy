package com.kingpixel.ultraeconomy.commands.admin;

import com.kingpixel.cobbleutils.command.suggests.CobbleUtilsSuggests;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.commands.Register;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.exceptions.UnknownCurrencyException;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * @author Carlos Varas Alonso - 23/09/2025 22:01
 */
public class BalanceCommand {
  public static void put(CommandDispatcher<ServerCommandSource> dispatcher, LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(get());
    dispatcher.register(get());
  }

  private static LiteralArgumentBuilder<ServerCommandSource> get() {
    return CommandManager.literal("balance")
      .executes(context -> {
        ServerPlayerEntity player = context.getSource().getPlayer();
        run(player == null ? null : player.getUuid(), context, Currencies.DEFAULT_CURRENCY.getId());
        return 1;
      }).then(
        CommandManager.argument("currency", StringArgumentType.string())
          .suggests((context, builder) -> {
            var size = Currencies.CURRENCY_IDS.length;
            for (int i = 0; i < size; i++) {
              builder.suggest(Currencies.CURRENCY_IDS[i]);
            }
            return builder.buildFuture();
          }).executes(context -> {
            ServerPlayerEntity player = context.getSource().getPlayer();
            run(player == null ? null : player.getUuid(), context, StringArgumentType.getString(context,
              "currency"));
            return 1;
          }).then(
            CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.suggestPlayerName("player", List.of(
                "ultraeconomy.admin.balance"
              ), 0)
              .executes(context -> {
                CompletableFuture.runAsync(() -> {
                  var target = StringArgumentType.getString(context, "player");
                  if (!UltraEconomyApi.existsPlayerWithName(target)) {
                    context.getSource().sendMessage(Text.literal("§cPlayer not found"));
                    return;
                  }
                  var currencyId = StringArgumentType.getString(context, "currency");
                  var targetUUID = CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.getPlayerUUIDWithName(target);
                  run(targetUUID, context, currencyId);
                }, UltraEconomy.ULTRA_ECONOMY_EXECUTOR).exceptionally(e -> {
                  if (e instanceof UnknownCurrencyException) return null;
                  e.printStackTrace();
                  return null;
                });
                return 1;
              })
          )
      );
  }

  public static void run(UUID targetUUID, CommandContext<ServerCommandSource> context, String currencyId) {
    CompletableFuture.runAsync(() -> {
        var source = context.getSource();
        if (targetUUID == null) {
          source.sendError(Text.literal("§cYou must be a player to use this command"));
          return;
        }

        var account = UltraEconomyApi.getAccount(targetUUID);
        if (account == null) {
          source.sendError(Text.literal("§cAccount not found"));
          return;
        }

        var currency = Currencies.getCurrency(currencyId);
        if (currency == null) {
          source.sendMessage(Text.literal("§cCurrency not found: " + currencyId + ". Available: " + String.join(", ",
            Currencies.CURRENCY_IDS)));
          return;
        }

        var balance = account.getBalance(currency);
        if (balance == null) {
          source.sendError(Text.literal("§cBalance not found"));
          return;
        }
        ServerPlayerEntity player = source.getPlayer();

        var lang = UltraEconomy.lang;
        String modifiedContent = lang.getMessageBalance().getRawMessage().replace("%balance%", currency.format(balance, UltraEconomyApi.getLocale(player)));
        var message = lang.getMessageBalance();
        message.sendMessage(
          player,
          modifiedContent,
          UltraEconomy.lang.getPrefix(),
          false
        );
      }, UltraEconomy.ULTRA_ECONOMY_EXECUTOR)
      .exceptionally(e -> Register.sendFeedBack(e, context));
  }
}
