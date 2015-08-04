package org.minnen.retiretool;

import org.minnen.retiretool.FeatureVec;
import org.minnen.retiretool.Sequence;

/*
 * Functions that operate on Shiller's data (S&P price, dividends, CPI, GS10, CAPE).
 * Data: http://www.econ.yale.edu/~shiller/data.htm
 */
public class Shiller
{
  public static int PRICE = 0;
  public static int DIV   = 1;
  public static int CPI   = 2;
  public static int GS10  = 3;
  public static int CAPE  = 4;

  /** @return Sequence containing all CAPE data. */
  public static Sequence getCapeData(Sequence shiller)
  {
    return getCapeData(shiller, 0, shiller.size() - 1);
  }

  /** @return Sequence containing CAPE data in the given range (inclusive). */
  public static Sequence getCapeData(Sequence shiller, int iStart, int iEnd)
  {
    Sequence cape = new Sequence("CAPE");
    for (int i = iStart; i <= iEnd; ++i) {
      cape.addData(shiller.get(i, CAPE), shiller.getTimeMS(i));
    }
    return cape;
  }

  /** @return Sequence containing all stock and dividend data. */
  public static Sequence getStockData(Sequence shiller)
  {
    return getStockData(shiller, 0, shiller.size() - 1);
  }

  /** @return Sequence containing stock and dividend data in the given range (inclusive). */
  public static Sequence getStockData(Sequence shiller, int iStart, int iEnd)
  {
    Sequence seq = new Sequence("S&P");
    for (int i = iStart; i <= iEnd; ++i) {
      seq.addData(new FeatureVec(2, shiller.get(i, PRICE), shiller.get(i, DIV)), shiller.getTimeMS(i));
    }
    return seq;
  }

  /** @return Sequence containing all bond data. */
  public static Sequence getBondData(Sequence shiller)
  {
    return getBondData(shiller, 0, shiller.size() - 1);
  }

  /** @return Sequence containing bond data in the given range (inclusive). */
  public static Sequence getBondData(Sequence shiller, int iStart, int iEnd)
  {
    Sequence seq = new Sequence("US 10-year Bonds");
    for (int i = iStart; i <= iEnd; ++i) {
      seq.addData(shiller.get(i, GS10), shiller.getTimeMS(i));
    }
    return seq;
  }

  /**
   * Calculate cumulative returns for mixed stock + bonds investment strategy.
   * 
   * In this model, stock dividends are used to buy stock and/or bonds to help maintain target allocation percentages.
   * 
   * @param
   * @param iStart index of month for first investment
   * @param nMonths number of months to run simulation
   * @param targetPercentStock target percent for stocks
   * @param targetPercentBonds target percent for bonds
   * @param rebalanceMonths rebalance every N months (zero for never)
   * @return Sequence containing cumulative returns for the investment mix.
   */
  @Deprecated
  public static Sequence calcMixedReturns(Sequence shiller, int iStart, int nMonths, double targetPercentStock,
      double targetPercentBonds, int rebalanceMonths)
  {
    if (iStart < 0 || nMonths < 1 || iStart + nMonths >= shiller.size()) {
      throw new IllegalArgumentException(String.format("iStart=%d, nMonths=%d, size=%d", iStart, nMonths,
          shiller.size()));
    }

    double targetPercentCash = 100.0 - (targetPercentStock + targetPercentBonds);
    if (targetPercentCash < 0.0 || targetPercentCash > 100.0) {
      throw new IllegalArgumentException(String.format("Invalid asset allocation (%stock=%.3f, %bonds=%.3f)",
          targetPercentStock, targetPercentBonds));
    }

    Sequence bondData = getBondData(shiller);
    Sequence seq = new Sequence("Mixed Stocks & Bonds");

    final double principal = 1000.0;
    double stockValue = principal * targetPercentStock / 100.0;
    double bondValue = principal * targetPercentBonds / 100.0;
    double shares = stockValue / shiller.get(iStart, PRICE);
    double cash = principal - (stockValue + bondValue);
    seq.addData(principal, shiller.getTimeMS(iStart));
    for (int i = iStart; i < iStart + nMonths; ++i) {
      double stockPrice = shiller.get(i + 1, PRICE);
      stockValue = shares * stockPrice;

      // Collect dividends from stocks for previous month.
      cash += shares * shiller.get(i, DIV);

      // Update bond value for this month.
      if (bondValue > 0.0) {
        Bond bond = new Bond(bondData, bondValue, i);
        bondValue = bond.price(i + 1);
      }
      double total = bondValue + stockValue + cash;
      // System.out.printf("%d: [%.2f, %.2f, %.2f]  [%.1f, %.1f, %.1f]  %.2f\n", i, stockValue, bondValue, cash, 100.0
      // * stockValue / total, 100.0 * bondValue / total, 100.0 * cash / total, total);

      // Rebalance as requested.
      int months = i - iStart;
      if (rebalanceMonths > 0 && months % rebalanceMonths == 0) {
        // Sell stocks if we're over the requested allocation.
        double percentStock = 100.0 * stockValue / total;
        if (percentStock > targetPercentStock) {
          double sell = total * (percentStock - targetPercentStock) / 100.0;
          stockValue -= sell;
          cash += sell;
          shares = stockValue / stockPrice;
        }

        // Sell bonds if we're over the requested allocation.
        double percentBonds = 100.0 * bondValue / total;
        if (percentBonds > targetPercentBonds) {
          double sell = total * (percentBonds - targetPercentBonds) / 100.0;
          bondValue -= sell;
          cash += sell;
        }
      }

      // Use cash to buy stocks / bonds to get closer to desired asset allocation.
      if (cash > 0.0) {
        double percentStock = 100.0 * stockValue / total;
        if (percentStock < targetPercentStock) {
          double spend = Math.min(total * (targetPercentStock - percentStock) / 100.0, cash);
          stockValue += spend;
          cash -= spend;
          shares = stockValue / stockPrice;
        }

        double percentBonds = 100.0 * bondValue / total;
        if (percentBonds < targetPercentBonds) {
          double spend = Math.min(total * (targetPercentBonds - percentBonds) / 100.0, cash);
          bondValue += spend;
          cash -= spend;
        }
      }
      total = bondValue + stockValue + cash;

      // System.out.printf("      [%.2f, %.2f, %.2f]  [%.1f, %.1f, %.1f]  %.2f\n", stockValue, bondValue, cash, 100.0
      // * stockValue / total, 100.0 * bondValue / total, 100.0 * cash / total, total);

      // Add data point for current value.
      seq.addData(total, shiller.getTimeMS(i + 1));
    }

    return seq._div(principal);
  }
}
