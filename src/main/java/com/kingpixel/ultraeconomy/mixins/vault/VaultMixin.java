package com.kingpixel.ultraeconomy.mixins.vault;

import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import net.milkbowl.vault.economy.AbstractEconomy;
import org.bukkit.OfflinePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigDecimal;

@Mixin(AbstractEconomy.class)
public abstract class VaultMixin {

  @Inject(method = "getBalance(Lorg/bukkit/OfflinePlayer;)D", at = @At("RETURN"), remap = false)
  private void onGetBalance(OfflinePlayer player, CallbackInfoReturnable<Double> cir) {
    UltraEconomyApi.setBalance(player.getUniqueId(), "", BigDecimal.valueOf(cir.getReturnValue()));
  }

  @Inject(method = "depositPlayer(Lorg/bukkit/OfflinePlayer;D)Lnet/milkbowl/vault/economy/EconomyResponse;", at = @At("HEAD"), remap = false)
  private void onDeposit(OfflinePlayer player, double amount, CallbackInfoReturnable<?> cir) {
    double balance = UltraEconomyApi.getBalance(player.getUniqueId(), "").doubleValue();
    UltraEconomyApi.setBalance(player.getUniqueId(), "", BigDecimal.valueOf(balance + amount));
  }

  @Inject(method = "withdrawPlayer(Lorg/bukkit/OfflinePlayer;D)Lnet/milkbowl/vault/economy/EconomyResponse;", at = @At("HEAD"), remap = false)
  private void onWithdraw(OfflinePlayer player, double amount, CallbackInfoReturnable<?> cir) {
    double balance = UltraEconomyApi.getBalance(player.getUniqueId(), "").doubleValue();
    UltraEconomyApi.setBalance(player.getUniqueId(), "", BigDecimal.valueOf(balance - amount));
  }
}
