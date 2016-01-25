package org.minnen.retiretool;

import java.io.File;
import java.io.IOException;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.minnen.retiretool.broker.PriceModel;
import org.minnen.retiretool.broker.SimFactory;
import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.predictor.config.ConfigAdaptive;
import org.minnen.retiretool.predictor.config.ConfigAdaptive.TradeFreq;
import org.minnen.retiretool.predictor.config.ConfigAdaptive.Weighting;
import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.ConfigMixed;
import org.minnen.retiretool.predictor.config.ConfigTactical;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.AdaptivePredictor;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.predictor.daily.VolResPredictor;
import org.minnen.retiretool.predictor.optimize.AdaptiveScanner;
import org.minnen.retiretool.predictor.optimize.ConfigScanner;
import org.minnen.retiretool.predictor.optimize.Optimizer;
import org.minnen.retiretool.stats.ComparisonStats;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;

public class AdaptiveAlloc
{
  public static final SequenceStore store        = new SequenceStore();

  public static final Slippage      slippage     = new Slippage(0.03, 0.0);

  // These symbols go back to 13 May 1996.
  public static final String[]      fundSymbols  = new String[] { "SPY", "VTSMX", "VBMFX", "VGSIX", "VGTSX", "EWU",
      "EWG", "EWJ", "VGENX", "WHOSX", "FAGIX", "BUFHX", "VFICX", "FNMIX", "DFGBX", "SGGDX", "VGPMX", "USAGX", "FSPCX",
      "FSRBX", "FPBFX", "ETGIX", "VDIGX", "MDY", "VBINX" };

  // public static final String[] fundSymbols = new String[] { "VTSMX", "VBMFX", "VGSIX", "VGTSX", "VGENX", "WHOSX",
  // "USAGX" };

  // These symbols go back to 27 April 1992.
  // public static final String[] fundSymbols = new String[] { "VTSMX", "VBMFX", "VGENX", "WHOSX", "FAGIX", "DFGBX",
  // "VGPMX", "USAGX", "FSPCX", "FSRBX", "FPBFX" };

  // QQQ, XLK, AAPL, MSFT
  public static final String[]      assetSymbols = new String[fundSymbols.length + 1];

  static {
    // Add "cash" as the last asset since it's not a fund in fundSymbols.
    System.arraycopy(fundSymbols, 0, assetSymbols, 0, fundSymbols.length);
    assetSymbols[assetSymbols.length - 1] = "cash";

    // Silence debug spew from JOptimizer.
    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
  }

  public static Simulation walkForwadOptimization(long timeSimStart, SimFactory simFactory)
  {
    final int stepMonths = 3;
    // final int minOptMonths = 12 * 1;
    final int maxOptMonths = 12 * 5;

    // Simulator used for optimizing predictor parameters.
    Simulation simOpt = simFactory.build();
    AdaptiveScanner scanner = new AdaptiveScanner();

    // Simulator used for tracking walk-forward results.
    Simulation wfSim = simFactory.build();
    wfSim.setupRun(null, timeSimStart, TimeLib.TIME_END, "WalkForward");

    long testStart = timeSimStart;
    assert testStart >= wfSim.getStartMS();
    final long timeFirstAbleToPredict = TimeLib.toMs(TimeLib.toNextBusinessDay(TimeLib.ms2date(
        store.getCommonStartTime()).plusMonths(6))); // TODO pull requirements from config/predictor
    System.out.printf("First Able to Predict: [%s]\n", TimeLib.formatDate(timeFirstAbleToPredict));

    while (true) {
      // New test period is extends N months beyond test start time.
      final long testEnd = TimeLib.toPreviousBusinessDay(TimeLib
          .toMs(TimeLib.ms2date(testStart).plusMonths(stepMonths)));
      if (testEnd > wfSim.getEndMS()) break;

      // Optimization period extends backward from test start time.
      final long optEnd = TimeLib.toPreviousBusinessDay(testStart);
      final long optStart = Math.max(timeFirstAbleToPredict,
          TimeLib.toMs(TimeLib.ms2date(optEnd).minusMonths(maxOptMonths).with(TemporalAdjusters.firstDayOfMonth())));
      assert optStart < optEnd;

      // System.out.printf("Optm: [%s] -> [%s]\n", TimeLib.formatDate(optStart), TimeLib.formatDate(optEnd));
      // System.out.printf("Test: [%s] -> [%s]\n", TimeLib.formatDate(testStart), TimeLib.formatDate(testEnd));

      // Find best predictor parameters.
      scanner.reset();
      PredictorConfig config = Optimizer.grid(scanner, simOpt, optStart, optEnd, assetSymbols);
      Predictor predictor = config.build(wfSim.broker.accessObject, assetSymbols);

      // Run the predictor over the test period.
      wfSim.setPredictor(predictor);
      wfSim.runTo(testEnd);

      // Report results for this test period.
      CumulativeStats stats = CumulativeStats.calc(wfSim.returnsMonthly);
      System.out.printf("Test: [%s] -> [%s]: %.3f, %.2f  %s\n", TimeLib.formatDate(testStart),
          TimeLib.formatDate(testEnd), stats.cagr, stats.drawdown, config);

      // Advance test start time by N months.
      testStart = TimeLib.toMs(TimeLib.ms2date(testStart).plusMonths(stepMonths)
          .with(TemporalAdjusters.firstDayOfMonth()));
    }
    wfSim.finishRun();
    return wfSim;
  }

  public static void main(String[] args) throws IOException
  {
    File outputDir = new File("g:/web");
    File dataDir = new File("g:/research/finance/");
    assert dataDir.isDirectory();

    File yahooDir = new File(dataDir, "yahoo/");
    if (!yahooDir.exists()) yahooDir.mkdirs();

    Sequence tbillData = DataIO.loadDateValueCSV(new File(dataDir, "treasury-bills-3-month.csv"));
    tbillData.setName("3-Month Treasury Bills");
    tbillData.adjustDatesToEndOfMonth();
    System.out.printf("TBills: [%s] -> [%s]\n", TimeLib.formatMonth(tbillData.getStartMS()),
        TimeLib.formatMonth(tbillData.getEndMS()));
    store.add(tbillData, "tbilldata");
    store.alias("interest-rates", "tbilldata");

    // Make sure we have the latest data.
    for (String symbol : fundSymbols) {
      File file = DataIO.getYahooFile(yahooDir, symbol);
      DataIO.updateDailyDataFromYahoo(file, symbol, 8 * TimeLib.MS_IN_HOUR);
    }

    // Load data and trim to same time period.
    List<Sequence> seqs = new ArrayList<>();
    for (String symbol : fundSymbols) {
      File file = DataIO.getYahooFile(yahooDir, symbol);
      Sequence seq = DataIO.loadYahooData(file);
      seqs.add(seq);
    }
    // for (Sequence seq : seqs) {
    // System.out.printf("%s: [%s] -> [%s]\n", seq.getName(), TimeLib.formatDate(seq.getStartMS()),
    // TimeLib.formatDate(seq.getEndMS()));
    // }
    long commonStart = TimeLib.calcCommonStart(seqs);
    // long commonStart = TimeLib.toMs(2006, Month.JANUARY, 1);
    // long commonEnd = TimeLib.calcCommonEnd(seqs);
    long commonEnd = TimeLib.toMs(2012, Month.DECEMBER, 31);
    store.setCommonTimes(commonStart, commonEnd);
    System.out.printf("Common[%d]: [%s] -> [%s]\n", seqs.size(), TimeLib.formatDate(commonStart),
        TimeLib.formatDate(commonEnd));

    long timeSimStart = TimeLib.toMs(TimeLib.ms2date(commonStart).plusMonths(12)
        .with(TemporalAdjusters.firstDayOfMonth()));
    double nSimMonths = TimeLib.monthsBetween(timeSimStart, commonEnd);
    System.out.printf("Simulation Start: [%s] (%.1f months)\n", TimeLib.formatDate(timeSimStart), nSimMonths);

    // Extract common subsequence from each data sequence and add to the sequence store.
    for (int i = 0; i < seqs.size(); ++i) {
      Sequence seq = seqs.get(i);
      seq = seq.subseq(commonStart, commonEnd, EndpointBehavior.Closest);
      seqs.set(i, seq);
      store.add(seq);
    }

    // Setup simulation.
    Sequence guideSeq = store.get(fundSymbols[0]).dup();
    SimFactory simFactory = new SimFactory(store, guideSeq, slippage, 0, PriceModel.adjCloseModel);
    Simulation sim = simFactory.build();

    // Run simulation for buy-and-hold of individual assets.
    // List<Sequence> symbolReturns = new ArrayList<>();
    // for (int i = 0; i < fundSymbols.length; ++i) {
    // PredictorConfig config = new ConfigConst(fundSymbols[i]);
    // Predictor predictor = config.build(sim.broker.accessObject, assetSymbols);
    // sim.run(predictor, TimeLib.TIME_BEGIN, fundSymbols[i]);
    // symbolReturns.add(sim.returnsMonthly);
    // System.out.println(CumulativeStats.calc(sim.returnsMonthly));
    // }
    // Chart.saveLineChart(new File(outputDir, "all.html"), "Individual Returns", 1000, 640, true, true, symbolReturns);

    PredictorConfig config;
    Predictor predictor;
    List<Sequence> returns = new ArrayList<>();

    // Lazy 2-fund portfolio.
    String[] lazy2 = new String[] { "VTSMX", "VBMFX" };
    config = new ConfigMixed(new DiscreteDistribution(lazy2, new double[] { 0.7, 0.3 }), new ConfigConst(lazy2[0]),
        new ConfigConst(lazy2[1]));
    predictor = config.build(sim.broker.accessObject, lazy2);
    Sequence returnsLazy2 = sim.run(predictor, timeSimStart, "Lazy2");
    System.out.println(CumulativeStats.calc(returnsLazy2));
    returns.add(returnsLazy2);
    Chart.saveHoldings(new File(outputDir, "holdings-lazy2.html"), sim.holdings);

    // Lazy 3-fund portfolio.
    // String[] lazy3 = new String[] { "VTSMX", "VBMFX", "VGTSX" };
    // config = new ConfigMixed(new DiscreteDistribution(lazy3, new double[] { 0.34, 0.33, 0.33 }), new ConfigConst(
    // lazy3[0]), new ConfigConst(lazy3[1]), new ConfigConst(lazy3[2]));
    // predictor = config.build(sim.broker.accessObject, lazy3);
    // Sequence returnsLazy3 = sim.run(predictor, timeSimStart, "Lazy3");
    // System.out.println(CumulativeStats.calc(returnsLazy3));
    // returns.add(returnsLazy3);

    // Lazy 4-fund portfolio.
    // String[] lazy4 = new String[] { "VTSMX", "VBMFX", "VGSIX", "VGTSX" };
    // config = new ConfigMixed(new DiscreteDistribution(lazy4, new double[] { 0.4, 0.2, 0.1, 0.3 }), new ConfigConst(
    // lazy4[0]), new ConfigConst(lazy4[1]), new ConfigConst(lazy4[2]), new ConfigConst(lazy4[3]));
    // predictor = config.build(sim.broker.accessObject, lazy4);
    // Sequence returnsLazy4 = sim.run(predictor, timeSimStart, "Lazy4");
    // System.out.println(CumulativeStats.calc(returnsLazy4));
    // returns.add(returnsLazy4);

    // All stock.
    PredictorConfig stockConfig = new ConfigConst("VTSMX");
    predictor = stockConfig.build(sim.broker.accessObject, assetSymbols);
    Sequence returnsStock = sim.run(predictor, timeSimStart, "Stock");
    System.out.println(CumulativeStats.calc(returnsStock));
    returns.add(returnsStock);

    // // All bonds.
    // PredictorConfig bondConfig = new ConfigConst("VBMFX");
    // predictor = bondConfig.build(sim.broker.accessObject, assetSymbols);
    // Sequence returnsBonds = sim.run(predictor, timeSimStart,"Bonds");
    // System.out.println(CumulativeStats.calc(returnsBonds));
    // returns.add(returnsBonds);

    // Volatility-Responsive Asset Allocation.
    // predictor = new VolResPredictor("VTSMX", "VBMFX", sim.broker.accessObject);
    // Sequence returnsVolRes = sim.run(predictor, timeSimStart, "VolRes");
    // System.out.println(CumulativeStats.calc(returnsVolRes));
    // returns.add(returnsVolRes);

    // PredictorConfig tacticalConfig = new ConfigTactical(0, "SPY", "VTSMX", "VGSIX", "VGTSX", "EWU", "EWG", "EWJ",
    // "VGENX", "WHOSX", "FAGIX", "BUFHX", "VFICX", "FNMIX", "DFGBX", "SGGDX", "VGPMX", "USAGX", "FSPCX", "FSRBX",
    // "FPBFX", "ETGIX", "VBMFX", "cash");

    // PredictorConfig tacticalConfig = new ConfigTactical(0, "VTSMX", "MDY", "VGSIX", "VGTSX", "VGENX", "WHOSX",
    // "VGPMX",
    // "USAGX", "VBMFX", "cash");

    // PredictorConfig tacticalConfig = new ConfigTactical(0, "VTSMX", "SPY", "VBMFX");

    // predictor = tacticalConfig.build(sim.broker.accessObject, assetSymbols);
    // Sequence returnsTactical = sim.run(predictor, timeSimStart,"Tactical");
    // System.out.println(CumulativeStats.calc(returnsTactical));

    // Run adaptive asset allocation.
    TradeFreq tradeFreq = TradeFreq.Weekly;
    int pctQuantum = 2;
    // PredictorConfig minvarConfig = new ConfigAdaptive(15, 0.9, Weighting.MinVar, 20, 100, 80, 0.7, -1, pctQuantum,
    // tradeFreq, 0);
    PredictorConfig equalWeightConfig = new ConfigAdaptive(-1, -1, Weighting.Equal, 40, 100, 80, 0.5, 4, pctQuantum,
        tradeFreq, 0);
    // PredictorConfig ewConfig2 = new ConfigAdaptive(-1, -1, Weighting.Equal, 30, 110, 60, 0.5, 5, pctQuantum,
    // tradeFreq, 0);

    // Adaptive Asset Allocation (Equal Weight).
    predictor = equalWeightConfig.build(sim.broker.accessObject, assetSymbols);
    sim.run(predictor, timeSimStart, "Adaptive1");
    System.out.println(CumulativeStats.calc(sim.returnsMonthly));
    returns.add(sim.returnsMonthly);
    Chart.saveHoldings(new File(outputDir, "holdings-adaptive.html"), sim.holdings);

    // for (int i = 0; i <= 100; i += 101) {
    // double alpha = i / 100.0;
    // double[] weights = new double[] { alpha, 1.0 - alpha };
    // config = new ConfigMixed(new DiscreteDistribution(weights), minvarConfig, equalWeightConfig);
    // assert config.isValid();
    // predictor = config.build(sim.broker.accessObject, assetSymbols);
    // String name;
    // if (i == 0) {
    // name = "EqualWeight";
    // } else if (i == 100) {
    // name = "MinVar";
    // } else {
    // name = String.format("AAA.%d/%d", i, 100 - i);
    // }
    // Sequence ret = sim.run(predictor, timeSimStart,name);
    // returns.add(ret);
    // System.out.println(CumulativeStats.calc(ret));
    // }

    // List<ComparisonStats> compStats = new ArrayList<>();
    // Sequence[] defenders = new Sequence[] { returnsStock, returnsBonds, returnsLazy2, returnsLazy3, returnsLazy4 };
    // for (Sequence ret : defenders) {
    // compStats.add(ComparisonStats.calc(ret, 0.5, defenders));
    // }

    // Combination of EqualWeight and Tactical.
    // for (int i = 0; i <= 100; i += 100) {
    // double alpha = i / 100.0;
    // double[] weights = new double[] { alpha, 1.0 - alpha };
    // config = new ConfigMixed(new DiscreteDistribution(weights), tacticalConfig, equalWeightConfig);
    // assert config.isValid();
    // predictor = config.build(sim.broker.accessObject, assetSymbols);
    // String name;
    // if (i == 0) {
    // name = "EqualWeight";
    // } else if (i == 100) {
    // name = "Tactical";
    // } else {
    // name = String.format("TEW.%d/%d", i, 100 - i);
    // }
    // Sequence ret = sim.run(predictor, timeSimStart,name);
    // returns.add(ret);
    // // compStats.add(ComparisonStats.calc(ret, 0.5, defenders));
    // System.out.println(CumulativeStats.calc(ret));
    // }

    // Simulation wfSim = walkForwadOptimization(timeSimStart, simFactory);
    // System.out.println(CumulativeStats.calc(wfSim.returnsMonthly));
    // returns.add(wfSim.returnsMonthly);

    // AdaptiveScanner scanner = new AdaptiveScanner();
    // List<CumulativeStats> cstats = new ArrayList<CumulativeStats>();
    // while (true) {
    // config = scanner.get();
    // if (config == null) break;
    // // System.out.println(config);
    // predictor = config.build(sim.broker.accessObject, assetSymbols);
    // Sequence ret = sim.run(predictor, timeSimStart,config.toString());
    // CumulativeStats stats = CumulativeStats.calc(ret);
    // cstats.add(stats);
    // System.out.printf("%6.2f %s\n", 100.0 * scanner.percent(), stats);
    // }
    // CumulativeStats.filter(cstats);
    // System.out.printf("Best Results (%d)\n", scanner.size());
    // for (CumulativeStats stats : cstats) {
    // System.out.println(stats);
    // }

    Chart.saveLineChart(new File(outputDir, "returns.html"),
        String.format("Returns (%d\u00A2 Spread)", Math.round(slippage.constSlip * 200)), 1000, 640, true, true,
        returns);

    // Chart.saveAnnualStatsTable(new File(outputDir, "annual-stats.html"), 1000, false, returns);
    // Chart.saveComparisonTable(new File(outputDir, "comparison.html"), 1000, compStats);

    // Account account = sim.broker.getAccount(0);
    // account.printTransactions();//TimeLib.TIME_BEGIN, TimeLib.toMs(1996, Month.DECEMBER, 31));
  }
}
