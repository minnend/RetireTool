package org.minnen.retiretool.vanguard;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.minnen.retiretool.broker.SimFactory;
import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.ml.Stump;
import org.minnen.retiretool.predictor.config.ConfigAdaptive;
import org.minnen.retiretool.predictor.config.ConfigAdaptive.TradeFreq;
import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.ConfigMixed;
import org.minnen.retiretool.predictor.config.ConfigTactical;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.AdaptivePredictor;
import org.minnen.retiretool.predictor.daily.MixedPredictor;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.predictor.features.FeatureExtractor;
import org.minnen.retiretool.predictor.features.ITAScore;
import org.minnen.retiretool.predictor.features.Momentum;
import org.minnen.retiretool.stats.ComparisonStats;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.stats.DurationalStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.PriceModel;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;

public class VanguardSummarySim
{
  public static final SequenceStore             store        = new SequenceStore();

  public static final VanguardFund.FundSet      fundSet      = VanguardFund.FundSet.All;
  public static final Slippage                  slippage     = Slippage.None;
  public static final String[]                  fundSymbols  = VanguardFund.getFundNames(fundSet);
  public static final String[]                  assetSymbols = new String[fundSymbols.length + 1];
  public static final Map<String, VanguardFund> funds        = VanguardFund.getFundMap(fundSet);
  public static final String[]                  statNames    = new String[] { "CAGR", "MaxDrawdown", "Worst Period",
      "10th Percentile", "Median "                          };

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
    seqs.sort(Sequence.getStartDateComparator());
    for (Sequence seq : seqs) {
      String symbol = seq.getName();
      System.out.printf("%5s [%s] -> [%s]  %s\n", symbol, TimeLib.formatDate2(seq.getStartMS()),
          TimeLib.formatDate2(seq.getEndMS()), funds.get(symbol).description);
    }

    long commonStart = TimeLib.calcCommonStart(seqs);
    long commonEnd = TimeLib.calcCommonEnd(seqs);
    // commonEnd = TimeLib.toMs(2012, Month.DECEMBER, 31); // TODO
    store.setCommonTimes(commonStart, commonEnd);
    System.out.printf("Common[%d]: [%s] -> [%s]\n", seqs.size(), TimeLib.formatDate(commonStart),
        TimeLib.formatDate(commonEnd));

    long timeSimStart = commonStart;
    // long timeSimStart = TimeLib.toMs(TimeLib.ms2date(commonStart).plusWeeks(53 + 4)
    // .with(TemporalAdjusters.firstDayOfMonth()));
    // timeSimStart = TimeLib.toMs(2005, Month.JANUARY, 1); // TODO
    long timeSimEnd = commonEnd;
    // timeSimEnd = TimeLib.toMs(2012, Month.DECEMBER, 31); // TODO
    double nSimMonths = TimeLib.monthsBetween(timeSimStart, timeSimEnd);
    System.out.printf("Simulation: [%s] -> [%s] (%.1f months total)\n", TimeLib.formatDate(timeSimStart),
        TimeLib.formatDate(timeSimEnd), nSimMonths);

    // Extract common subsequence from each data sequence and add to the sequence store.
    for (int i = 0; i < seqs.size(); ++i) {
      Sequence seq = seqs.get(i);
      seq = seq.subseq(commonStart, commonEnd, EndpointBehavior.Closest);
      seqs.set(i, seq);
      store.add(seq);
    }

    File file = new File(outputDir, "vanguard-portfolios-sim.txt");

    // Setup simulation and calculate portfolio stats.
    // Sequence guideSeq = store.get(fundSymbols[0]).dup();
    // PriceModel valueModel = PriceModel.adjCloseModel;
    // PriceModel quoteModel = new PriceModel(PriceModel.Type.Open, true);
    // double startingBalance = 50000.0;
    // double monthlyDeposit = 0.0; // TODO real question is what method works best with ongoing contributions.
    // SimFactory simFactory = new SimFactory(store, guideSeq, slippage, 0, startingBalance, monthlyDeposit, valueModel,
    // quoteModel);
    // Simulation sim = simFactory.build();
    // SimRunner runner = new SimRunner(sim, timeSimStart, timeSimEnd, 5 * 12); // TODO best duration?
    // SummaryTools.savePortfolioStats(runner, file);

    List<FeatureVec> portfolioStats = SummaryTools.loadPortfolioStats(file, true);
    System.out.printf("Portfolio Stats Loaded: %d\n", portfolioStats.size());
    SummaryTools.prunePortfolios(portfolioStats);
    System.out.printf("Portfolios (after pruning): %d\n", portfolioStats.size());

    List<FeatureVec> list = new ArrayList<>();
    for (FeatureVec v : portfolioStats) {
      // if (v.get(1) > 40.0) continue;
      // if (v.get(2) < -1.0) continue;
      // if (v.get(3) < 0.0) continue;
      // if (v.get(4) < 6.0) continue;
      list.add(v);
    }
    portfolioStats = list;
    System.out.printf("Filtered Portfolios: %d\n", portfolioStats.size());

    int nStats = portfolioStats.get(0).getNumDims();
    for (int i = 0; i < nStats; ++i) {
      for (int j = i + 1; j < nStats; ++j) {
        Sequence scatter = new Sequence().append(portfolioStats);
        String filename = String.format("vanguard-scatter-%d%d.html", i + 1, j + 1);
        ChartConfig chartConfig = new ChartConfig(new File(outputDir, filename)).setType(ChartConfig.Type.Scatter)
            .setSize(1200, 800).setRadius(2).setData(scatter).showToolTips(true).setDimNames(statNames)
            .setAxisTitles(statNames[i], statNames[j]).setIndexXY(i, j);
        Chart.saveScatterPlot(chartConfig);
      }
    }
  }
}
