package com.kingpixel.ultraeconomy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.util.Utils;
import com.kingpixel.ultraeconomy.commands.Register;
import com.kingpixel.ultraeconomy.config.Config;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.kingpixel.ultraeconomy.config.Lang;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import com.kingpixel.ultraeconomy.manager.PlayerMessageQueueManager;
import com.kingpixel.ultraeconomy.models.Account;
import com.kingpixel.ultraeconomy.placeholders.PlaceHolders;
import dev.architectury.event.events.common.PlayerEvent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.util.concurrent.*;

public class UltraEconomy implements ModInitializer {
  public static final String MOD_ID = "ultraeconomy";
  public static final String PATH = "/config/ultraeconomy";
  public static MinecraftServer server;
  public static Config config = new Config();
  public static Lang lang = new Lang();
  public static final ExecutorService ULTRA_ECONOMY_EXECUTOR = Executors.newFixedThreadPool(4, new ThreadFactoryBuilder()
    .setNameFormat("ultra economy-executor-%d")
    .setDaemon(true)
    .build()
  );
  public static final ScheduledExecutorService ULTRA_ECONOMY_SCHEDULER = Executors.newScheduledThreadPool(1,
    new ThreadFactoryBuilder()
      .setNameFormat("ultra economy-scheduler-%d")
      .setDaemon(true)
      .build()
  );
  public static boolean migrationDone;

  @Override
  public void onInitialize() {
    File folder = Utils.getAbsolutePath(PATH);
    if (!folder.exists()) {
      folder.mkdirs();
    }
    load();
    events();
    tasks();
    PlaceHolders.register();
  }

  public static void load() {
    config.init();
    lang.init();
    Currencies.init();
    DatabaseFactory.init(config.getDatabase());
  }

  public void events() {
    PlayerEvent.PLAYER_JOIN.register((player) -> CompletableFuture.runAsync(() -> {
        Account account = DatabaseFactory.INSTANCE.getAccount(player.getUuid());
        account.setPlayerName(player.getGameProfile().getName());
        DatabaseFactory.CACHE_ACCOUNTS.put(player.getUuid(), account);
        account.fix();
        DatabaseFactory.INSTANCE.saveOrUpdateAccount(account);
      }, ULTRA_ECONOMY_EXECUTOR)
      .exceptionally(e -> {
        e.printStackTrace();
        return null;
      }));

    PlayerEvent.PLAYER_QUIT.register((player) -> {
      if (server.isStopped() || server.isStopping()) return;
      DatabaseFactory.CACHE_ACCOUNTS.invalidate(player.getUuid());
    });

    ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
      UltraEconomy.server = server;
      config.getMigration().startMigration();
    });

    ServerLifecycleEvents.SERVER_STOPPING.register((server) -> {
      DatabaseFactory.INSTANCE.flushCache();
      DatabaseFactory.INSTANCE.disconnect();
      CobbleUtils.shutdownAndAwait(ULTRA_ECONOMY_EXECUTOR);
      CobbleUtils.shutdownAndAwait(PlayerMessageQueueManager.SCHEDULER);
    });

    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> Register.register(dispatcher));
  }

  private void tasks() {
    // Aquí puedes agregar tareas programadas si es necesario
    ULTRA_ECONOMY_SCHEDULER.scheduleAtFixedRate(() -> {
      DatabaseFactory.CACHE_ACCOUNTS.asMap().values().forEach(account -> DatabaseFactory.INSTANCE.saveOrUpdateAccount(account));
    }, 60, 30, TimeUnit.SECONDS);
  }
}
