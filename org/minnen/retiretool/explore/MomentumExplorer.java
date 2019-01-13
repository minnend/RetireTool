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
import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.predictor.config.ConfigTactical;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.stats.ComparisonStats;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

import smile.math.Math;

public class MomentumExplorer
{
  public static final SequenceStore store        = new SequenceStore();
  public static final Slippage      slippage     = new Slippage(0.03, 0.0);

  public static final String[]      fundSymbols  = new String[] { "VFINX", "VBMFX", "VWIGX", "VWINX", "NAESX", "VGENX",
      "FRESX", "VEXMX", "AAPL" };

  public static final String[]      assetSymbols = new String[fundSymbols.length + 2];

  static {
    // Add "cash" as the last asset since it's not a fund in fundSymbols.
    System.arraycopy(fundSymbols, 0, assetSymbols, 0, fundSymbols.length);
    assetSymbols[assetSymbols.length - 2] = "cash";
    assetSymbols[assetSymbols.length - 1] = "3-Month Treasuries";
  }

  public static void main(String[] args) throws IOException
  {
    Sequence tb3mo = FinLib.inferAssetFrom3MonthTreasuries();

    double startingBalance = 10000.0;
    double monthlyDeposit = 0.0;
    Simulation sim = Tiingo.setupSimulation(fundSymbols, startingBalance, monthlyDeposit, slippage, null, store, tb3mo);

    long commonStart = store.getCommonStartTime();
    long commonEnd = store.getCommonEndTime();
    long timeSimStart = TimeLib
        .toMs(TimeLib.ms2date(commonStart).plusWeeks(53 + 4).with(TemporalAdjusters.firstDayOfMonth()));
    long timeSimEnd = commonEnd;
    double nSimMonths = TimeLib.monthsBetween(timeSimStart, timeSimEnd);
    System.out.printf("Simulation: [%s] -> [%s] (%.1f months total)\n", TimeLib.formatDate(timeSimStart),
        TimeLib.formatDate(timeSimEnd), nSimMonths);

    // Simulate different portfolios.
    Predictor predictor;
    List<Sequence> returns = new ArrayList<>();

    String stockSymbol = "VFINX"; // "VTSMX";
    String stockExtendedSymbol = "VEXMX";
    String bondSymbol = "VBMFX";
    String intlSymbol = "VWIGX"; // "VGTSX";
    String reitSymbol = "FRESX"; // VGSIX; // best = ~130
    String smallCapSymbol = "NAESX";
    String energySymbol = "VGENX";
    String cashSymbol = "3-Month Treasuries"; // "VFISX" // "cash" // "FSHBX"

    StandardPortfolios portfolios = new StandardPortfolios(sim);

    String symbol = "AAPL"; // stockSymbol;
    predictor = portfolios.passive(String.format("Buy & Hold [%s]", symbol), symbol);
    Sequence returnsBase = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsBase);

    List<ComparisonStats> compStats = new ArrayList<>();
    final int duration = 10 * 12;
    final int nPerturbed = 10;
    System.out.printf("Duration: %s\n", TimeLib.formatDurationMonths(duration));
    ComparisonStats best = null;
    for (int nMonths = 1; nMonths <= 24; ++nMonths) {
      int n = nMonths * 20; // ~20 days in a month
      PredictorConfig config = new ConfigSMA(5, 0, n, n - 5, 50, FinLib.AdjClose, 5 * TimeLib.MS_IN_DAY);
      predictor = config.build(sim.broker.accessObject, new String[] { symbol, cashSymbol });
      predictor.name = String.format("SMA:%d", n);
      Sequence returnsSMA = portfolios.run(predictor, timeSimStart, timeSimEnd, false);
      // System.out.printf("Base: %s\n", CumulativeStats.calc(returnsSMA));

      ComparisonStats worst = ComparisonStats.calc(returnsSMA, 0.5, returnsBase);;
      for (int i = 0; i < nPerturbed; ++i) {
        PredictorConfig perturbed = config.genPerturbed();

        predictor = perturbed.build(sim.broker.accessObject, new String[] { symbol, cashSymbol });
        predictor.name = String.format("SMA:%d", n);
        returnsSMA = portfolios.run(predictor, timeSimStart, timeSimEnd, false);
        ComparisonStats stats = ComparisonStats.calc(returnsSMA, 0.5, returnsBase);
        if (stats.durationToResults.get(duration).winPercent2 > worst.durationToResults.get(duration).winPercent2) {
          worst = stats;
        }
      }
      ComparisonStats.Results results = worst.durationToResults.get(duration);
      System.out.printf("%s  win=%.1f  lose=%.1f\n", CumulativeStats.calc(worst.returns1), results.winPercent1,
          results.winPercent2);
      compStats.add(worst);
      if (best == null
          || worst.durationToResults.get(duration).winPercent2 < best.durationToResults.get(duration).winPercent2) {
        best = worst;
      }
    }
    returns.add(best.returns1);

    // Save reports: graph of returns + comparison summary.
    String title = String.format("Returns (%d\u00A2 Spread)", Math.round(slippage.constSlip * 200));
    Chart.saveLineChart(new File(DataIO.getOutputPath(), "returns.html"), title, "100%", "640px", ChartScaling.LOGARITHMIC,
        ChartTiming.MONTHLY, returns);

    Chart.saveComparisonTable(new File(DataIO.getOutputPath(), "comparison.html"), "100%", compStats);

    // Report: comparison of returns over next N months.
    for (int nMonths : new int[] { 12 * 5, 12 * 10 }) {
      List<Sequence> durationalReturns = new ArrayList<>();
      for (Sequence r : returns) {
        Sequence seq = FinLib.calcReturnsForMonths(r, nMonths);
        seq.setName(r.getName());
        durationalReturns.add(seq);
      }
      title = String.format("Returns (%s, %d\u00A2 Spread)", TimeLib.formatDurationMonths(nMonths),
          Math.round(slippage.constSlip * 200));
      File file = new File(DataIO.getOutputPath(), String.format("duration-returns-%d-months.html", nMonths));
      Chart.saveLineChart(file, title, "100%", "640px", ChartScaling.LINEAR, ChartTiming.MONTHLY, durationalReturns);
    }
  }
}
