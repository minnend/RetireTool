package org.minnen.retiretool;

import org.minnen.retiretool.data.Sequence;

/**
 * Model that adjusts trade price to simulate slippage/spread.
 * 
 * @author David Minnen
 */
public class Slippage
{
  /** Fixed amount of slippage (in dollars) for each trade. */
  public final double          fixed;

  /** Percent of trade price to slip (1.0 = 1%). */
  public final double          percent;

  public static final Slippage None = new Slippage(0.0, 0.0);

  /**
   * Construct a new slippage model.
   * 
   * @param fixed fixed amount added or subtracted from the price (0.1 = 10 cents).
   * @param percent percent of trade price to add/subtract (1.0 = 1%).
   */
  public Slippage(double fixed, double percent)
  {
    this.fixed = fixed;
    this.percent = percent;
  }

  public double applyToBuy(double price)
  {
    // Price goes up when buying.
    return price * (1.0 + percent / 100.0) + fixed;
  }

  public double applyToSell(double price)
  {
    // Price goes down when selling.
    return price * (1.0 - percent / 100.0) - fixed;
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
