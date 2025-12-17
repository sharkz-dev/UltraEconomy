package com.kingpixel.ultraeconomy.commands.admin;

import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

/**
 *
 * @author Carlos Varas Alonso - 17/12/2025 2:12
 */
public class BackUpCommands {

  public static void register(LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(
      CommandManager.literal("backup")
        .requires(source -> source.hasPermissionLevel(2))
        .then(createBackUp())
        .then(restoreBackUp())
        .then(listBackUps())
    );
  }

  private static ArgumentBuilder<ServerCommandSource, ?> restoreBackUp() {
    return CommandManager.literal("restore")
      .then(
        CommandManager.argument("backupName", StringArgumentType.string())
          .executes(context -> {
            String backupName = StringArgumentType.getString(context, "backupName");
            return 1;
          })
      );
  }

  private static ArgumentBuilder<ServerCommandSource, ?> listBackUps() {
    return CommandManager.literal("list")
      .executes(context -> {

        return 1;
      });
  }

  private static ArgumentBuilder<ServerCommandSource, ?> createBackUp() {
    return CommandManager.literal("create")
      .executes(context -> {
        DatabaseFactory.INSTANCE.createBackUp();
        return 1;
      });
  }

}
