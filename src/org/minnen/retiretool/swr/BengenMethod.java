package org.minnen.retiretool.swr;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.swr.data.BengenEntry;
import org.minnen.retiretool.swr.data.BengenTable;
import org.minnen.retiretool.swr.data.MarwoodEntry;
import org.minnen.retiretool.swr.data.MonthlyInfo;
import org.minnen.retiretool.util.FinLib.Inflation;
import org.minnen.retiretool.util.IntPair;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

public class BengenMethod
{
  public static MonthlyInfo runPeriod(BengenEntry info)
  {
    return runPeriod(info, null);
  }

  public static MonthlyInfo runPeriod(BengenEntry info, List<MonthlyInfo> trajectory)
  {
    final int index = SwrLib.indexForTime(info.time);
    return runPeriod(index, info.swr / 100.0, info.retirementYears, info.percentStock, Inflation.Real, trajectory);
  }

  public static MonthlyInfo runPeriod(MarwoodEntry info)
  {
    return runPeriod(info, Inflation.Real, null);
  }

  public static MonthlyInfo runPeriod(MarwoodEntry info, Inflation inflation, List<MonthlyInfo> trajectory)
  {
    final int index = SwrLib.indexForTime(info.retireTime);
    return runPeriod(index, info.dmswr / 100.0, info.retirementYears, info.percentStock, Inflation.Real, trajectory);
  }

  /**
   * Simulate a retirement.
   * 
   * @param iStart index of retirement month (first withdrawal)
   * @param withdrawalRate annual withdrawal rate as a percent (3.5 = 3.5%)
   * @param retirementYears number of years of retirement
   * @param percentStock percent stock (vs. bonds) held in brokerage account
   * @param inflation if nominal, adjust for inflation here, else assume growth is pre-adjusted
   * @param trajectory if non-null, will be filled with monthly info objects (optional)
   * @return info for the final month: either the last month of retirement or the failure month
   */
  public static MonthlyInfo runPeriod(int iStart, double withdrawalRate, int retirementYears, int percentStock,
      Inflation inflation, List<MonthlyInfo> trajectory)
  {
    final int iEnd = Math.min(iStart + 12 * retirementYears, SwrLib.length());
    return run(iStart, iEnd, withdrawalRate, percentStock, inflation, trajectory);
  }

  /**
   * Simulate a Bengen-style retirement starting with a fixed withdrawal rate.
   * 
   * @param iStart index of retirement month (first withdrawal)
   * @param iEnd last index of simulation period (exclusive)
   * @param withdrawalRate annual withdrawal rate as a percent (3.5 = 3.5%) *
   * @param percentStock percent stock (vs. bonds) held in brokerage account
   * @param inflation if nominal, adjust for inflation here, else assume growth is pre-adjusted
   * @param trajectory if non-null, will be filled with monthly info objects (optional)
   * @return info for the final month: either the last month of retirement or the failure month
   */
  public static MonthlyInfo run(int iStart, int iEnd, double withdrawalRate, int percentStock, Inflation inflation,
      List<MonthlyInfo> trajectory)
  {
    assert iStart >= 0 && iStart < SwrLib.length();
    assert iEnd > iStart && iEnd <= SwrLib.length();
    assert withdrawalRate > 0.0 : withdrawalRate;

    final long retireTime = SwrLib.time(iStart);
    double balance = 1e6; // starting balance is mostly arbitrary since all results are relative
    double monthlyWithdrawal = balance * withdrawalRate / 1200.0;

    MonthlyInfo info = null;
    for (int i = iStart; i < iEnd; ++i) {
      final double startBalance = balance;

      balance -= monthlyWithdrawal; // make withdrawal at the beginning of the month.
      if (balance > 0) {
        balance *= SwrLib.growth(i, percentStock); // remaining balance grows during the rest of month.
      }

      final double annualSalary = monthlyWithdrawal * 12;
      info = new MonthlyInfo(retireTime, SwrLib.time(i), i - iStart + 1, monthlyWithdrawal, startBalance, balance,
          annualSalary);
      if (trajectory != null) trajectory.add(info);
      if (info.failed()) return info;

      assert balance > -1e-5; // TODO avoid floating point issues
      if (inflation == Inflation.Nominal) {
        monthlyWithdrawal *= SwrLib.inflation(i);
      }
    }
    return info;
  }

  /** @return true if the withdrawal rate works for all retirement starting times. */
  public static boolean isSafe(int withdrawalRate, int years, int percentStock)
  {
    final int lastIndex = SwrLib.lastIndex(years);
    final double floatWR = withdrawalRate / 100.0;
    for (int i = 0; i <= lastIndex; ++i) {
      MonthlyInfo info = BengenMethod.runPeriod(i, floatWR, years, percentStock, Inflation.Real, null);
      if (info.failed()) return false;
      assert info.endBalance > 0 && info.monthlyIncome > 0;
    }
    return true;
  }

  /**
   * Find the SWR for a given retirement date.
   * 
   * @param index index of retirement date
   * @param years number of years of retirement
   * @param percentStock percent stock (vs bonds) in asset allocation (70 = 70%)
   * @param inflation if nominal, adjust for inflation here, else assume growth is pre-adjusted
   * @param quantum withdrawalRate % quantum == 0
   * @return safe withdrawal rate for the given retirement index and parameters
   */
  public static int findSwrForDate(int index, int years, int percentStock, Inflation inflation, int quantum)
  {
    int lowSWR = 0;
    int highSWR = 10001;

    BengenEntry entry = BengenTable.get(years - 1, percentStock, SwrLib.time(index));
    if (entry != null) {
      highSWR = entry.swr; // SWR for N years can't be larger than SWR for (N-1) years
    }

    while (highSWR - lowSWR > quantum) {
      final int swr = (lowSWR + highSWR) / (2 * quantum) * quantum;
      assert swr >= lowSWR && swr <= highSWR && swr % quantum == 0 : swr;
      MonthlyInfo info = BengenMethod.runPeriod(index, swr / 100.0, years, percentStock, inflation, null);
      if (info.ok()) {
        lowSWR = swr;
      } else {
        highSWR = swr;
      }
    }
    return lowSWR;
  }

  /**
   * Determine the Bengen SWR for a retirement of `years` with a `percentStock` held in stock.
   * 
   * All calculated SWRs will be rounded down to the nearest five basis points (3.27% -> 3.25%).
   * 
   * @param retirementYears length of retirement, i.e. the account balance must be >= 0 for this many years
   * @param percentStock the percent of stock (vs. bond) in the brokerage account
   * @return the largest Bengen SWR as an annualized percent (325 = 3.25%)
   */
  public static int findSWR(int retirementYears, int percentStock)
  {
    return findSWR(retirementYears, percentStock, 5);
  }

  /**
   * Determine the Bengen SWR for a retirement of `years` with a `percentStock` held in stock.
   * 
   * @param retirementYears length of retirement, i.e. the account balance must be >= 0 for this many years
   * @param percentStock the percent of stock (vs. bond) in the brokerage account
   * @param quantum require the SWR to have a multiple of this number of basis points
   * @return the largest Bengen SWR as an annualized percent (325 = 3.25%)
   */
  public static int findSWR(int retirementYears, int percentStock, int quantum)
  {
    assert retirementYears > 0 && percentStock >= 0 && percentStock <= 100 && quantum >= 1;

    // Binary search for largest WR that is always safe.
    int lowSWR = 10; // 0.1% will always works
    int highSWR = 10000; // never go over 100%
    while (highSWR - lowSWR > quantum) {
      final int swr = (lowSWR + highSWR) / (2 * quantum) * quantum;
      assert swr >= lowSWR && swr <= highSWR && swr % quantum == 0 : swr;
      if (BengenMethod.isSafe(swr, retirementYears, percentStock)) {
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
   * @param retirementYears length of retirement
   * @return the best SWR and the corresponding stock percent
   */
  public static IntPair findSWR(int retirementYears, int minPercentStock, int maxPercentStock, int step)
  {
    assert retirementYears > 0 && maxPercentStock >= minPercentStock && step > 0;

    int bestSWR = 0;
    int bestPercentStock = 0;

    // Search over different stock/bond allocations assuming stock >= 50%.
    for (int percentStock = minPercentStock; percentStock <= maxPercentStock; percentStock += step) {
      int swr = findSWR(retirementYears, percentStock, 5);
      if (swr > bestSWR) { // TODO best way to handle ties?
        bestSWR = swr;
        bestPercentStock = percentStock;
      }
      // System.out.printf("%d: %d | %d\n", percentStock, swr, bestSWR);
    }
    return new IntPair(bestSWR, bestPercentStock);
  }

  /** @return Sequence holding the SWR for each starting month. */
  public static Sequence findSwrAcrossTime(int nRetireYears, int percentStock)
  {
    Sequence seq = new Sequence(String.format("%d year SWR (%d/%d)", nRetireYears, percentStock, 100 - percentStock));
    final int lastIndex = SwrLib.lastIndex(nRetireYears);
    for (int i = 0; i <= lastIndex; ++i) {
      final int swr = findSwrForDate(i, nRetireYears, percentStock, Inflation.Real, 1);
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
      seqs.add(findSwrAcrossTime(years, percentStock));
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
