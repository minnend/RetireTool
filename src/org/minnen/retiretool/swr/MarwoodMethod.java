package org.minnen.retiretool.swr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.IntPair;
import org.minnen.retiretool.util.TimeLib;

public class MarwoodMethod
{
  /** Pre-computed annual Bengen SWR for years [30..40] using monthly inflation adjustments (325 = 3.25%). */
  private final static int[] bengenForYear      = new int[] { 370, 370, 365, 365, 360, 360, 355, 355, 350, 350, 345 };

  /** Pre-computed best stock perecent for years [30..40] (70 = 70%). */
  private final static int[] bengenPercentStock = new int[] { 70, 70, 70, 70, 70, 70, 70, 70, 70, 70, 70 };

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
  public static int findBengenSWR(int years, int percentStock)
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
  public static IntPair findBengenSWR(int years)
  {
    int bestSWR = 0;
    int bestPercentStock = 0;

    // Search over different stock/bond allocations assuming stock >= 50%.
    for (int percentStock = 50; percentStock <= 100; percentStock += 10) {
      int swr = findBengenSWR(years, percentStock);
      if (swr > bestSWR) { // TODO best way to handle ties?
        bestSWR = swr;
        bestPercentStock = percentStock;
      }
      // System.out.printf("%d: %d | %d\n", percentStock, swr, bestSWR);
    }
    return new IntPair(bestSWR, bestPercentStock);
  }

  /** Print information about the best Bengen SWR for different retirement durations. */
  public static void findBengenSWR()
  {
    for (int years = 30; years <= 40; years++) {
      IntPair x = findBengenSWR(years);
      final int swr = x.first;
      final int percentStock = x.second;
      System.out.printf("%d: %d  (%d%% stock)\n", years, swr, percentStock);
    }
  }

  public static void findMarwoodSWR(int years, int lookbackYears)
  {
    final int bengenSWR = lookUpSWR(years);

    int maxSWR = 0;
    int maxIndex = -1;

    int nWin = 0, nFail = 0;

    final int lookbackMonths = lookbackYears * 12;
    for (int iStart = lookbackMonths; iStart <= SwrLib.lastIndex(years); ++iStart) {
      // Find best "virtual" retirement year within the lookback period.
      int bestSWR = 0;
      int bestIndex = -1;
      MonthlyInfo bestInfo = null;
      for (int iLookback = 0; iLookback <= lookbackMonths; ++iLookback) {
        final int iVirtualStart = iStart - iLookback;

        final int virtualYears = years + (int) Math.ceil(iLookback / 12.0);
        final int percentStock = lookUpPercentStock(virtualYears);
        final int swr = lookUpSWR(virtualYears);

        List<MonthlyInfo> virtualSalaries = new ArrayList<MonthlyInfo>();
        final int simYears = (int) Math.floor(iLookback / 12.0) + 1;
        MonthlyInfo info = SwrLib.runPeriod(iVirtualStart, swr / 100.0, simYears, percentStock, virtualSalaries);
        assert info.balance > 0;

        MonthlyInfo virtualNow = virtualSalaries.get(iLookback);
        assert virtualNow.index == iStart;

        final int impliedSWR = (int) Math.floor(virtualNow.percent() * 100);
        if (impliedSWR > bestSWR) {
          bestSWR = impliedSWR;
          bestIndex = iVirtualStart;
          bestInfo = virtualNow;
        }

        // System.out.printf(" %d [%s] vy=%d [%d] r: %7.4f [%d, %d] balance: $%.2f\n", iVirtualStart,
        // TimeLib.formatMonth(SwrLib.time(iVirtualStart)), virtualYears, swr, FinLib.mul2ret(r), impliedSWR, bestSWR,
        // currentBalance);
      }

      if (bestSWR > maxSWR) {
        maxSWR = bestSWR;
        maxIndex = iStart;
      }

      if (bestIndex != iStart) {
        List<MonthlyInfo> months = new ArrayList<>();
        MonthlyInfo info = SwrLib.runPeriod(iStart, bestSWR / 100.0, years, lookUpPercentStock(years), months);

        final double realBalance = info.balance * SwrLib.inflation(info.index, iStart);
        final double swrGain = FinLib.mul2ret((double) bestSWR / bengenSWR);
        System.out.printf("%d [%s] -> %d [%s] swr: %d +%.3f%% |$%.2f ($%.2f)| inflation: %f  %s\n", iStart,
            TimeLib.formatMonth(SwrLib.time(iStart)), bestIndex, TimeLib.formatMonth(SwrLib.time(bestIndex)), bestSWR,
            swrGain, realBalance, info.balance, SwrLib.inflation(iStart, bestIndex), bestInfo);
        assert info.balance > 0;
      }

      if (bestSWR > bengenSWR) {
        ++nWin;
      } else {
        ++nFail;
      }
    }

    System.out.printf("%d [%s]: %d\n", maxIndex, TimeLib.formatMonth(SwrLib.time(maxIndex)), maxSWR);
    System.out.printf("win=%d (%.2f%%), fail=%d / %d\n", nWin, 100.0 * nWin / (nWin + nFail), nFail, nWin + nFail);

  }

  public static void main(String[] args) throws IOException
  {
    SwrLib.setup();
    findMarwoodSWR(30, 10);
  }
}
