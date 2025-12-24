package com.kingpixel.ultraeconomy.mixins.cobbledollars;

import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 *
 * @author Carlos Varas Alonso - 24/12/2025 23:48
 */
@Mixin(targets = "fr.harmex.cobbledollars.common.mixin.PlayerMixin", remap = false)
public abstract class CobbleDollarsMixin {

  @Inject(
    method = "cobbleDollars$getCobbleDollars",
    at = @At("HEAD"),
    cancellable = true
  )
  private void onGetCobbleDollars(CallbackInfoReturnable<BigInteger> cir) {
    if (!UltraEconomy.config.getMigration().isActive()) {
      PlayerEntity player = (PlayerEntity) (Object) this;
      BigInteger money = UltraEconomyApi.getBalance(player.getUuid(), "").toBigInteger();
      cir.setReturnValue(money);
    }
  }

  @Inject(
    method = "cobbleDollars$setCobbleDollars",
    at = @At("HEAD"),
    cancellable = true
  )
  private void onSetCobbleDollars(BigInteger amount, CallbackInfo ci) {
    if (!UltraEconomy.config.getMigration().isActive()) {
      PlayerEntity player = (PlayerEntity) (Object) this;

      UltraEconomyApi.setBalance(player.getUuid(), "", new BigDecimal(amount));

      ci.cancel();
    }
  }
}

