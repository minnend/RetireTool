package org.minnen.retiretool.swr;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.swr.data.BengenEntry;
import org.minnen.retiretool.swr.data.BengenTable;
import org.minnen.retiretool.swr.data.MarwoodEntry;
import org.minnen.retiretool.swr.data.MonthlyInfo;
import org.minnen.retiretool.util.FinLib.Inflation;
import org.minnen.retiretool.util.IntPair;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.Writer;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;
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
    return runPeriod(index, info.swr / 100.0, info.retirementYears, info.percentStock, Inflation.Real, trajectory);
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
    final int swrBasisPoints = (int) Math.round(withdrawalRate * 100);
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
      info = new MonthlyInfo(retireTime, SwrLib.time(i), swrBasisPoints, i - iStart + 1, monthlyWithdrawal,
          startBalance, balance, annualSalary);
      if (trajectory != null) trajectory.add(info);
      if (info.failed()) return info;

      assert balance > -1e-5; // TODO avoid floating point issues
      if (inflation == Inflation.Nominal) {
        monthlyWithdrawal *= SwrLib.inflation(i);
      }
    }

    if (trajectory != null) {
      MonthlyInfo.setFinalBalance(balance, trajectory);
    } else {
      info.finalBalance = balance;
    }
    assert info.finalBalance == balance;
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
  public static void printSWRs(int[] retirementYearsList, int[] percentStockList)
  {
    for (int retirementYears : retirementYearsList) {
      for (int percentStock : percentStockList) {
        final int swr = BengenTable.getSWR(retirementYears, percentStock);
        System.out.printf("%d %d: %d\n", retirementYears, percentStock, swr);
      }
    }
  }

  public static void createChartSwrAcrossTime(int percentStock) throws IOException
  {
    List<Sequence> seqs = new ArrayList<>();
    for (int years = 10; years <= 50; years += 10) {
      seqs.add(findSwrAcrossTime(years, percentStock));
    }

    Chart.saveLineChart(new File(DataIO.getOutputPath(), "max-swr-across-time.html"), "Max SWR", "100%", "800px",
        ChartScaling.LINEAR, ChartTiming.MONTHLY, seqs);
  }

  public static IntPair getSuccessFail(int withdrawalRate, int retirementYears, int percentStock)
  {
    final int n = SwrLib.lastIndex(retirementYears) + 1;
    int nWin = 0;
    for (int i = 0; i < n; ++i) {
      final int swr = BengenTable.get(retirementYears, percentStock, SwrLib.time(i)).swr;
      if (withdrawalRate <= swr) ++nWin;
    }
    final int nFail = n - nWin;
    return new IntPair(nWin, nFail);
  }

  public static int interpolate(int x, int... pairs)
  {
    int n = pairs.length;
    assert n % 2 == 0;
    n = n / 2;

    int[] keys = new int[n];
    int[] values = new int[n];
    for (int i = 0; i < n; ++i) {
      keys[i] = pairs[i];
      values[i] = pairs[n + i];
    }

    if (x <= keys[0]) return values[0];
    if (x >= keys[n - 1]) return values[n - 1];

    int index = Arrays.binarySearch(keys, x);
    if (index >= 0) return values[index];

    index = -(index + 1);

    final int a = keys[index - 1];
    final int b = keys[index];
    assert a <= x && b >= x;

    final int p = values[index - 1];
    final int q = values[index];

    final double percent = (double) (x - a) / (b - a);
    return p + (int) Math.round((q - p) * percent);
  }

  public static void createSuccessChart(File file, int[] retirementYearsList, int[] percentStockList,
      int[] withdrawalRateList) throws IOException
  {
    // Build color table; colors adapted from: https://learnui.design/tools/data-color-picker.html#divergent
    // TODO move to util.Colors.
    String[] colors = new String[101];
    for (int i = 0; i < colors.length; ++i) {
      final int red = interpolate(i, //
          0, 30, 50, 80, 100, //
          255, 250, 253, 241, 100);
      final int green = interpolate(i, //
          0, 30, 50, 80, 100, //
          90, 148, 183, 232, 191);
      final int blue = interpolate(i, //
          0, 30, 50, 80, 100, //
          80, 116, 122, 133, 124);
      assert red >= 0 && red <= 255 : red;
      assert green >= 0 && green <= 255 : green;
      assert blue >= 0 && blue <= 255 : blue;
      colors[i] = String.format("rgb(%d, %d, %d)", red, green, blue);
    }

    StringWriter sw = new StringWriter(8192);
    try (Writer writer = new Writer(sw)) {
      writer.write("<html><head>\n");
      writer.write("<title>Bengen Success Chart</title>\n");
      writer.write("<link rel='stylesheet' href='css/success-chart.css'>\n");
      writer.write("</head>\n");

      writer.write("<body>\n");
      writer.write("<table class='success-chart'>\n");

      writer.write("<tr>");
      writer.write("<th class='thick-right' colspan=2 rowspan=2></th>");
      writer.writef("<th colspan=%d>Annual Withdrawal Rate</th>", withdrawalRateList.length);
      writer.write("</tr>\n");

      writer.write("<tr>");
      for (int withdrawalRate : withdrawalRateList) {
        writer.writef("<th>%.2f%%</th>", withdrawalRate / 100.0);
      }
      writer.write("</tr>\n");

      for (int iPercent = 0; iPercent < percentStockList.length; ++iPercent) {
        final int percentStock = percentStockList[iPercent];
        writer.writef("<tr class='thick-top'><th rowspan=%d>%d%%<br/>Stock</th>\n", retirementYearsList.length,
            percentStock);
        for (int iYears = 0; iYears < retirementYearsList.length; ++iYears) {
          if (iYears > 0) writer.write("<tr>");
          final int retirementYears = retirementYearsList[iYears];
          final int n = SwrLib.lastIndex(retirementYears) + 1;
          writer.writef("<th class='thick-right'>%d Years</th>", retirementYears);
          for (int withdrawalRate : withdrawalRateList) {
            final int nWin = getSuccessFail(withdrawalRate, retirementYears, percentStock).first;
            int percent = (int) Math.round(nWin * 100.0 / n);
            if (percent == 100 && nWin < n) percent = 99; // don't round to 100%
            writer.writef("<td style='background-color: %s'>%d%%</td>", colors[percent], percent);
          }
          writer.write("</tr>\n");
        }
      }

      writer.write("</table>\n");
      writer.write("</body></html>\n");
    }

    // Dump string to file.
    try (Writer writer = new Writer(file)) {
      writer.write(sw.toString());
    }
  }

  public static void main(String[] args) throws IOException
  {
    Inflation inflation = Inflation.Real;
    SwrLib.setup(SwrLib.getDefaultBengenFile(), null, inflation); // DMSWR data not needed

    // Examples from paper.
    MonthlyInfo info = BengenMethod.runPeriod(SwrLib.indexForTime(Month.JANUARY, 1950), 4.0, 30, 50, inflation, null);
    System.out.printf("[%s] -> [%s] %.2f%% -> %f\n", TimeLib.formatYM(info.retireTime),
        TimeLib.formatYM(info.currentTime), info.swr / 100.0, info.finalBalance / 1e6);

    List<MonthlyInfo> trajectory = new ArrayList<>();
    info = BengenMethod.runPeriod(SwrLib.indexForTime(Month.JANUARY, 1965), 8.0, 30, 50, inflation, trajectory);
    System.out.printf("[%s] -> [%s] %.2f%% -> %f\n", TimeLib.formatYM(info.retireTime),
        TimeLib.formatYM(info.currentTime), info.swr / 100.0, info.finalBalance / 1e6);
    System.out.println(trajectory.get(trajectory.size() - 1));

    int swr = BengenTable.getSWR(35, 75);
    System.out.printf("SWR (35, 75): %d\n", swr);
    for (FeatureVec v : BengenTable.getAcrossTime(35, 75)) {
      if ((int) Math.round(v.get(0)) == swr) {
        System.out.printf(" [%s]\n", TimeLib.formatYM(v.getTime()));
      }
    }

    // Bengen SWR for different retirement durations.
    Sequence seqBengenSWR = new Sequence("Bengen SWR");
    List<String> labels = new ArrayList<>();
    for (int retirementYears = 10; retirementYears <= 60; ++retirementYears) {
      swr = BengenTable.getSWR(retirementYears, 75);
      seqBengenSWR.addData(swr / 100.0);
      labels.add(String.format("%d", retirementYears));
      // System.out.printf("%d %d\n", retirementYears, swr);
    }
    File file = new File(DataIO.getOutputPath(), "bengen-swr-durations.html");
    ChartConfig config = ChartConfig.build(file, ChartConfig.Type.Bar, "Bengen SWR", labels.toArray(new String[0]),
        null, "100%", "500px", 0, 8, 0.5, ChartScaling.LINEAR, ChartTiming.INDEX, 0, seqBengenSWR);
    config.setAxisLabelFontSize(24);
    config.setTickFormatter(null, "return this.value + '%';");
    config.setTitleConfig("margin: 0, y: 32, style: { fontSize: 32 }");
    config.setTickInterval(2, 1);
    config.setDataLabelConfig(true, -90, "#fff", 2, 1, 4, 16, false);
    config.setAnimation(false);
    Chart.saveChart(config);

    // Save success chart.
    file = new File(DataIO.getOutputPath(), "bengen-success-chart.html");
    int[] durations = new int[] { 30, 40, 50 };
    int[] percentStockList = new int[] { 100, 75, 50, 25, 0 };
    int[] withdrawalRates = new int[] { 300, 325, 350, 375, 400, 425, 450, 475, 500 };
    createSuccessChart(file, durations, percentStockList, withdrawalRates);

    // printSWRs(new int[] { 20, 30, 40, 50, 60 }, BengenTable.percentStockList);
    // createChartSwrAcrossTime(75);
  }
}
