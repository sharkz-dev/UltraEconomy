/*package com.kingpixel.ultraeconomy.mixins.cobbledollars;

import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.api.UltraEconomyApi;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;

import java.math.BigDecimal;
import java.math.BigInteger;

@Mixin(targets = "fr.harmex.cobbledollars.common.mixin.PlayerMixin", remap = false)
public abstract class CobbleDollarsMixin {
  @Final @Shadow
  private static TrackedData<String> DATA_COBBLEDOLLARS_ID;

  @Shadow protected abstract PlayerEntity cobbleDollars$self();

  /**
   * @return
   *
   * @author Carlos Varas Alonso - 28/09/2025 4:40
   * @reason UltraEconomy migration
   */
  @Unique @Overwrite
  public @NotNull BigInteger cobbleDollars$getCobbleDollars() {
    PlayerEntity player = (PlayerEntity) (Object) this;
    if (UltraEconomy.migrationDone) {
      return UltraEconomyApi.getBalance(player.getUuid(), "").toBigInteger();
    } else {
      return new BigInteger(this.cobbleDollars$self().getDataTracker().get(DATA_COBBLEDOLLARS_ID));
    }
  }

  /**
   * @return
   *
   * @author Carlos Varas Alonso - 28/09/2025 4:40
   * @reason UltraEconomy migration
   */
  @Unique @Overwrite
  public void cobbleDollars$setCobbleDollars(@NotNull BigInteger amount) {
    if (UltraEconomy.migrationDone) {
      PlayerEntity player = (PlayerEntity) (Object) this;
      UltraEconomyApi.setBalance(player.getUuid(), "", new BigDecimal(amount));
    } else {
      this.cobbleDollars$self().getDataTracker().set(DATA_COBBLEDOLLARS_ID, amount.toString());
    }
  }
}
*/