package com.kingpixel.ultraeconomy.config;

import com.google.gson.Gson;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.messages.HiperMessage;
import com.kingpixel.cobbleutils.Model.messages.HiperMessageBuilder;
import com.kingpixel.cobbleutils.Model.messages.MessageType;
import com.kingpixel.cobbleutils.util.Utils;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.gui.BalTopMenu;
import lombok.Data;

import java.util.concurrent.CompletableFuture;

/**
 * @author Carlos Varas Alonso - 23/09/2025 22:01
 */
@Data
public class Lang {
  private static final String PATH = UltraEconomy.PATH + "/lang/";
  private String prefix;

  // Mensajes de balance
  private HiperMessage messageBalance = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("%prefix%<#FFD700>💰 Balance: <#00FFAA>%balance% <#FFD700>coins")
    .build();

  private HiperMessage messageSetBalance = HiperMessageBuilder.builder()
    .setType(MessageType.ACTIONBAR)
    .setRawMessage("%prefix%<#00FFAA>✅ Your balance has been updated: <#FFDD55>%amount% <#00FFAA>coins.")
    .build();

  private HiperMessage messageDeposit = HiperMessageBuilder.builder()
    .setType(MessageType.ACTIONBAR)
    .setRawMessage("%prefix%<#00FFAA>💰 You have received a deposit of <#FFDD55>%amount% <#00FFAA>into your account.")
    .build();

  private HiperMessage messageWithdraw = HiperMessageBuilder.builder()
    .setType(MessageType.ACTIONBAR)
    .setRawMessage("%prefix%<#FF5555>💸 You have withdrawn <#FFAA33>%amount% <#FF5555>from your account.")
    .build();

  private HiperMessage messageCurrencyNotTransferable = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("%prefix%<#FF4444>⚠️ This currency cannot be transferred.")
    .build();

  // Mensajes de pagos
  private HiperMessage messagePaySuccessSender = HiperMessageBuilder.builder()
    .setType(MessageType.ACTIONBAR)
    .setRawMessage("%prefix%<#00FFAA>✅ You have paid <#FFDD55>%amount% <#00FFAA>to <#33FFFF>%player%")
    .build();

  private HiperMessage messagePaySuccessReceiver = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("%prefix%<#00FFAA>💰 You have received <#FFDD55>%amount% <#00FFAA>from <#33FFFF>%player%")
    .build();

  private HiperMessage messagePayYourself = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("%prefix%<#FF4444>❌ You cannot pay yourself.")
    .build();

  private HiperMessage messageNoMoney = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("%prefix%<#FF4444>❌ You don't have enough coins!")
    .build();

  private HiperMessage messagePlayerNotFound = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("%prefix%<#FF4444>❌ Player not found.")
    .build();

  private HiperMessage messageInvalidAmount = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("%prefix%<#FF4444>❌ Invalid amount.")
    .build();

  private HiperMessage messageUnknownCurrency = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("%prefix%<#FF4444>❌ Unknown currency.")
    .build();

  // Mensajes BalTop
  private String messageBalTopHeader = "%prefix%<#FFAA00>--- <#FFD700>Top %number% Richest Players <#FFAA00>---";
  private String messageBalTopLine = "%prefix%<#FFD700>%rank%. <#FFDD55>%player%: <#00FFAA>%balance% <#FFAA00>coins";
  private String messageBalTopFooter = "%prefix%<#FFAA00>------------------------------";
  private String messageBalTopEmpty = "%prefix%<#FF5555>No players found.";
  private BalTopMenu balTopMenu = new BalTopMenu();

  public Lang() {
    prefix = "<#FFAA00>[<#FFD700>UltraEconomy<#FFAA00>] <#FFFFFF>";
  }

  public void init() {
    String filename = UltraEconomy.config.getLang() + ".json";
    CompletableFuture<Boolean> futureRead = Utils.readFileAsync(PATH, filename, (el) -> {
      Gson gson = Utils.newGson();
      UltraEconomy.lang = gson.fromJson(el, Lang.class);
      String data = gson.toJson(UltraEconomy.lang);
      Utils.writeFileAsync(PATH, filename, data);
    });
    if (!futureRead.join()) {
      CobbleUtils.LOGGER.info("Creating new config file at " + PATH + "/" + filename);
      Gson gson = Utils.newGson();
      UltraEconomy.lang = this;
      String data = gson.toJson(UltraEconomy.lang);
      Utils.writeFileAsync(PATH, filename, data);
    }
  }
}

