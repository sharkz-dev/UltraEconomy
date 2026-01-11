package com.kingpixel.ultraeconomy.commands.admin;

import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.command.suggests.CobbleUtilsSuggests;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import com.kingpixel.ultraeconomy.models.Currency;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 23/09/2025 22:01
 */
public class PayCommand {
  public static void put(CommandDispatcher<ServerCommandSource> dispatcher, LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(get());
    dispatcher.register(get());
  }

  private static LiteralArgumentBuilder<ServerCommandSource> get() {
    return CommandManager.literal("pay")
      .requires(source -> PermissionApi.hasPermission(
        source,
        "ultraeconomy.command.pay",
        2
      ))
      .then(
        CommandManager.argument("currency", StringArgumentType.string())
          .suggests((context, builder) -> {
            var size = Currencies.CURRENCY_IDS.length;
            for (int i = 0; i < size; i++) {
              builder.suggest(Currencies.CURRENCY_IDS[i]);
            }
            return builder.buildFuture();
          }).then(
            CommandManager.argument("amount", FloatArgumentType.floatArg())
              .then(
                CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.suggestPlayerName(
                    "player",
                    List.of("ultraeconomy.command.pay"),
                    2
                  )
                  .executes(context -> {
                    var executor = context.getSource().getPlayer();
                    var target = StringArgumentType.getString(context, "player");
                    if (!UltraEconomyApi.existsPlayerWithName(target)) {
                      context.getSource().sendMessage(Text.literal("§cPlayer not found"));
                      return 0;
                    }
                    var currencyId = StringArgumentType.getString(context, "currency");
                    var amount = BigDecimal.valueOf(FloatArgumentType.getFloat(context, "amount"));
                    run(executor, target, currencyId, amount, context);
                    return 1;
                  })
              )
          )
      );
  }

  private static void run(ServerPlayerEntity executor, String target, String currencyId, BigDecimal amount, CommandContext<ServerCommandSource> context) {
    if (PlayerUtils.hasCooldownCommand(executor, "ultraeconomy.command.pay",
      UltraEconomy.config.getCommandCooldown())) return;
    UltraEconomy.runAsync(() -> {
      Currency currency = Currencies.getCurrency(currencyId);

      if (!DatabaseFactory.INSTANCE.hasEnoughBalance(executor.getUuid(), currency, amount)) return;

      UUID targetUUID = CobbleUtilsSuggests.SUGGESTS_PLAYER_OFFLINE_AND_ONLINE.getPlayerUUIDWithName(target);
      if (executor.getUuid().equals(targetUUID)) {
        UltraEconomy.lang.getMessagePayYourself().sendMessage(
          executor,
          UltraEconomy.lang.getPrefix(),
          false
        );
        return;
      }
      UltraEconomyApi.transfer(executor.getUuid(), targetUUID, currency.getId(), amount);
    });
  }

}
