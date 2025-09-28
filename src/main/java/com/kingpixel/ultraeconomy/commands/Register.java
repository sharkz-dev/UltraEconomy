package com.kingpixel.ultraeconomy.commands;

import com.kingpixel.cobbleutils.Model.messages.HiperMessage;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.commands.admin.*;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.models.Currency;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 23/09/2025 21:29
 */
public class Register {
  public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess,
                              CommandManager.RegistrationEnvironment environment) {
    for (String command : UltraEconomy.config.getCommands()) {
      var base = CommandManager.literal(command);
      base.executes(context -> {
        ServerPlayerEntity player = context.getSource().getPlayer();
        BalanceCommand.run(player == null ? null : player.getUuid(), context.getSource(),
          Currencies.DEFAULT_CURRENCY.getId());
        return 1;
      });

      PayCommand.put(dispatcher, base);
      BalanceCommand.put(dispatcher, base);
      ReloadCommand.put(dispatcher, base);
      DepositCommand.put(dispatcher, base);
      WithdrawCommand.put(dispatcher, base);
      SetCommand.put(dispatcher, base);
      BaltopCommand.put(dispatcher, base);

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
}
