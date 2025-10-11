package com.kingpixel.ultraeconomy.manager;

/**
 *
 * @author Carlos Varas Alonso - 11/10/2025 7:51
 */

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.ultraeconomy.UltraEconomy;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.*;

public class PlayerMessageQueueManager {

  private static final ConcurrentHashMap<UUID, Queue<Runnable>> MESSAGE_QUEUES = new ConcurrentHashMap<>();
  public
  static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

  static {
    // Inicia un scheduler que procesa las colas cada 1 segundo
    SCHEDULER.scheduleAtFixedRate(PlayerMessageQueueManager::processQueues, 0,
      UltraEconomy.config.getBetweenMessagesDelay().toMillis(),
      TimeUnit.MILLISECONDS);
  }

  /**
   * Añadir un mensaje a la cola de un jugador
   */
  public static void enqueue(UUID playerUUID, Runnable messageAction) {
    MESSAGE_QUEUES.computeIfAbsent(playerUUID, id -> new ConcurrentLinkedQueue<>()).add(messageAction);
  }

  /**
   * Procesa una pila por jugador (envía un mensaje por ciclo)
   */
  private static void processQueues() {
    for (var entry : MESSAGE_QUEUES.entrySet()) {
      UUID playerUUID = entry.getKey();
      Queue<Runnable> queue = entry.getValue();

      Runnable action = queue.poll();
      if (action != null) {
        try {
          action.run();
        } catch (Exception e) {
          if (UltraEconomy.config.isDebug()) {
            CobbleUtils.LOGGER.error(UltraEconomy.MOD_ID, "Error al enviar mensaje en cola: " + e.getMessage());
          }
        }
      }
      if (queue.isEmpty()) {
        MESSAGE_QUEUES.remove(playerUUID);
      }
    }
  }
}

