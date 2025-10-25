package com.kingpixel.ultraeconomy.mixins.vault;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigDecimal;

@Mixin(AbstractEconomy.class)
public abstract class VaultMixin {

  @Inject(method = "getBalance(Lorg/bukkit/OfflinePlayer;)D", at = @At("RETURN"), remap = false, cancellable = true)
  private void getBalance(OfflinePlayer player, CallbackInfoReturnable<Double> cir) {
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info("VaultMixin.getBalance called for player: " + player.getName() + " UUID: " + player.getUniqueId());
    }
    var bigDecimal = UltraEconomyApi.getBalance(player.getUniqueId(), "");
    if (bigDecimal == null) return;
    cir.setReturnValue(bigDecimal.doubleValue());
  }

  @Inject(method = "depositPlayer(Lorg/bukkit/OfflinePlayer;D)Lnet/milkbowl/vault/economy/EconomyResponse;", at = @At("HEAD"), remap = false)
  private void onDeposit(OfflinePlayer player, double amount, CallbackInfoReturnable<EconomyResponse> cir) {
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info("VaultMixin.depositPlayer called for player: " + player.getName() + " UUID: " + player.getUniqueId() + " Amount: " + amount);
    }
    UltraEconomyApi.deposit(player.getUniqueId(), "", BigDecimal.valueOf(amount));
  }

  @Inject(method = "withdrawPlayer(Lorg/bukkit/OfflinePlayer;D)Lnet/milkbowl/vault/economy/EconomyResponse;", at = @At("HEAD"), remap = false)
  private void onWithdraw(OfflinePlayer player, double amount, CallbackInfoReturnable<EconomyResponse> cir) {
    if (UltraEconomy.config.isDebug()) {
      CobbleUtils.LOGGER.info("VaultMixin.withdrawPlayer called for player: " + player.getName() + " UUID: " + player.getUniqueId() + " Amount: " + amount);
    }
    UltraEconomyApi.withdraw(player.getUniqueId(), "", BigDecimal.valueOf(amount));
  }
}
