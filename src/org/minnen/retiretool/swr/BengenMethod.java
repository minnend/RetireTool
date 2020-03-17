package org.minnen.retiretool.swr;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.swr.data.BengenTable;
import org.minnen.retiretool.util.IntPair;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

public class BengenMethod
{
  /**
   * Determine the Bengen SWR for a retirement of `years` with a `percentStock` held in stock.
   * 
   * All calculated SWRs will be rounded down to the nearest five basis points (3.27% -> 3.25%).
   * 
   * @param years length of retirement, i.e. the account balance must be >= 0 for this many years
   * @param percentStock the percent of stock (vs. bond) in the brokerage account
   * @return the largest Bengen SWR as an annualized percent (325 = 3.25%)
   */
  public static int findSWR(int years, int percentStock)
  {
    return findSWR(years, percentStock, 5);
  }

  /**
   * Determine the Bengen SWR for a retirement of `years` with a `percentStock` held in stock.
   * 
   * @param years length of retirement, i.e. the account balance must be >= 0 for this many years
   * @param percentStock the percent of stock (vs. bond) in the brokerage account
   * @param quantum require the SWR to have a multiple of this number of basis points
   * @return the largest Bengen SWR as an annualized percent (325 = 3.25%)
   */
  public static int findSWR(int years, int percentStock, int quantum)
  {
    assert years > 0 && percentStock >= 0 && percentStock <= 100 && quantum >= 1;

    // Binary search for largest WR that is always safe.
    int lowSWR = 10; // 0.1% will always works
    int highSWR = 10000; // never go over 100%
    while (highSWR - lowSWR > quantum) {
      final int swr = (lowSWR + highSWR) / (2 * quantum) * quantum;
      assert swr >= lowSWR && swr <= highSWR && swr % quantum == 0 : swr;
      if (SwrLib.isSafe(swr, years, percentStock)) {
        lowSWR = swr;
      } else {
        highSWR = swr;
      }
    }
    return lowSWR;
  }

  /**
   * Calculate the best Bengen SWR for a `years` retirement.
   * 
   * @param years length of retirement
   * @return the best SWR and the corresponding stock percent
   */
  public static IntPair findSWR(int years, int minPercentStock, int maxPercentStock, int step)
  {
    assert years > 0 && maxPercentStock >= minPercentStock && step > 0;

    int bestSWR = 0;
    int bestPercentStock = 0;

    // Search over different stock/bond allocations assuming stock >= 50%.
    for (int percentStock = minPercentStock; percentStock <= maxPercentStock; percentStock += step) {
      int swr = findSWR(years, percentStock, 5);
      if (swr > bestSWR) { // TODO best way to handle ties?
        bestSWR = swr;
        bestPercentStock = percentStock;
      }
      // System.out.printf("%d: %d | %d\n", percentStock, swr, bestSWR);
    }
    return new IntPair(bestSWR, bestPercentStock);
  }

  /** @return Sequence holding the SWR for each starting month. */
  public static Sequence findSwrSequence(int nRetireYears, int percentStock)
  {
    Sequence seq = new Sequence(String.format("%d year SWR (%d/%d)", nRetireYears, percentStock, 100 - percentStock));
    final int lastIndex = SwrLib.lastIndex(nRetireYears);
    for (int i = 0; i <= lastIndex; ++i) {
      final int swr = SwrLib.findSwrForYear(i, nRetireYears, percentStock, 1);
      seq.addData(swr / 100.0, SwrLib.time(i));
    }
    return seq;
  }

  /** Print information about the best Bengen SWR for different retirement durations. */
  public static String printSWRs(int minYears, int maxYears, int minPercentStock, int maxPercentStock, int step)
  {
    assert maxYears >= minYears && maxPercentStock >= minPercentStock && step > 0;

    List<String> results = new ArrayList<>();
    for (int years = minYears; years <= maxYears; years++) {
      IntPair x = findSWR(years, minPercentStock, maxPercentStock, step);
      final int swr = x.first;
      final int percentStock = x.second;
      System.out.printf("%d: %d (%d%% stock)\n", years, swr, percentStock);
      results.add(String.format("%d,%d,%d", years, swr, percentStock));
    }
    String s = String.join("|", results);
    System.out.println(s);
    return s;
  }

  public static void saveMaxSwr(int percentStock) throws IOException
  {
    List<Sequence> seqs = new ArrayList<>();
    for (int years = 20; years <= 50; years += 10) {
      seqs.add(findSwrSequence(years, percentStock));
    }

    Chart.saveLineChart(new File(DataIO.getOutputPath(), "max-swr.html"), "Max SWR", "100%", "800px",
        ChartScaling.LINEAR, ChartTiming.MONTHLY, seqs);
  }

  public static void main(String[] args) throws IOException
  {
    SwrLib.setupWithDefaultFiles();

    // printSWRs(1, 50, 70, 70, 10);
    saveMaxSwr(75);
  }
}
