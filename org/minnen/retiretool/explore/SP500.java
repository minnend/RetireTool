package org.minnen.retiretool.explore;

import java.io.File;
import java.io.IOException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.StandardPortfolios;
import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.tiingo.Tiingo;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.stats.ComparisonStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

public class SP500
{
  public static final SequenceStore store      = new SequenceStore();

  public static final String[]      indexFunds = new String[] { "VFINX", "VBMFX", "VWIGX", "VFISX" };
  public static final String[]      allSymbols = new String[FinLib.SP500_FUNDS.length + indexFunds.length];

  static {
    // Combine all symbols into one list.
    System.arraycopy(FinLib.SP500_FUNDS, 0, allSymbols, 0, FinLib.SP500_FUNDS.length);
    System.arraycopy(indexFunds, 0, allSymbols, FinLib.SP500_FUNDS.length, indexFunds.length);
  }

  public static void main(String[] args) throws IOException
  {
    double startingBalance = 10000.0;
    double monthlyDeposit = 0.0;
    Simulation sim = Tiingo.setupSimulation(allSymbols, startingBalance, monthlyDeposit, Slippage.None, null, store);

    long commonStart = store.getCommonStartTime();
    long commonEnd = store.getCommonEndTime();
    long timeSimStart = commonStart;
    // timeSimStart = TimeLib.toMs(2013, Month.JANUARY, 1);
    long timeSimEnd = commonEnd;
    // timeSimEnd = TimeLib.toMs(2012, Month.DECEMBER, 31);
    double nSimMonths = TimeLib.monthsBetween(timeSimStart, timeSimEnd);
    System.out.printf("Simulation: [%s] -> [%s] (%.1f months total)\n", TimeLib.formatDate(timeSimStart),
        TimeLib.formatDate(timeSimEnd), nSimMonths);
    assert timeSimStart < timeSimEnd;

    // Simulate different portfolios.
    List<Sequence> returns = new ArrayList<>();
    Predictor predictor;
    StandardPortfolios portfolios = new StandardPortfolios(sim);
    List<ComparisonStats> compStats = new ArrayList<>();

    // Baseline = S&P 500.
    predictor = portfolios.passive("S&P 500 Fund", "VFINX");
    Sequence returnsStock = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsStock);

    // S&P 500 individual symbols (equal-weight).
    predictor = portfolios.passiveEqualWeight("S&P 500 Equal Weight", FinLib.SP500_FUNDS);
    Sequence returnsSP500EW = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsSP500EW);
    compStats.add(ComparisonStats.calc(returnsSP500EW, 0.5, returnsStock));

    // Save reports: graph of returns + comparison summary.
    Chart.saveLineChart(new File(DataIO.outputPath, "returns.html"), "Total Returns", 1000, 640,
        ChartScaling.LOGARITHMIC, ChartTiming.MONTHLY, returns);
    Chart.saveComparisonTable(new File(DataIO.outputPath, "comparison.html"), 1000, compStats);

    // Report: comparison of returns over next N months.
    for (int nMonths : new int[] { 12 * 5, 12 * 10 }) {
      List<Sequence> durationalReturns = new ArrayList<>();
      for (Sequence r : returns) {
        Sequence seq = FinLib.calcReturnsForMonths(r, nMonths);
        seq.setName(r.getName());
        durationalReturns.add(seq);
      }
      String title = String.format("Total Returns (Next %s)", TimeLib.formatDurationMonths(nMonths));
      File file = new File(DataIO.outputPath, String.format("duration-returns-%d-months.html", nMonths));
      Chart.saveLineChart(file, title, 1000, 640, ChartScaling.LINEAR, ChartTiming.MONTHLY, durationalReturns);
    }

    // TiingoFund fund = TiingoFund.fromSymbol("T", true);
    // for (FeatureVec x : fund.data) {
    // double div = x.get(FinLib.DivCash);
    // double split = x.get(FinLib.SplitFactor);
    // assert div >= 0.0;
    // if (split != 1.0) {
    // System.out.printf("%s: %g (split)\n", TimeLib.formatDate2(x.getTime()), split);
    // }
    // if (div > 0.0) {
    // System.out.printf("%s: %g (div)\n", TimeLib.formatDate2(x.getTime()), div);
    // }
    // }
  }
}
