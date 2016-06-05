package org.minnen.retiretool.ckde;

import java.io.File;
import java.io.IOException;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.broker.SimFactory;
import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.ConfigMixed;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.PriceModel;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;

public class ExploreCKDE
{
  public static final SequenceStore store        = new SequenceStore();
  public static final Slippage      slippage     = new Slippage(0.03, 0.0);

  // These symbols go back to 13 May 1996.
  public static final String[]      fundSymbols  = new String[] { "SPY", "VTSMX", "VBMFX", "VGSIX", "VGTSX", "VFISX",
      "VFSTX", "VBISX", "EWU", "EWG", "EWJ", "VGENX", "WHOSX", "FAGIX", "BUFHX", "VFICX", "FNMIX", "DFGBX", "SGGDX",
      "VGPMX", "USAGX", "FSPCX", "FSRBX", "FPBFX", "ETGIX", "VDIGX", "MDY", "VBINX", "VWINX", "MCA", "^IXIC" };

  // public static final String[] fundSymbols = new String[] { "VTSMX", "VBMFX", "VGTSX", "VWINX",
  // "FPBFX", "USAGX" };

  public static final String[]      assetSymbols = new String[fundSymbols.length + 1];

  static {
    // Add "cash" as the last asset since it's not a fund in fundSymbols.
    System.arraycopy(fundSymbols, 0, assetSymbols, 0, fundSymbols.length);
    assetSymbols[assetSymbols.length - 1] = "cash";
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

    long commonStart = TimeLib.calcCommonStart(seqs);
    long commonEnd = TimeLib.calcCommonEnd(seqs);
    commonEnd = TimeLib.toMs(2012, Month.DECEMBER, 31); // TODO
    store.setCommonTimes(commonStart, commonEnd);
    System.out.printf("Common[%d]: [%s] -> [%s]\n", seqs.size(), TimeLib.formatDate(commonStart),
        TimeLib.formatDate(commonEnd));

    long timeSimStart = TimeLib.toMs(TimeLib.ms2date(commonStart).plusWeeks(53)
        .with(TemporalAdjusters.firstDayOfMonth()));
    // timeSimStart = TimeLib.toMs(2006, Month.JUNE, 1); // TODO
    long timeSimEnd = commonEnd;
    // timeSimEnd = TimeLib.toMs(1999, Month.DECEMBER, 31); // TODO
    double nSimMonths = TimeLib.monthsBetween(timeSimStart, timeSimEnd);
    System.out.printf("Simulation Start: [%s] (%.1f months total)\n", TimeLib.formatDate(timeSimStart), nSimMonths);

    // Extract common subsequence from each data sequence and add to the sequence store.
    for (int i = 0; i < seqs.size(); ++i) {
      Sequence seq = seqs.get(i);
      seq = seq.subseq(commonStart, commonEnd, EndpointBehavior.Closest);
      seqs.set(i, seq);
      store.add(seq);
    }

    // Setup simulation.
    Sequence guideSeq = store.get(fundSymbols[0]).dup();
    PriceModel valueModel = PriceModel.adjCloseModel;
    PriceModel quoteModel = new PriceModel(PriceModel.Type.Open, true);
    double startingBalance = 10000.0;
    double monthlyDeposit = 0.0; // TODO real question is what method works best with ongoing contributions.
    SimFactory simFactory = new SimFactory(store, guideSeq, slippage, 0, startingBalance, monthlyDeposit, valueModel,
        quoteModel);
    Simulation sim = simFactory.build();

    PredictorConfig config;
    List<Sequence> returns = new ArrayList<>();

    // Lazy 2-fund portfolio.
    String[] assetsLazy2 = new String[] { "VTSMX", "VBMFX" };
    config = new ConfigMixed(new DiscreteDistribution(assetsLazy2, new double[] { 0.8, 0.2 }), new ConfigConst(
        assetsLazy2[0]), new ConfigConst(assetsLazy2[1]));
    Predictor lazy2 = config.build(sim.broker.accessObject, assetsLazy2);
    Sequence returnsLazy2 = sim.run(lazy2, timeSimStart, timeSimEnd, "Lazy2");
    System.out.println(CumulativeStats.calc(returnsLazy2));
    returns.add(returnsLazy2);

    Chart.saveLineChart(new File(outputDir, "returns.html"),
        String.format("Returns (%d\u00A2 Spread)", Math.round(slippage.constSlip * 200)), 1000, 640, true, true,
        returns);
  }
}
