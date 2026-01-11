package com.kingpixel.ultraeconomy.commands;

import com.kingpixel.cobbleutils.Model.messages.HiperMessage;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.commands.admin.*;
import com.kingpixel.ultraeconomy.commands.base.BalanceCommand;
import com.kingpixel.ultraeconomy.commands.base.BaltopCommand;
import com.kingpixel.ultraeconomy.commands.base.PayCommand;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.exceptions.UnknownCurrencyException;
import com.kingpixel.ultraeconomy.models.Currency;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 23/09/2025 21:29
 */
public class Register {
  public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
    for (String command : UltraEconomy.config.getCommands()) {
      var base = CommandManager.literal(command);
      base.executes(context -> {
        ServerPlayerEntity player = context.getSource().getPlayer();
        BalanceCommand.run(player == null ? null : player.getUuid(), context,
          Currencies.DEFAULT_CURRENCY.getId());
        return 1;
      });

      PayCommand.put(dispatcher, base);
      BalanceCommand.put(dispatcher, base);
      ReloadCommand.put(base);
      DepositCommand.put(base);
      WithdrawCommand.put(base);
      SetCommand.put(base);
      BaltopCommand.put(dispatcher, base);
      BackUpCommands.register(base);

      dispatcher.register(base);
    }
  }

  public static void sendMessage(Currency currency, BigDecimal value, UUID playerUUID,
                                 HiperMessage message) {
    if (!UltraEconomy.config.isNotifications()) {
      message.sendMessage(playerUUID, UltraEconomy.lang.getPrefix(), false, false, null,
        message.getRawMessage().replace("%amount%", currency.format(value)));
    }
  }

  public static Void sendFeedBack(Throwable e, CommandContext<ServerCommandSource> context) {
    if (e instanceof UnknownCurrencyException) {
      if (context.getSource().isExecutedByPlayer()) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player != null)
          UltraEconomy.lang.getMessageUnknownCurrency()
            .sendMessage(player.getUuid(), UltraEconomy.lang.getPrefix(), false);
      } else {
        context.getSource().sendError(
          Text.literal("§cUnknown currency")
        );
      }
      return null;
    }
    e.printStackTrace();
    return null;
  }
}
