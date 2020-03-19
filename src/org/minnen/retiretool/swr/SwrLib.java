package org.minnen.retiretool.swr;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.minnen.retiretool.Bond;
import org.minnen.retiretool.Bond.DivOrPow;
import org.minnen.retiretool.BondFactory;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.Shiller;
import org.minnen.retiretool.swr.data.BengenEntry;
import org.minnen.retiretool.swr.data.BengenTable;
import org.minnen.retiretool.swr.data.MarwoodEntry;
import org.minnen.retiretool.swr.data.MarwoodTable;
import org.minnen.retiretool.swr.data.MonthlyInfo;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

public class SwrLib
{
  /** Stock, bonds, and CPI (inflation data). */
  public static Sequence                stock, bonds;
  private static Sequence               cpi;

  /** Each "Mul" sequence holds the multiplier representing growth for each month (1.01 = 1% growth). */
  private static Sequence               stockMul, bondsMul, cpiMul;

  /** Mixed stock/bond cumulative returns keyed by stock percent (70 = 70% stocks / 30% bonds). */
  private static Map<Integer, Sequence> mixedMap;

  /** @return timestamp (in ms) for the i'th data point. */
  public static long time(int i)
  {
    return stockMul.getTimeMS(i);
  }

  /** @return closest index for the given time */
  public static int indexForTime(long ms)
  {
    return stockMul.getClosestIndex(ms);
  }

  /** @return number of elements in the underlying data. */
  public static int length()
  {
    return stockMul.length();
  }

  /** @return index of the last month for which we can simulate a `years` retirement. */
  public static int lastIndex(int years)
  {
    return length() - years * 12;
  }

  /** @return percent as basis points, e.g. 3.2% -> 320. */
  public static int percentToBasisPoints(double percent)
  {
    return (int) Math.floor(percent * 100 + 1e-5);
  }

  /** @return total growth for a stock/bond portfolio over [from..to]. */
  public static double growth(int from, int to, int percentStock)
  {
    if (from == to) return 1.0;
    Sequence mixed = mixedMap.get(percentStock);
    return mixed.get(to, 0) / mixed.get(from, 0);
  }

  /**
   * Calculate the investment growth for the i'th month.
   * 
   * @param i index of month
   * @param percentStock percent invested in stock vs. bonds (70 => 70%)
   * @return growth for month `i` as a multiplier (4% => 1.04)
   */
  public static double growth(int i, int percentStock)
  {
    final double alpha = percentStock / 100.0;
    final double beta = 1.0 - alpha;
    return stockMul.get(i, 0) * alpha + bondsMul.get(i, 0) * beta;
  }

  /** @return inflation (as a multiplier) over [from..to]. */
  public static double inflation(int from, int to)
  {
    if (from == to) return 1.0;
    return cpi.get(to, 0) / cpi.get(from, 0);
  }

  public static double getNestEgg(int i, int lookbackYears, int percentStock)
  {
    final int lookbackMonths = lookbackYears * 12;
    assert i >= lookbackMonths;

    double nestEgg = 1e6 * SwrLib.inflation(-1, lookbackMonths); // $1M adjusted for inflation to start of sim
    return nestEgg * SwrLib.growth(lookbackMonths, i, percentStock); // update forward based on market growth
  }

  public static MonthlyInfo runPeriod(BengenEntry info)
  {
    return runPeriod(info, null);
  }

  public static MonthlyInfo runPeriod(BengenEntry info, List<MonthlyInfo> salaries)
  {
    final int index = indexForTime(info.time);
    return runPeriod(index, info.swr / 100.0, info.retirementYears, info.percentStock, salaries);
  }

  public static MonthlyInfo runPeriod(MarwoodEntry info)
  {
    return runPeriod(info, null);
  }

  public static MonthlyInfo runPeriod(MarwoodEntry info, List<MonthlyInfo> salaries)
  {
    final int index = indexForTime(info.retireTime);
    return runPeriod(index, info.swr / 100.0, info.retirementYears, info.percentStock, salaries);
  }

  /**
   * Simulate a retirement.
   * 
   * @param iStart index of retirement month (first withdrawal)
   * @param withdrawalRate annual withdrawal rate as a percent (3.5 = 3.5%)
   * @param years number of years of retirement
   * @param percentStock percent stock (vs. bonds) held in brokerage account
   * @param salaries if non-null, will be filled with monthly info objects (optional)
   * @return info for the final month: either the last month of retirement or the failure month
   */
  public static MonthlyInfo runPeriod(int iStart, double withdrawalRate, int years, int percentStock,
      List<MonthlyInfo> salaries)
  {
    final int iEnd = Math.min(iStart + 12 * years, length());
    return run(iStart, iEnd, withdrawalRate, percentStock, salaries);
  }

  /**
   * Simulate a retirement.
   * 
   * @param iStart index of retirement month (first withdrawal)
   * @param iEnd last index of simulation period (exclusive)
   * @param withdrawalRate annual withdrawal rate as a percent (3.5 = 3.5%) *
   * @param percentStock percent stock (vs. bonds) held in brokerage account
   * @param salaries if non-null, will be filled with monthly info objects (optional)
   * @return info for the final month: either the last month of retirement or the failure month
   */
  public static MonthlyInfo run(int iStart, int iEnd, double withdrawalRate, int percentStock,
      List<MonthlyInfo> salaries)
  {
    assert iStart >= 0 && iStart < length();
    assert iEnd > iStart && iEnd <= length();
    assert withdrawalRate > 0.0 : withdrawalRate;

    double balance = 1000000.0; // starting balance is mostly arbitrary since all results are relative
    double annualWithdrawal = balance * withdrawalRate / 100.0;
    double monthlyWithdrawal = annualWithdrawal / 12;

    MonthlyInfo info = null;
    for (int i = iStart; i < iEnd; ++i) {
      info = new MonthlyInfo(i, SwrLib.time(i), i - iStart + 1, monthlyWithdrawal, balance);
      if (salaries != null) salaries.add(info);
      if (info.failed()) return info;

      // Make withdrawal at the beginning of the month.
      balance -= monthlyWithdrawal;
      assert balance > -1e-5; // TODO avoid floating point issues

      // Remaining balance grows during the rest of month.
      balance *= growth(i, percentStock);
    }
    return info;
  }

  /**
   * Find the SWR for a given retirement date.
   * 
   * @param index index of retirement date
   * @param years number of years of retirement
   * @param percentStock percent stock (vs bonds) in asset allocation (70 = 70%)
   * @param quantum withdrawalRate % quantum == 0
   * @return safe withdrawal rate for the given retirement index and parameters
   */
  public static int findSwrForYear(int index, int years, int percentStock, int quantum)
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
      MonthlyInfo info = SwrLib.runPeriod(index, swr / 100.0, years, percentStock, null);
      if (info.ok()) {
        lowSWR = swr;
      } else {
        highSWR = swr;
      }
    }
    return lowSWR;
  }

  /** @return true if the withdrawal rate works for all retirement starting times. */
  public static boolean isSafe(int withdrawalRate, int years, int percentStock)
  {
    final int lastIndex = SwrLib.lastIndex(years);
    final double floatWR = withdrawalRate / 100.0;
    for (int i = 0; i <= lastIndex; ++i) {
      MonthlyInfo info = SwrLib.runPeriod(i, floatWR, years, percentStock, null);
      if (info.failed()) return false;
      assert info.balance > 0 && info.salary > 0;
    }
    return true;
  }

  /** Verify that we're matching the "Real Total Return Price" from Shiller's spreadsheet. */
  private static Sequence calcSnpReturns(Sequence snp)
  {
    double finalCPI = snp.get(-1, Shiller.CPI);
    Sequence seq = new Sequence("Stock");
    double shares = 1.0; // track number of shares to calculate total dividend payment
    for (int i = 0; i < snp.size(); ++i) {
      final double cpi = snp.get(i, Shiller.CPI);
      final double inflation = finalCPI / cpi;
      final double price = snp.get(i, Shiller.PRICE);
      final double realPrice = price * inflation;
      final double dividend = shares * snp.get(i, Shiller.DIV);
      if (i > 0) { // why? Shiller does it
        shares += dividend / price;
      }
      final double balance = shares * price * inflation;
      System.out.printf("%d [%s] $%.2f -> $%.2f (cpi=%.2f) $%.2f %.3f shares\n", i,
          TimeLib.formatMonth(snp.getTimeMS(i)), price, realPrice, cpi, balance, shares);
      seq.addData(balance, snp.getTimeMS(i));
    }
    return seq;
  }

  public static Sequence adjustForInflation(Sequence seq, Sequence cpi)
  {
    assert seq.matches(cpi);
    final double finalCPI = cpi.getLast(0);
    Sequence seqAdjusted = new Sequence(seq.getName() + "(real)");
    for (int i = 0; i < seq.size(); ++i) {
      final double inflation = finalCPI / cpi.get(i, 0);
      seqAdjusted.addData(seq.get(i).mul(inflation));
    }
    assert seqAdjusted.matches(seq);
    return seqAdjusted;
  }

  public static File getDefaultBengenFile()
  {
    return new File(DataIO.getFinancePath(), "bengen-table.csv");
  }

  public static File getDefaultDmswrFile()
  {
    return new File(DataIO.getFinancePath(), "dmswr-stock75-lookback20.csv");
  }

  /** Load data and initialize / calculate static data sequences. */
  public static void setupWithDefaultFiles() throws IOException
  {
    setup(getDefaultBengenFile(), getDefaultDmswrFile());
  }

  /** Load data and initialize / calculate static data sequences. */
  public static void setup(File bengenFile, File dmswrFile) throws IOException
  {
    Shiller.downloadData();

    Sequence shillerData = Shiller.loadAll(Shiller.getPathCSV(), true);
    Sequence bondData = shillerData.extractDimAsSeq(Shiller.GS10).setName("GS10");

    cpi = shillerData.extractDimAsSeq(Shiller.CPI).setName("CPI");
    stock = shillerData.extractDimAsSeq(Shiller.RTRP).setName("Stock (real)");
    bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, 0, -1);
    // bonds = Bond.calcReturnsNaiveInterest(BondFactory.note10Year, bondData, 0, -1, DivOrPow.DivideBy12);
    bonds.setName("Bonds");
    bonds = adjustForInflation(bonds, cpi).setName("Bonds (real)");

    System.out.println(stock);
    assert bonds.matches(stock);
    assert cpi.matches(stock);

    cpi._div(cpi.getFirst(0));
    stock._div(stock.getFirst(0));
    bonds._div(bonds.getFirst(0));

    stockMul = stock.derivativeMul();
    bondsMul = bonds.derivativeMul();
    cpiMul = cpi.derivativeMul();
    assert bondsMul.matches(stockMul);
    assert cpiMul.matches(stockMul);

    mixedMap = new HashMap<>();
    for (int percentStock = 0; percentStock <= 100; percentStock += 5) {
      // Note that stock*alpha + bonds*(1-alpha) models an initial split *without* rebalancing. We want to include
      // rebalancing (monthly, for simplicity) so the cumulative returns must be calculated month-by-month.
      Sequence mixed = new Sequence(String.format("Mixed (%d / %d)", percentStock, 100 - percentStock));
      double x = 1.0;
      for (int i = 0;; ++i) {
        mixed.addData(x, stock.getTimeMS(i));
        if (i >= length()) break; // can't compute growth because there's no more data
        x *= growth(i, percentStock);
      }
      mixedMap.put(percentStock, mixed);
      assert mixed.matches(stock);
    }

    // Load pre-computed bengen results.
    if (bengenFile != null) {
      System.out.printf("Load Bengen Data: [%s]\n", bengenFile);
      BengenTable.loadTable(bengenFile);
    }

    // Load pre-computed DMSWR results.
    if (dmswrFile != null) {
      System.out.printf("Load DMSWR Data: [%s]\n", dmswrFile);
      MarwoodTable.loadTable(dmswrFile);
    }
  }

  /** Save an interactive chart with stock and bond data as a local HTML file. */
  public static void saveGraph() throws IOException
  {
    Chart.saveLineChart(new File(DataIO.getOutputPath(), "shiller.html"), "Shiller Data", "100%", "800px",
        ChartScaling.LOGARITHMIC, ChartTiming.MONTHLY, stock, bonds, mixedMap.get(70), cpi);
  }

  public static void main(String[] args) throws IOException
  {
    setupWithDefaultFiles();
    saveGraph();

    // Calculate and print the success rate for different WRs and stock/bond mixes.
    final int nYears = 30;
    for (int withdrawalRate = 300; withdrawalRate <= 400; withdrawalRate += 25) {
      for (int percentStock = 0; percentStock <= 100; percentStock += 25) {
        int nFail = 0;
        int nWin = 0;
        for (int iStart = 0; iStart <= lastIndex(nYears); ++iStart) {
          MonthlyInfo info = runPeriod(iStart, withdrawalRate / 100.0, nYears, percentStock, null);
          if (info.failed()) {
            ++nFail;
          } else {
            ++nWin;
          }
        }
        System.out.printf("%.2f%%  %3d%%| %6.2f%%   Failed: %3d / %d\n", withdrawalRate / 100.0, percentStock,
            100.0 * nWin / (nWin + nFail), nFail, nWin + nFail);
      }
    }
  }
}
