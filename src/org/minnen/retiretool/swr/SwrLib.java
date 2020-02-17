package org.minnen.retiretool.swr;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.minnen.retiretool.Bond;
import org.minnen.retiretool.BondFactory;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.Shiller;
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

  /** @return total growth for a stock/bond portfolio over [fom..to]. */
  public static double growth(int from, int to, int percentStock)
  {
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
    assert iStart >= 0 && iStart < length();
    assert withdrawalRate > 0.0 && withdrawalRate <= 100.0 : withdrawalRate;

    double balance = 1000.0; // starting balance is mostly arbitrary since all results are relative
    double annualWithdrawal = balance * withdrawalRate / 100.0;
    double monthlyWithdrawal = annualWithdrawal / 12;

    MonthlyInfo info = null;
    final int iEnd = Math.min(iStart + 12 * years, length());
    for (int i = iStart; i < iEnd; ++i) {
      info = new MonthlyInfo(i, SwrLib.time(i), i - iStart + 1, monthlyWithdrawal, balance);
      if (salaries != null) salaries.add(info);
      if (info.failed()) return info;

      // Make withdrawal at the beginning of the month.
      balance -= monthlyWithdrawal;
      assert balance >= 0; // TODO avoid floating point issues

      // Remaining balance grows during the rest of month.
      balance *= growth(i, percentStock);
    }
    return info;
  }

  public static boolean isSafe(double withdrawalRate, int years, int percentStock)
  {
    final int lastIndex = SwrLib.lastIndex(years);
    for (int i = 0; i <= lastIndex; ++i) {
      MonthlyInfo info = SwrLib.runPeriod(i, withdrawalRate, years, percentStock, null);
      if (info.failed()) return false;
      assert info.balance > 0 && info.salary > 0;
    }
    return true;
  }

  /** Verify that we're matching the "Real Total Return Price" from Shiller's spreadsheet. */
  private static Sequence calcSnpReturns(Sequence snp)
  {
    double finalCPI = snp.get(-1, Shiller.CPI);;
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

  /** Load data and initialize / calculate static data sequences. */
  public static void setup() throws IOException
  {
    Shiller.downloadData();

    Sequence shillerData = Shiller.loadAll(Shiller.getPathCSV(), true);
    Sequence bondData = shillerData.extractDimAsSeq(Shiller.GS10).setName("GS10");

    cpi = shillerData.extractDimAsSeq(Shiller.CPI).setName("CPI");
    stock = shillerData.extractDimAsSeq(Shiller.RTRP).setName("Stock (real)");
    bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, 0, -1).setName("Bonds");
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
  }

  /** Save an interactive chart with stock and bond data as a local HTML file. */
  public static void saveGraph() throws IOException
  {
    Chart.saveLineChart(new File(DataIO.getOutputPath(), "shiller.html"), "Shiller Data", "100%", "800px",
        ChartScaling.LOGARITHMIC, ChartTiming.MONTHLY, stock, bonds, mixedMap.get(70), cpi);
  }

  public static void main(String[] args) throws IOException
  {
    setup();
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
