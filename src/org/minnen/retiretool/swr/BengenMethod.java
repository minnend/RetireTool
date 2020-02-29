package org.minnen.retiretool.swr;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.util.IntPair;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

public class BengenMethod
{
  /** Pre-computed annual Bengen SWR for different retirement durations (325 = 3.25%). */
  private final static Map<Integer, Integer> yearsToSWR                     = new HashMap<>();

  /** Pre-computed best stock percent for different retirement durations (70 = 70%). */
  private final static Map<Integer, Integer> yearsToPercentStock            = new HashMap<>();

  private final static String                precomputedBengenOptAllocation = "1,8855,10|2,4230,10|3,2685,0|4,1870,0|5,1455,0|"
      + "6,1210,0|7,1050,0|8,935,0|9,850,20|10,775,10|11,715,10|12,655,10|13,615,10|14,585,20|15,560,20|"
      + "16,530,30|17,500,30|18,480,40|19,465,50|20,450,60|21,440,70|22,430,70|23,420,70|24,410,70|25,400,70|"
      + "26,395,70|27,390,70|28,385,70|29,375,60|30,375,70|31,370,70|32,365,70|33,365,70|34,360,70|35,360,70|"
      + "36,355,70|37,355,70|38,350,70|39,350,70|40,350,70|41,345,70|42,345,70|43,340,70|44,340,70|45,340,70|"
      + "46,335,70|47,335,70|48,335,70|49,330,70|50,330,70";

  private final static String                precomputedBengenStock70       = "1,7400,70|2,3645,70|3,2265,70|4,1685,70|5,1375,70|"
      + "6,1180,70|7,1035,70|8,925,70|9,820,70|10,730,70|11,680,70|12,630,70|13,595,70|14,570,70|15,545,70|"
      + "16,515,70|17,495,70|18,475,70|19,465,70|20,450,70|21,440,70|22,430,70|23,420,70|24,410,70|25,400,70|"
      + "26,395,70|27,390,70|28,385,70|29,375,70|30,375,70|31,370,70|32,365,70|33,365,70|34,360,70|35,360,70|"
      + "36,355,70|37,355,70|38,350,70|39,350,70|40,350,70|41,345,70|42,345,70|43,340,70|44,340,70|45,340,70|"
      + "46,335,70|47,335,70|48,335,70|49,330,70|50,330,70";

  static {
    // Parse bengen data and populate SWR and percent stock maps.
    // String[] entries = precomputedBengenOptAllocation.split("\\|");
    String[] entries = precomputedBengenStock70.split("\\|");
    for (String entry : entries) {
      String[] fields = entry.trim().split(",");
      assert fields.length == 3 : entry;
      final int years = Integer.parseInt(fields[0]);
      final int swr = Integer.parseInt(fields[1]);
      final int percentStock = Integer.parseInt(fields[2]);
      yearsToSWR.put(years, swr);
      yearsToPercentStock.put(years, percentStock);
    }
    assert yearsToSWR.size() == yearsToPercentStock.size();
    assert yearsToSWR.size() == 50;
  }

  /** @return highest Bengen SWR as an annual percentage for a retirement of `years` years (325 = 3.25%). */
  public static int lookUpSWR(int years)
  {
    return yearsToSWR.get(years);
  }

  /** @return percent stock that yields best Bengen SWR for a retirement of `years` years. */
  public static int lookUpPercentStock(int years)
  {
    return yearsToPercentStock.get(years);
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

    int lowSWR = 10; // 0.1% will always works
    int highSWR = 10000; // never go over 100%
    while (highSWR - lowSWR > quantum) {
      final int swr = (lowSWR + highSWR) / (2 * quantum) * quantum;
      assert swr >= lowSWR && swr <= highSWR && swr % quantum == 0 : swr;
      if (SwrLib.isSafe(swr / 100.0, years, percentStock)) {
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

  private static void verifyPrecomputedSWRs()
  {
    for (int years = 1; years <= 50; ++years) {
      final int swr = lookUpSWR(years);
      final int percentStock = lookUpPercentStock(years);
      assert SwrLib.isSafe(swr / 100.0, years, percentStock); // safe value should be safe
      assert !SwrLib.isSafe((swr + 5) / 100.0, years, percentStock); // 5 bps above safe value should NOT be safe
      System.out.printf("%-2d: %3d, %2d%% stock | OK\n", years, swr, percentStock);
    }
  }

  /** @return Sequence holding the SWR for each starting month. */
  public static Sequence findSwrSequence(int nRetireYears, int percentStock)
  {
    Sequence seq = new Sequence(String.format("%d year SWR (%d/%d)", nRetireYears, percentStock, 100 - percentStock));
    final int lastIndex = SwrLib.lastIndex(nRetireYears);
    for (int i = 0; i <= lastIndex; ++i) {
      int swr = SwrLib.findSwrForYear(i, nRetireYears, percentStock, 1);
      seq.addData(swr / 100.0, SwrLib.time(i));
    }
    return seq;
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
    SwrLib.setup();

    // printSWRs(1, 50, 70, 70, 10);

    verifyPrecomputedSWRs();
    // saveMaxSwr(70);
  }
}
