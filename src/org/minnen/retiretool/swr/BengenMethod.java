package org.minnen.retiretool.swr;

import java.io.IOException;
import org.minnen.retiretool.util.IntPair;

public class BengenMethod
{
  /** Pre-computed annual Bengen SWR for years [30..40] using monthly inflation adjustments (325 = 3.25%). */
  public final static int[] bengenForYear      = new int[] { 370, 370, 365, 365, 360, 360, 355, 355, 350, 350, 345 };

  /** Pre-computed best stock perecent for years [30..40] (70 = 70%). */
  public final static int[] bengenPercentStock = new int[] { 70, 70, 70, 70, 70, 70, 70, 70, 70, 70, 70 };

  static {
    assert bengenForYear.length == bengenPercentStock.length;
  }

  /** @return highest Bengen SWR as an annual percentage for a retirement of `years` years (325 = 3.25%). */
  public static int lookUpSWR(int years)
  {
    return bengenForYear[years - 30];
  }

  /** @return percent stock that yields best Bengen SWR for a retirement of `years` years. */
  public static int lookUpPercentStock(int years)
  {
    return bengenPercentStock[years - 30];
  }

  /**
   * Determine the Bengen SWR for a retirement of `years` with a `percentStock` held in stock.
   * 
   * @param years length of retirement, i.e. the account balance must be >= 0 for this many years
   * @param percentStock the percent of stock (vs. bond) in the brokerage account
   * @return the largest Bengen SWR as an annualized percent (325 = 3.25%)
   */
  public static int findSWR(int years, int percentStock)
  {
    int prevSWR = 0;
    final int lastIndex = SwrLib.lastIndex(years);

    // Start at 2% and search in increments of 5 basis points.
    for (int swr = 200;; swr += 5) {
      boolean safe = true;
      for (int i = 0; i <= lastIndex; ++i) {
        MonthlyInfo info = SwrLib.runPeriod(i, swr / 100.0, years, percentStock, null);
        if (info.failed()) {
          safe = false;
          break;
        }
      }
      if (!safe) return prevSWR;
      prevSWR = swr;
    }
  }

  /**
   * Calculate the best Bengen SWR for a `years` retirement.
   * 
   * @param years length of retirement
   * @return the best SWR and the corresponding stock percent
   */
  public static IntPair findSWR(int years)
  {
    int bestSWR = 0;
    int bestPercentStock = 0;

    // Search over different stock/bond allocations assuming stock >= 50%.
    for (int percentStock = 50; percentStock <= 100; percentStock += 10) {
      int swr = findSWR(years, percentStock);
      if (swr > bestSWR) { // TODO best way to handle ties?
        bestSWR = swr;
        bestPercentStock = percentStock;
      }
      // System.out.printf("%d: %d | %d\n", percentStock, swr, bestSWR);
    }
    return new IntPair(bestSWR, bestPercentStock);
  }

  /** Print information about the best Bengen SWR for different retirement durations. */
  public static void printSWRs(int minYears, int maxYears)
  {
    for (int years = minYears; years <= maxYears; years++) {
      IntPair x = findSWR(years);
      final int swr = x.first;
      final int percentStock = x.second;
      System.out.printf("%d: %d  (%d%% stock)\n", years, swr, percentStock);
    }
  }

  public static void main(String[] args) throws IOException
  {
    SwrLib.setup();
    printSWRs(30, 40);
  }
}
