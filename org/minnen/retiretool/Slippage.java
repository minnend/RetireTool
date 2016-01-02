package org.minnen.retiretool;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.util.Fixed;

/**
 * Model that adjusts trade price to simulate slippage/spread.
 * 
 * @author David Minnen
 */
public class Slippage
{
  /** Fixed amount of slippage (in dollars) for each trade. */
  public final double          constSlip;

  /** Percent of trade price to slip (1.0 = 1%). */
  public final double          percentSlip;

  public final long            fixedConstSlip;
  public final long            fixedPercentSlip;

  public static final Slippage None = new Slippage(0.0, 0.0);

  /**
   * Construct a new slippage model.
   * 
   * @param constSlip fixed amount added or subtracted from the price (0.1 = 10 cents).
   * @param percentSlip percent of trade price to add/subtract (1.0 = 1%).
   */
  public Slippage(double constSlip, double percentSlip)
  {
    this.constSlip = constSlip;
    this.percentSlip = percentSlip / 100.0;
    this.fixedConstSlip = Fixed.toFixed(this.constSlip);
    this.fixedPercentSlip = Fixed.toFixed(this.percentSlip);
  }

  public double applyToBuy(double price)
  {
    // Price goes up when buying.
    return price * (1.0 + percentSlip) + constSlip;
  }

  public double applyToSell(double price)
  {
    // Price goes down when selling.
    return price * (1.0 - percentSlip) - constSlip;
  }

  public long applyToBuy(long price)
  {
    // Price goes up when buying.
    if (fixedPercentSlip != 0) {
      price = Fixed.mul(price, Fixed.ONE + fixedPercentSlip);
    }
    return price + fixedConstSlip;
  }

  public long applyToSell(long price)
  {
    // Price goes down when selling.
    if (fixedPercentSlip != 0) {
      price = Fixed.mul(price, Fixed.ONE - fixedPercentSlip);
    }
    return price - fixedConstSlip;
  }

  public double adjustForAssetChange(double balance, int index, Sequence currentAsset, Sequence nextAsset)
  {
    if (nextAsset != currentAsset) {
      // Slippage for sale.
      if (currentAsset != null) {
        double listedPrice = currentAsset.get(index, 0);
        double tradePrice = applyToSell(listedPrice);
        double ratio = tradePrice / listedPrice;
        assert ratio <= 1.0;
        balance *= ratio;
      }

      // Slippage for buy.
      if (nextAsset != null) {
        double listedPrice = nextAsset.get(index, 0);
        double tradePrice = applyToBuy(listedPrice);
        double ratio = listedPrice / tradePrice;
        assert ratio <= 1.0;
        balance *= ratio;
      }

      currentAsset = nextAsset;
    }
    return balance;
  }
}
