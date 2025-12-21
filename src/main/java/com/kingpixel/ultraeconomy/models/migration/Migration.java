package com.kingpixel.ultraeconomy.models.migration;

import com.kingpixel.cobbleutils.Model.EconomyUse;
import lombok.Data;

/**
 *
 * @author Carlos Varas Alonso - 21/12/2025 0:57
 */
@Data
public class Migration {
  private String economyId;
  private String currencyId;
  private String migrationToCurrencyId;

  public Migration(String economyId, String currencyId, String migrationToCurrencyId) {
    this.economyId = economyId;
    this.currencyId = currencyId;
    this.migrationToCurrencyId = migrationToCurrencyId;
  }

  public EconomyUse toEconomyUse() {
    return new EconomyUse(economyId, currencyId);
  }
}
