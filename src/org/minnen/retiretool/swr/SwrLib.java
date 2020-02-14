package org.minnen.retiretool.swr;

import java.io.File;
import java.io.IOException;
import java.util.List;

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
  public static Sequence  stock, bonds, cpi, stockReal, bondsReal, mixed, mixedReal;
  private static Sequence stockMul, bondsMul, cpiMul;

  public static long time(int i)
  {
    return stockMul.getTimeMS(i);
  }

  /** @return copy of internal sequence that holds monthly CPI multipliers. */
  public static Sequence getCPI()
  {
    return cpiMul.dup();
  }

  public static int lastIndex(int years)
  {
    return stockMul.length() - years * 12;
  }

  /** @return inflation over [from..to] as a multiplier. */
  public static double inflation(int from, int to)
  {
    return cpi.get(to, 0) / cpi.get(from, 0);
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

  public static MonthlyInfo runPeriod(int iStart, double withdrawalRate, int years, int percentStock,
      List<MonthlyInfo> salaries)
  {
    double balance = 1000.0;
    double annualWithdrawal = balance * withdrawalRate / 100.0;
    double monthlyWithdrawal = annualWithdrawal / 12;
    // System.out.printf("annual: $%.2f monthly: $%.2f\n", annualWithdrawal, monthlyWithdrawal);

    MonthlyInfo info = null;
    final int iEnd = iStart + 12 * years;
    for (int i = iStart; i < iEnd; ++i) {
      info = new MonthlyInfo(i, monthlyWithdrawal, balance);
      if (salaries != null) salaries.add(info);
      balance -= monthlyWithdrawal;
      if (balance <= 0.0) return info;
      balance *= growth(i, percentStock);
      monthlyWithdrawal *= cpiMul.get(i, 0);
      // System.out.printf("%d [%s] %f $%.2f %f $%.2f\n", i, TimeLib.formatYM(stock.getTimeMS(i)), r, balance,
      // cpi.get(i, 0), monthlyWithdrawal);
    }

    return info;
  }

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

    mixed = stock.dup()._mul(0.7).add(bonds.dup()._mul(0.3)).setName("70/30");
    mixedReal = mixed.div(cpi).setName("70/30 (real)");

    stockMul = stock.derivativeMul();
    bondsMul = bonds.derivativeMul();
    cpiMul = cpi.derivativeMul();

    System.out.println(stockMul);
    assert bondsMul.matches(stockMul);
    assert cpiMul.matches(stockMul);

    // Chart.saveLineChart(new File(DataIO.getOutputPath(), "shiller.html"), "Shiller Data", "100%", "800px",
    // ChartScaling.LOGARITHMIC, ChartTiming.MONTHLY, stock, stockReal, bonds, bondsReal, mixed, mixedReal, cpi);
  }

  public static void main(String[] args) throws IOException
  {
    setup();

    final int nYears = 30;
    for (int withdrawalRate = 300; withdrawalRate <= 400; withdrawalRate += 25) {
      for (int percentStock = 0; percentStock <= 100; percentStock += 25) {

        int nFail = 0;
        int nWin = 0;
        for (int iStart = 0; iStart <= lastIndex(nYears); ++iStart) {
          MonthlyInfo info = runPeriod(iStart, withdrawalRate / 100.0, nYears, percentStock, null);
          if (info.balance > 0) {
            ++nWin;
          } else {
            ++nFail;
          }
        }
        System.out.printf("%.2f%%  %3d%%| %6.2f%%  (%d / %d)\n", withdrawalRate / 100.0, percentStock,
            100.0 * nWin / (nWin + nFail), nFail, nWin + nFail);
      }
    }
  }
}
