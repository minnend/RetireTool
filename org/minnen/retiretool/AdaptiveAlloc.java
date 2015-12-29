package org.minnen.retiretool;

import java.io.File;
import java.io.IOException;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.ConfigMixed;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.AdaptivePredictor;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

public class AdaptiveAlloc
{
  public static final SequenceStore store        = new SequenceStore();
  public static final String[]      fundSymbols  = new String[] { "SPY", "EWU", "EWG", "EWJ", "VGENX", "WHOSX",
      "FAGIX", "BUFHX", "VFICX", "FNMIX", "DFGBX", "SGGDX", "VGPMX", "USAGX", "FSPCX", "FSRBX", "FPBFX", "ETGIX" };
  // QQQ, XLK, AAPL, MSFT
  public static final String[]      assetSymbols = new String[fundSymbols.length + 1];

  static {
    System.arraycopy(fundSymbols, 0, assetSymbols, 0, fundSymbols.length);
    assetSymbols[assetSymbols.length - 1] = "cash";
  }

  public static void main(String[] args) throws IOException
  {
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
    for (Sequence seq : seqs) {
      System.out.printf("%s: [%s] -> [%s]\n", seq.getName(), TimeLib.formatDate(seq.getStartMS()),
          TimeLib.formatDate(seq.getEndMS()));
    }
    long commonStart = TimeLib.calcCommonStart(seqs);
    // long commonEnd = TimeLib.calcCommonEnd(seqs);
    long commonEnd = TimeLib.toMs(2012, Month.DECEMBER, 31); // TODO
    System.out.printf("Common: [%s] -> [%s]\n", TimeLib.formatDate(commonStart), TimeLib.formatDate(commonEnd));

    long simStartMs = TimeLib
        .toMs(TimeLib.ms2date(commonStart).plusMonths(7).with(TemporalAdjusters.firstDayOfMonth()));
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
    Sequence spy = store.get(fundSymbols[0]);
    Sequence guideSeq = spy.subseq(simStartMs, spy.getEndMS(), EndpointBehavior.Closest);
    Simulation sim = new Simulation(store, guideSeq);

    // Run simulation for buy-and-hold of individual assets.
    PredictorConfig[] constConfigs = new PredictorConfig[fundSymbols.length];
    for (int i = 0; i < constConfigs.length; ++i) {
      constConfigs[i] = new ConfigConst(i);
      Predictor predictor = constConfigs[i].build(sim.broker.accessObject, assetSymbols);
      Sequence returns = sim.run(predictor, fundSymbols[i]);
      System.out.println(CumulativeStats.calc(returns));
    }

    // Run simulation for fixed mix of assets.
    PredictorConfig config = new ConfigMixed(DiscreteDistribution.uniform(fundSymbols), constConfigs);
    Predictor predictor = config.build(sim.broker.accessObject, assetSymbols);
    Sequence returns = sim.run(predictor, "Mix");
    System.out.println(CumulativeStats.calc(returns));

    // Run adaptive asset allocation.
    predictor = new AdaptivePredictor(sim.broker.accessObject, assetSymbols);
    returns = sim.run(predictor, "AAA");
    System.out.println(CumulativeStats.calc(returns));
  }
}
