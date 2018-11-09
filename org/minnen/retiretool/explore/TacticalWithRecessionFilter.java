package org.minnen.retiretool.explore;

import java.io.File;
import java.io.IOException;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.minnen.retiretool.StandardPortfolios;
import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.YahooIO;
import org.minnen.retiretool.data.fred.FredSeries;
import org.minnen.retiretool.data.tiingo.TiingoFund;
import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.ConfigMonthlySMA;
import org.minnen.retiretool.predictor.config.ConfigMulti;
import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.MultiPredictor;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.predictor.daily.TimeCode;
import org.minnen.retiretool.stats.ComparisonStats;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.PriceModel;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.Writer;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.PlotBand;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

import smile.math.Math;

public class TacticalWithRecessionFilter
{
  public final static SequenceStore     store         = new SequenceStore();

  /** Dimension to use in the price sequence for SMA predictions. */
  public static int                     iPriceSMA     = 0;

  public static final Slippage          slippage      = Slippage.None;

  public static final int               maxDelay      = 0;
  public static final long              gap           = 2 * TimeLib.MS_IN_DAY;
  public static final PriceModel        priceModel    = PriceModel.closeModel;

  public static final String            riskyName     = "stock";
  public static final String            safeName      = "3-month-treasuries";
  public static final String[]          assetNames    = new String[] { riskyName, safeName };

  public static final PredictorConfig[] singleConfigs = new PredictorConfig[] {
      new ConfigSMA(20, 0, 240, 150, 0.25, FinLib.Close, gap), new ConfigSMA(25, 0, 155, 125, 0.75, FinLib.Close, gap),
      new ConfigSMA(5, 0, 165, 5, 0.5, FinLib.Close, gap) };

  public static void setupData() throws IOException
  {
    String symbol = "^GSPC";
    File file = YahooIO.downloadDailyData(symbol, 8 * TimeLib.MS_IN_HOUR);
    Sequence stock = YahooIO.loadData(file);

    // TiingoFund fund = TiingoFund.fromSymbol("VFINX", true);
    // Sequence stock = fund.data;

    System.out.printf("S&P (Daily): [%s] -> [%s]\n", TimeLib.formatDate(stock.getStartMS()),
        TimeLib.formatDate(stock.getEndMS()));
    store.add(stock, "stock");

    Sequence tb3mo = FinLib.inferAssetFrom3MonthTreasuries();
    store.add(tb3mo, "3-month-treasuries");

    Sequence unemploymentRate = FredSeries.fromName("unemployment rate").data;
    System.out.printf("Unemployment: [%s] -> [%s]\n", TimeLib.formatDate(unemploymentRate.getStartMS()),
        TimeLib.formatDate(unemploymentRate.getEndMS()));
    store.add(unemploymentRate, "unemployment rate");
  }

  public static void main(String[] args) throws IOException
  {
    setupData();
    Sequence stock = store.get(riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 365 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    Simulation sim = new Simulation(store, guideSeq, slippage, maxDelay, priceModel, priceModel);

    StandardPortfolios portfolios = new StandardPortfolios(sim);
    List<ComparisonStats> compStats = new ArrayList<>();
    List<Sequence> returns = new ArrayList<>();

    // Buy-and-Hold 100% stock.
    Predictor predRisky = portfolios.passive("Buy & Hold", riskyName);
    sim.run(predRisky, "Buy & Hold");
    Sequence baselineReturns = sim.returnsDaily;
    Sequence defenders = sim.returnsMonthly;
    returns.add(sim.returnsMonthly);
    System.out.println(CumulativeStats.calc(sim.returnsMonthly));

    // Only unemployment signal for in/out.
    int nMonthsUnrateSMA = 10;
    ConfigMonthlySMA configUnemployment = new ConfigMonthlySMA(nMonthsUnrateSMA, true, "unemployment rate", 0);
    Predictor predUnemployment = configUnemployment.build(sim.broker.accessObject, riskyName, safeName);
    sim.run(predUnemployment, "Unemployment");
    assert sim.returnsDaily.matches(baselineReturns);
    returns.add(sim.returnsMonthly);
    System.out.println(CumulativeStats.calc(sim.returnsMonthly));
    compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, defenders));
    // Sequence seq = sim.returnsMonthly.dup();
    // seq.setName("Unemployment Indicator");
    // Sequence timeCodes = TimeCode.asSequence(predUnemployment.timeCodes);
    // for (FeatureVec x : seq) {
    // int i = timeCodes.getIndexAtOrBefore(x.getTime());
    // double v = timeCodes.get(i, 0);
    // x.set(0, 0.5 + v / 2);
    // }
    // returns.add(seq);
    List<PlotBand> bands = new ArrayList<>();
    for (int i = 1; i < predUnemployment.timeCodes.size(); ++i) {
      TimeCode a = predUnemployment.timeCodes.get(i - 1);
      if (a.code == 0) {
        TimeCode b = predUnemployment.timeCodes.get(i);
        assert b.code == 1;
        int from = sim.returnsMonthly.getClosestIndex(a.time);
        int to = sim.returnsMonthly.getClosestIndex(b.time);
        bands.add(new PlotBand(from, to, "rgba(128,20,10,0.05)"));
      }
    }

    Sequence unrate = store.get(configUnemployment.analysisName);
    Sequence unrateSMA = unrate.calcSMA(nMonthsUnrateSMA);
    Chart.saveLineChart(new File(DataIO.outputPath, "unrate.html"), "Unemployment Rate", 1000, 640, ChartScaling.LINEAR,
        ChartTiming.MONTHLY, unrate, unrateSMA);

    // Tactical multi-predictor.
    Set<Integer> contrary = new HashSet<Integer>();
    contrary.add(0);
    PredictorConfig config = new ConfigMulti(true, contrary, singleConfigs);
    Predictor predictor = config.build(sim.broker.accessObject, assetNames);
    sim.run(predictor, "Tactical");
    assert sim.returnsDaily.matches(baselineReturns);
    returns.add(sim.returnsMonthly);
    System.out.println(CumulativeStats.calc(sim.returnsMonthly));
    compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, defenders));

    // Simple momentum.
    int nMonthsSMA = 10;
    int n = nMonthsSMA * 20;
    PredictorConfig configSMA = new ConfigSMA(5, 0, n, n - 5, 0.1, FinLib.AdjClose, 2 * TimeLib.MS_IN_DAY);
    predictor = configSMA.build(sim.broker.accessObject, assetNames);
    sim.run(predictor, String.format("SMA:%d", nMonthsSMA));
    assert sim.returnsDaily.matches(baselineReturns);
    returns.add(sim.returnsMonthly);
    System.out.println(CumulativeStats.calc(sim.returnsMonthly));

    // Tactical + unemployment as a recession signal.
    // PredictorConfig configStrategy = new ConfigMulti(x, configSMA, configUnemployment);
    PredictorConfig configStrategy = new ConfigMulti(true, contrary, singleConfigs[0], singleConfigs[1],
        singleConfigs[2], configUnemployment);
    predictor = configStrategy.build(sim.broker.accessObject, assetNames);
    sim.run(predictor, "Tactical+Unemployment");
    assert sim.returnsDaily.matches(baselineReturns);
    returns.add(sim.returnsMonthly);
    System.out.println(CumulativeStats.calc(sim.returnsMonthly));
    compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, defenders));

    ChartConfig chartConfig = Chart.saveLineChart(new File(DataIO.outputPath, "returns.html"), null, 1000, 600,
        ChartScaling.LOGARITHMIC, ChartTiming.MONTHLY, returns);
    chartConfig.addPlotBandX(bands);
    Chart.saveChart(chartConfig);
    Chart.saveComparisonTable(new File(DataIO.outputPath, "comparison.html"), 1000, compStats);

    // Report: comparison of returns over next N months.
    for (int nMonths : new int[] { 12 * 5, 12 * 10 }) {
      List<Sequence> durationalReturns = new ArrayList<>();
      for (Sequence r : returns) {
        Sequence seq = FinLib.calcReturnsForMonths(r, nMonths);
        seq.setName(r.getName());
        durationalReturns.add(seq);
      }
      String title = String.format("Returns (%s, %d\u00A2 Spread)", TimeLib.formatDurationMonths(nMonths),
          Math.round(slippage.constSlip * 200));
      File file = new File(DataIO.outputPath, String.format("duration-returns-%d-months.html", nMonths));
      Chart.saveLineChart(file, title, 1000, 640, ChartScaling.LINEAR, ChartTiming.MONTHLY, durationalReturns);
    }
  }
}
