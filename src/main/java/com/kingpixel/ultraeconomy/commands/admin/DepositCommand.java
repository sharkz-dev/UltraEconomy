package com.kingpixel.ultraeconomy.commands.admin;

import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.command.suggests.CobbleUtilsSuggests;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.commands.Register;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author Carlos Varas Alonso - 23/09/2025 22:01
 */
public class DepositCommand {
  public static void put(LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(
      CommandManager.literal("deposit")
        .requires(source -> PermissionApi.hasPermission(source, "ultraeconomy.admin.deposit", 2))
        .then(
          CommandManager.argument("amount", StringArgumentType.string())
            .then(
              CommandManager.argument("currency", StringArgumentType.string())
                .suggests((context, builder) -> {
                  var size = Currencies.CURRENCY_IDS.length;
                  for (int i = 0; i < size; i++) {
                    builder.suggest(Currencies.CURRENCY_IDS[i]);
                  }
                  return builder.buildFuture();
                }).then(
                  CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.suggestPlayerName("player", List.of("ultraeconomy.admin.deposit"), 2)
                    .executes(context -> {
                      CompletableFuture.runAsync(() -> {
                          var target = StringArgumentType.getString(context, "player");
                          if (!UltraEconomyApi.existsPlayerWithName(target)) {
                            context.getSource().sendMessage(Text.literal("§cPlayer not found"));
                            return;
                          }
                          var currency = Currencies.getCurrency(StringArgumentType.getString(context, "currency"));
                          var amountStr = StringArgumentType.getString(context, "amount");
                          var playerUUID = CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.getPlayerUUIDWithName(target);
                          if (playerUUID != null) {
                            BigDecimal value = BigDecimal.valueOf(Double.parseDouble(amountStr));
                            UltraEconomyApi.deposit(playerUUID, currency.getId(), value);
                            Register.sendMessage(currency, value, playerUUID, UltraEconomy.lang.getMessageDeposit());
                          } else {
                            context.getSource().sendError(Text.literal("§cPlayer not found"));
                          }
                        }, UltraEconomy.ULTRA_ECONOMY_EXECUTOR)
                        .exceptionally(e -> Register.sendFeedBack(e, context));
                      return 1;
                    })
                )
            )
        )
    );
  }
}
