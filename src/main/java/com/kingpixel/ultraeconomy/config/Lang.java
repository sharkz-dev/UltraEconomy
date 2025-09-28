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
  private HiperMessage messageBalance = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("%prefix%Your balance is: &a%balance%")
    .build();
  private HiperMessage messageSetBalance = HiperMessageBuilder.builder()
    .setType(MessageType.ACTIONBAR)
    .setRawMessage("&aYour balance has been set to &e%amount%.")
    .build();
  private HiperMessage messageDeposit = HiperMessageBuilder.builder()
    .setType(MessageType.ACTIONBAR)
    .setRawMessage("&aYou have deposited &e%amount% &ain your account.")
    .build();
  private HiperMessage messageWithdraw = HiperMessageBuilder.builder()
    .setType(MessageType.ACTIONBAR)
    .setRawMessage("&aYou have withdrawn &e%amount% &afrom your account.")
    .build();
  private HiperMessage messageCurrencyNotTransferable = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("&cThis currency is not transferable.")
    .build();
  private HiperMessage messagePaySuccessSender = HiperMessageBuilder.builder()
    .setType(MessageType.ACTIONBAR)
    .setRawMessage("&aYou have paid &e%amount% &ato &e%player%.")
    .build();
  private HiperMessage messagePaySuccessReceiver = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("%prefix%&aYou have received &e%amount% &afrom &e%player%.")
    .build();
  private HiperMessage messagePayYourself = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("&cYou can't pay yourself.")
    .build();
  private HiperMessage messageNoMoney = HiperMessageBuilder.builder()
    .setType(MessageType.CHAT)
    .setRawMessage("&cYou don't have enough money.")
    .build();
  // BalTop messages
  private String messageBalTopHeader = "&6--- &eTop %number% richest players &6---";
  private String messageBalTopLine = "&e%rank%. &6%player%: &a%balance%";
  private String messageBalTopFooter = "&6------------------------------";
  private String messageBalTopEmpty = "&cNo players found.";
  private BalTopMenu balTopMenu = new BalTopMenu();


  public Lang() {
    prefix = "&6[&eUltraEconomy&6] &r";
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
