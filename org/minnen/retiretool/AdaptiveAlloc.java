package org.minnen.retiretool;

import java.io.File;
import java.io.IOException;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.minnen.retiretool.broker.Account;
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
import org.minnen.retiretool.predictor.config.ConfigMulti;
import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

public class AdaptiveAlloc
{
  public static final SequenceStore store        = new SequenceStore();

  public static final Slippage      slippage     = new Slippage(0.02, 0.0);

  // These symbols go back to 1 April 1996.
  public static final String[]      fundSymbols  = new String[] { "SPY", "VTSMX", "VBMFX", "VGSIX", "VGTSX", "EWU",
      "EWG", "EWJ", "VGENX", "WHOSX", "FAGIX", "BUFHX", "VFICX", "FNMIX", "DFGBX", "SGGDX", "VGPMX", "USAGX", "FSPCX",
      "FSRBX", "FPBFX", "ETGIX"                 };

  // These symbols go back to 28 Jan 1993.
  // public static final String[] fundSymbols = new String[] { "VTSMX", "VBMFX", "VGENX", "WHOSX", "FAGIX", "DFGBX",
  // "VGPMX", "USAGX", "FSPCX", "FSRBX", "FPBFX" };

  // QQQ, XLK, AAPL, MSFT
  public static final String[]      assetSymbols = new String[fundSymbols.length + 1];

  static {
    System.arraycopy(fundSymbols, 0, assetSymbols, 0, fundSymbols.length);
    assetSymbols[assetSymbols.length - 1] = "cash";

    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
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
    // long commonEnd = TimeLib.calcCommonEnd(seqs);
    long commonEnd = TimeLib.toMs(2012, Month.DECEMBER, 31);
    System.out.printf("Common[%d]: [%s] -> [%s]\n", seqs.size(), TimeLib.formatDate(commonStart),
        TimeLib.formatDate(commonEnd));

    long simStartMs = TimeLib.toMs(TimeLib.ms2date(commonStart).plusMonths(12)
        .with(TemporalAdjusters.firstDayOfMonth()));
    double nSimMonths = TimeLib.monthsBetween(simStartMs, commonEnd);
    System.out.printf("Simulation Start: [%s] (%.1f months)\n", TimeLib.formatDate(simStartMs), nSimMonths);

    for (int i = 0; i < seqs.size(); ++i) {
      Sequence seq = seqs.get(i);
      // TODO need to incorporate dividend payments explicitly.
      // Currently extracting adjusted close that implicitly incorporates dividends.
      seq = seq.extractDims(FinLib.AdjClose);
      seq = seq.subseq(commonStart, commonEnd, EndpointBehavior.Closest);
      seqs.set(i, seq);
      store.add(seq);

      // double tr = FinLib.getTotalReturn(seq, seq.getClosestIndex(simStartMs), -1, 0);
      // double ar = FinLib.getAnnualReturn(tr, nSimMonths);
      // System.out.printf("%s: %5.2f%%  (%.2fx)\n", seq.getName(), ar, tr);
    }

    // Setup simulation.
    Sequence guideSeq = store.get(fundSymbols[0]);
    guideSeq = guideSeq.subseq(simStartMs, guideSeq.getEndMS(), EndpointBehavior.Closest);
    Simulation sim = new Simulation(store, guideSeq, slippage, 0, true);

    // Run simulation for buy-and-hold of individual assets.
    PredictorConfig[] constConfigs = new PredictorConfig[fundSymbols.length];
    for (int i = 0; i < constConfigs.length; ++i) {
      constConfigs[i] = new ConfigConst(i);
      // Predictor predictor = constConfigs[i].build(sim.broker.accessObject, assetSymbols);
      // Sequence returns = sim.run(predictor, fundSymbols[i]);
      // System.out.println(CumulativeStats.calc(returns));
    }

    PredictorConfig config;
    Predictor predictor;
    List<Sequence> returns = new ArrayList<>();

    // Lazy 2-fund portfolio.
    String[] lazy2 = new String[] { "VTSMX", "VBMFX" };
    config = new ConfigMixed(new DiscreteDistribution(lazy2, new double[] { 0.7, 0.3 }), new PredictorConfig[] {
        constConfigs[0], constConfigs[1] });
    predictor = config.build(sim.broker.accessObject, lazy2);
    Sequence returnsLazy2 = sim.run(predictor, "Lazy2");
    System.out.println(CumulativeStats.calc(returnsLazy2));

    // Lazy 3-fund portfolio.
    String[] lazy3 = new String[] { "VTSMX", "VBMFX", "VGTSX" };
    config = new ConfigMixed(new DiscreteDistribution(lazy3, new double[] { 0.34, 0.33, 0.33 }), new PredictorConfig[] {
        constConfigs[0], constConfigs[1], constConfigs[2] });
    predictor = config.build(sim.broker.accessObject, lazy3);
    Sequence returnsLazy3 = sim.run(predictor, "Lazy3");
    System.out.println(CumulativeStats.calc(returnsLazy3));

    // Lazy 4-fund portfolio.
    // String[] lazy4 = new String[] { "VTSMX", "VBMFX", "VGSIX", "VGTSX", "cash" };
    // config = new ConfigMixed(new DiscreteDistribution(lazy4, new double[] { 0.4, 0.2, 0.1, 0.3, 0.0 }),
    // new PredictorConfig[] { constConfigs[0], constConfigs[1], constConfigs[2], constConfigs[3], constConfigs[4] });
    // predictor = config.build(sim.broker.accessObject, lazy4);
    // Sequence returnsLazy4 = sim.run(predictor, "Lazy4");
    // System.out.println(CumulativeStats.calc(returnsLazy4));

    // All stock.
    predictor = constConfigs[0].build(sim.broker.accessObject, "VTSMX");
    Sequence returnsStock = sim.run(predictor, "Stock");
    System.out.println(CumulativeStats.calc(returnsStock));

    // All bonds.
    predictor = constConfigs[0].build(sim.broker.accessObject, "VBMFX");
    Sequence returnsBonds = sim.run(predictor, "Bonds");
    System.out.println(CumulativeStats.calc(returnsBonds));

    // Tactical: Stocks vs. Bonds.
    final long assetMap = 254;
    final long gap = 2 * TimeLib.MS_IN_DAY;
    final int iPrice = 0;
    PredictorConfig[] tacticalConfigs = new PredictorConfig[] { new ConfigSMA(20, 0, 240, 150, 0.25, iPrice, gap),
        new ConfigSMA(50, 0, 180, 30, 1.0, iPrice, gap), new ConfigSMA(10, 0, 220, 0, 2.0, iPrice, gap), };
    PredictorConfig tacticalConfig = new ConfigMulti(assetMap, 1, 2, tacticalConfigs);
    predictor = tacticalConfig.build(sim.broker.accessObject, fundSymbols);
    Sequence returnsTactical = sim.run(predictor, "Tactical");
    System.out.println(CumulativeStats.calc(returnsTactical));

    // Run simulation for fixed mix of assets.
    // config = new ConfigMixed(DiscreteDistribution.uniform(fundSymbols), constConfigs);
    // predictor = config.build(sim.broker.accessObject, assetSymbols);
    // Sequence returnsMix = sim.run(predictor, "Equal Mix");
    // System.out.println(CumulativeStats.calc(returnsMix));

    // Run adaptive asset allocation.
    TradeFreq tradeFreq = TradeFreq.Weekly;
    PredictorConfig minvarConfig = new ConfigAdaptive(15, 0.9, Weighting.MinVar, 20, 100, 80, 0.7, -1, tradeFreq, 0);
    PredictorConfig equalWeightConfig = new ConfigAdaptive(-1, -1, Weighting.Equal, 30, 100, 80, 0.5, 4, tradeFreq, 0);

    for (int i = 0; i <= 100; i += 101) {
      double alpha = i / 100.0;
      double[] weights = new double[] { alpha, 1.0 - alpha };
      config = new ConfigMixed(new DiscreteDistribution(weights), minvarConfig, equalWeightConfig);
      assert config.isValid();
      predictor = config.build(sim.broker.accessObject, assetSymbols);
      String name;
      if (i == 0) {
        name = "AAA.EW";// qualWeight";
      } else if (i == 100) {
        name = "AAA.MinVar";
      } else {
        name = String.format("AAA.%d/%d", i, 100 - i);
      }
      Sequence ret = sim.run(predictor, name);
      returns.add(ret);
      System.out.println(CumulativeStats.calc(ret));
    }

    // Combination of EqualWeight and Tactical.
    config = new ConfigMixed(new DiscreteDistribution(0.0, 1.0), equalWeightConfig, tacticalConfig);
    assert config.isValid();
    predictor = config.build(sim.broker.accessObject, assetSymbols);
    Sequence returnsCombo = sim.run(predictor, "Combo");
    System.out.println(CumulativeStats.calc(returnsCombo));

    returns.add(returnsBonds);
    returns.add(returnsStock);
    returns.add(returnsLazy2);
    returns.add(returnsLazy3);
    // returns.add(returnsLazy4);
    returns.add(returnsTactical);
    returns.add(returnsCombo);
    Chart.saveLineChart(new File(outputDir, "returns.html"),
        String.format("Returns (%d\u00A2 Spread)", Math.round(slippage.constSlip * 200)), 1000, 640, true, returns);
    Chart.saveAnnualStatsTable(new File(outputDir, "annual-stats.html"), 1000, false, returns);

    // Account account = sim.broker.getAccount(0);
    // account.printTransactions();//TimeLib.TIME_BEGIN, TimeLib.toMs(1996, Month.DECEMBER, 31));
  }
}
