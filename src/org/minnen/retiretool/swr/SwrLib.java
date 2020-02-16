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
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.FinLib.DividendMethod;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

public class SwrLib
{
  /** Stock, bonds, 70/30 mixed values aling with CPI (inflation data) normalized so that the first value is 1.0. */
  public static Sequence                stock, bonds, cpi, stockReal, bondsReal;

  /** Each "Mul" sequence holds the multiplier representing growth for each month (1.01 = 1% growth). */
  private static Sequence               stockMul, bondsMul, cpiMul;

  /** Mixed stock/bond cumulative returns keyed by stock percent (70 = 70% stocks / 30% bonds). */
  private static Map<Integer, Sequence> mixedMap, mixedMapReal;

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

  /** @return inflation over [from..to] as a multiplier (1.01 = 1%). */
  public static double inflation(int from, int to)
  {
    return cpi.get(to, 0) / cpi.get(from, 0);
  }

  /** @return inflation multiplier for the i'th month (1.01 = 1%). */
  public static double inflation(int i)
  {
    return cpiMul.get(i, 0);
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

      // Adjust monthly salary for inflation.
      monthlyWithdrawal *= cpiMul.get(i, 0);
    }
    return info;
  }

  /** Load data and initialize / calculate static data sequences. */
  public static void setup() throws IOException
  {
    Shiller.downloadData();

    Sequence shillerData = Shiller.loadAll(Shiller.getPathCSV());
    Sequence bondData = shillerData.extractDims(new int[] { Shiller.GS10 }).setName("GS10");

    cpi = shillerData.extractDims(new int[] { Shiller.CPI }).setName("CPI");
    cpi._div(cpi.getFirst(0));
    stock = FinLib.calcSnpReturns(shillerData, 0, -1, DividendMethod.QUARTERLY).setName("Stock");
    bonds = Bond.calcReturnsRebuy(BondFactory.note10Year, bondData, 0, -1).setName("Bonds");

    stockReal = stock.div(cpi).setName("Stock (real)");
    bondsReal = bonds.div(cpi).setName("Bonds (real)");

    stockMul = stock.derivativeMul();
    bondsMul = bonds.derivativeMul();
    cpiMul = cpi.derivativeMul();

    System.out.println(stockMul);
    assert bondsMul.matches(stockMul);
    assert cpiMul.matches(stockMul);

    mixedMap = new HashMap<>();
    mixedMapReal = new HashMap<>();
    for (int percentStock = 0; percentStock <= 100; percentStock += 5) {
      // Note that stock*alpha + bonds*(1-alpha) models an initial split *without* rebalancing. We want to include
      // rebalancing (monthly, for simplicity) so the cumulative returns must be calculated month-by-month.
      Sequence nominal = new Sequence(String.format("Mixed (%d / %d)", percentStock, 100 - percentStock));
      double x = 1.0;
      for (int i = 0;; ++i) {
        nominal.addData(x, stock.getTimeMS(i));
        if (i >= length()) break; // can't compute growth because there's no more data
        x *= growth(i, percentStock);
      }
      Sequence real = nominal.div(cpi)
          .setName(String.format("Mixed (%d / %d, real)", percentStock, 100 - percentStock));
      mixedMap.put(percentStock, nominal);
      mixedMapReal.put(percentStock, real);

      assert nominal.matches(stock);
      assert real.matches(stock);
    }
  }

  /** Save an interactive chart with stock and bond data as a local HTML file. */
  public static void saveGraph() throws IOException
  {
    Chart.saveLineChart(new File(DataIO.getOutputPath(), "shiller.html"), "Shiller Data", "100%", "800px",
        ChartScaling.LOGARITHMIC, ChartTiming.MONTHLY, stock, stockReal, bonds, bondsReal, mixedMap.get(70),
        mixedMapReal.get(70), cpi);
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
