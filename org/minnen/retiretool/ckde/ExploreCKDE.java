package org.minnen.retiretool.ckde;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.minnen.retiretool.broker.Broker;
import org.minnen.retiretool.broker.SimFactory;
import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.broker.TimeInfo;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.YahooIO;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.ml.Example;
import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.ConfigMixed;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.ml.distance.DistanceMetric;
import org.minnen.retiretool.ml.distance.L1;
import org.minnen.retiretool.ml.distance.L2;
import org.minnen.retiretool.ml.distance.WeightedL2;
import org.minnen.retiretool.ml.kernel.EpanechnikovKernel;
import org.minnen.retiretool.ml.kernel.Kernel;
import org.minnen.retiretool.predictor.features.FeatureExtractor;
import org.minnen.retiretool.predictor.features.FeatureSet;
import org.minnen.retiretool.predictor.features.Momentum;
import org.minnen.retiretool.predictor.features.Momentum.CompoundPeriod;
import org.minnen.retiretool.predictor.features.Momentum.ReturnOrMul;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.stats.ReturnStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.PriceModel;
import org.minnen.retiretool.util.Random;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;

public class ExploreCKDE
{
  public static final SequenceStore store        = new SequenceStore();
  public static final Slippage      slippage     = new Slippage(0.03, 0.0);

  // These symbols go back to 13 May 1996.
  // public static final String[] fundSymbols = new String[] { "SPY", "VTSMX", "VBMFX", "VGSIX", "VGTSX", "VFISX",
  // "VFSTX", "VBISX", "EWU", "EWG", "EWJ", "VGENX", "WHOSX", "FAGIX", "BUFHX", "VFICX", "FNMIX", "DFGBX", "SGGDX",
  // "VGPMX", "USAGX", "FSPCX", "FSRBX", "FPBFX", "ETGIX", "VDIGX", "MDY", "VBINX", "VWINX", "MCA", "^IXIC" };

  // public static final String[] fundSymbols = new String[] { "VTSMX", "VBMFX", "VGTSX", "VGSIX", "VGENX", "VGPMX",
  // "VFISX" };

  public static final String[]      fundSymbols  = new String[] { "VTSMX", "VBMFX", "VGENX", "VGPMX", "VFISX" };

  public static final String[]      assetSymbols = new String[fundSymbols.length + 1];

  static {
    // Add "cash" as the last asset since it's not a fund in fundSymbols.
    System.arraycopy(fundSymbols, 0, assetSymbols, 0, fundSymbols.length);
    assetSymbols[assetSymbols.length - 1] = "cash";
  }

  public static List<Example> genExamples(String assetName, FeatureExtractor features, int step, int returnDays,
      long timeStart, long timeEnd)
  {
    final long key = Sequence.Lock.genKey();
    List<Example> examples = new ArrayList<>();
    long today = timeStart;
    if (!TimeLib.isBusinessDay(TimeLib.ms2date(today))) {
      today = TimeLib.toNextBusinessDay(today);
    }
    Broker broker = new Broker(store, Slippage.None, today);
    Sequence seq = store.get(assetName);
    int lastIndex = seq.getClosestIndex(timeEnd);

    while (true) {
      broker.setNewDay(new TimeInfo(today));

      int t1 = seq.getClosestIndex(today);
      int t2 = t1 + returnDays;
      if (t2 > lastIndex) break;
      double mul = FinLib.getTotalReturn(seq, t1, t2, FinLib.AdjClose);
      double r = FinLib.mul2ret(mul);

      seq.lock(0, t1, key);
      FeatureVec fv = features.calculate(broker.accessObject, assetName);
      assert fv.getTime() == today;
      seq.unlock(key);

      Example example = Example.forRegression(fv, r);
      // System.out.printf("[%s] -> [%s] = %s -> %.3f\n", TimeLib.formatDate(seq.getTimeMS(t1)),
      // TimeLib.formatDate(seq.getTimeMS(t2)), fv, r);
      examples.add(example);

      today = TimeLib.plusBusinessDays(today, step);
    }
    return examples;
  }

  public static List<Neighbor> findNeighbors(FeatureVec q, List<Example> examples, DistanceMetric metric,
      Kernel kernel, double fraction, long delay)
  {
    assert q.hasTime();

    // Calculate distance to all potential neighbors.
    List<Neighbor> all = new ArrayList<Neighbor>();
    for (Example example : examples) {
      long dtime = example.getTime() - q.getTime();
      if (dtime >= 0 && dtime < delay) continue;
      double dist = metric.distance(example.x, q);
      all.add(new Neighbor(example.y, dist, 1.0, example.getTime()));
    }
    int nNeighbors = (int) Math.round(all.size() * fraction);
    // System.out.printf("All: %d  Neighbors: %d\n", all.size(), nNeighbors);

    // Grab the closest N neighbors.
    Collections.sort(all);
    List<Neighbor> neighbors = new ArrayList<Neighbor>();
    for (int i = 0; i < nNeighbors; ++i) {
      neighbors.add(all.get(i));
    }

    // Calculate weight for each neighbor.
    if (kernel != null && nNeighbors > 0) {
      double maxDist = neighbors.get(nNeighbors - 1).distance * 1.01;
      // System.out.printf("Max Dist: %.3f\n", maxDist);
      for (Neighbor neighbor : neighbors) {
        neighbor.weight = kernel.weight(neighbor.distance / maxDist);
      }
    }

    return neighbors;
  }

  public static double CalcLogProb(String assetName, FeatureExtractor features, DistanceMetric metric, Kernel kernel,
      double bandwidth, double fraction, long delay, long timeSimStart, long timeSimEnd)
  {
    List<Example> examples = genExamples(assetName, features, 2, 10, timeSimStart, timeSimEnd);
    Uniform uniform = new Uniform(-100.0, 200.0);
    Mixture mixture = new Mixture();
    double probBlackSwan = 0.001;
    double logProb = Library.LOG_ONE;
    for (Example example : examples) {
      List<Neighbor> nbs = findNeighbors(example.x, examples, metric, kernel, fraction, delay);
      KDE kde = new KDE(nbs, bandwidth);
      mixture.clear();
      mixture.add(kde, 1.0 - probBlackSwan);
      mixture.add(uniform, probBlackSwan);
      double p = mixture.density(example.y);
      logProb += Math.log(p);
    }
    return logProb;
  }

  public static FeatureExtractor getRandomFeature(Random rng)
  {
    int nTriggerA = 20 + rng.nextInt(5) * 10;
    int nBaseB = nTriggerA + rng.nextInt(21) * 10;
    int nBaseA = nBaseB + rng.nextInt(7) * 10;
    return new Momentum(nTriggerA, 1, nBaseA, nBaseB, ReturnOrMul.Return, CompoundPeriod.Weekly, FinLib.AdjClose);
  }

  public static void main(String[] args) throws IOException
  {
    File outputDir = new File("g:/web");
    File dataDir = new File("g:/research/finance/");
    assert dataDir.isDirectory();

    Sequence tbillData = DataIO.loadDateValueCSV(new File(dataDir, "treasury-bills-3-month.csv"));
    tbillData.setName("3-Month Treasury Bills");
    tbillData.adjustDatesToEndOfMonth();
    System.out.printf("TBills: [%s] -> [%s]\n", TimeLib.formatMonth(tbillData.getStartMS()),
        TimeLib.formatMonth(tbillData.getEndMS()));
    store.add(tbillData, "tbilldata");
    store.alias("interest-rates", "tbilldata");

    // Make sure we have the latest data.
    for (String symbol : fundSymbols) {
      YahooIO.updateDailyData(symbol, 8 * TimeLib.MS_IN_HOUR);
    }

    // Load data and trim to same time period.
    List<Sequence> seqs = new ArrayList<>();
    for (String symbol : fundSymbols) {
      File file = YahooIO.getFile(symbol);
      Sequence seq = YahooIO.loadData(file);
      // System.out.printf("%s: [%s]\n", seq.getName(), TimeLib.formatDate(seq.getStartMS()));
      seqs.add(seq);
    }

    long commonStart = TimeLib.calcCommonStart(seqs);
    long commonEnd = TimeLib.calcCommonEnd(seqs);
    commonEnd = TimeLib.toMs(2012, Month.DECEMBER, 31); // TODO
    store.setCommonTimes(commonStart, commonEnd);
    System.out.printf("Common[%d]: [%s] -> [%s]\n", seqs.size(), TimeLib.formatDate(commonStart),
        TimeLib.formatDate(commonEnd));

    long timeSimStart = TimeLib.toMs(TimeLib.ms2date(commonStart).plusWeeks(52 * 4 + 4)
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

    // Run simulation for buy-and-hold of individual assets.
    for (int i = 0; i < fundSymbols.length; ++i) {
      config = new ConfigConst(fundSymbols[i]);
      Predictor predictor = config.build(sim.broker.accessObject, assetSymbols);
      sim.run(predictor, timeSimStart, timeSimEnd, fundSymbols[i]);
      System.out.println(CumulativeStats.calc(sim.returnsMonthly));
      returns.add(sim.returnsMonthly);
    }

    // Lazy 2-fund portfolio for a baseline.
    String[] assetsLazy2 = new String[] { "VTSMX", "VBMFX" };
    config = new ConfigMixed(new DiscreteDistribution(assetsLazy2, new double[] { 0.8, 0.2 }), new ConfigConst(
        assetsLazy2[0]), new ConfigConst(assetsLazy2[1]));
    Predictor lazy2 = config.build(sim.broker.accessObject, assetsLazy2);
    sim.run(lazy2, timeSimStart, timeSimEnd, "Lazy2");
    System.out.println(CumulativeStats.calc(sim.returnsMonthly));
    returns.add(sim.returnsMonthly);

    // Setup CKDE features
    // Momentum mom1 = new Momentum(40, 1, 250, 210, ReturnOrMul.Return, CompoundPeriod.Weekly, FinLib.AdjClose);
    // Momentum mom2 = new Momentum(30, 1, 130, 100, ReturnOrMul.Return, CompoundPeriod.Weekly, FinLib.AdjClose);
    // Momentum mom3 = new Momentum(20, 1, 60, 40, ReturnOrMul.Return, CompoundPeriod.Weekly, FinLib.AdjClose);

    Momentum mom1 = new Momentum(20, 1, 270, 210, ReturnOrMul.Return, CompoundPeriod.Weekly, FinLib.AdjClose);
    Momentum mom2 = new Momentum(20, 1, 90, 70, ReturnOrMul.Return, CompoundPeriod.Weekly, FinLib.AdjClose);
    Momentum mom3 = new Momentum(60, 1, 270, 240, ReturnOrMul.Return, CompoundPeriod.Weekly, FinLib.AdjClose);
    FeatureSet features = new FeatureSet(mom1, mom2, mom3);

    // Momentum[20,1]/[270,210]
    // Momentum[20,1]/[90,70]
    // Momentum[60,1]/[270,240]
    // Log(prob): -5494.974963 [0.597,0.286,0.116]

    // WeightedL2 metric = new WeightedL2(new double[] { 0.64, 0.27, 0.09 });
    WeightedL2 metric = new WeightedL2(new double[] { 0.55, 0.27, 0.18 });
    Kernel kernel = new EpanechnikovKernel();
    double bandwidth = 0.3;
    long delay = Duration.ofDays(730).toMillis();
    double fraction = 0.3;

    String assetName = "VTSMX";
    // Random rng = new Random();
    // double best = Double.NEGATIVE_INFINITY;
    // FeatureVec weights = new FeatureVec(3);
    // for (int iter = 0;; ++iter) {
    // // FeatureSet features = new FeatureSet();
    // // for (int i = 0; i < weights.getNumDims(); ++i) {
    // // features.add(getRandomFeature(rng));
    // // }
    // double logProb = CalcLogProb(assetName, features, metric, kernel, bandwidth, fraction, delay, timeSimStart,
    // timeSimEnd);
    // if (logProb > best) {
    // for (int i = 0; i < features.size(); ++i) {
    // System.out.println(features.get(i));
    // }
    // System.out.printf("Log(prob): %f  %s\n", logProb, metric.getWeights());
    // best = logProb;
    // }
    // if (iter > 0 && iter % 10 == 0) {
    // System.out.printf("%d\n", iter);
    // }
    // // metric.setWeights(rng.nextSimplex(metric.getNumDims()));
    // weights.set(0, 0.7 + rng.nextDouble() * 0.1);
    // weights.set(1, 0.3 + rng.nextDouble() * 0.1);
    // weights.set(2, 0.1 + rng.nextDouble() * 0.1);
    // weights._div(weights.sum());
    // assert (Math.abs(weights.sum() - 1.0) < 1e-5);
    // metric.setWeights(weights.get());
    // }

    int returnDays = 20;
    List<Example> examples = genExamples(assetName, features, 1, returnDays, timeSimStart, timeSimEnd);
    System.out.printf("Examples: %d\n", examples.size());
    Sequence helper = new Sequence("helper");
    for (Example example : examples) {
      helper.addData(example.x, example.getTime());
    }
    Sequence asset = store.get(assetName);
    Sequence median = new Sequence("Median");
    Sequence percentile20 = new Sequence("Percentile20");
    Sequence percentile80 = new Sequence("Percentile80");
    Sequence actualReturn = new Sequence("Actual");

    long t0 = TimeLib.getTime();
    for (int i = 0; i < asset.length() - returnDays - 1; ++i) {
      long time = asset.getTimeMS(i);
      int j = helper.getClosestIndex(time);
      List<Neighbor> nbs = findNeighbors(examples.get(j).x, examples, metric, kernel, fraction, delay);
      KDE kd = new KDE(nbs, bandwidth);
      median.addData(kd.percentile(50.0), time);
      percentile20.addData(kd.percentile(20.0), time);
      percentile80.addData(kd.percentile(80.0), time);
      double tr = FinLib.getTotalReturn(asset, i, i + returnDays, FinLib.AdjClose);
      actualReturn.addData(FinLib.mul2ret(tr), time);
      // TODO prob of most recent returnDays 
    }
    long t1 = TimeLib.getTime();
    System.out.printf("Time: %d\n", t1 - t0);
    // median._mul(10.0);
    // percentile20._mul(10.0);
    // percentile80._mul(10.0);

    Chart.saveLineChart(new File(outputDir, "median.html"), assetName + ": Price vs. Median", 1000, 640, false, true,
        new Sequence[] { actualReturn, median, percentile20, percentile80 });

    // Generate chart showing cumulative returns for all methods.
    Chart.saveLineChart(new File(outputDir, "returns.html"),
        String.format("Returns (%d\u00A2 Spread)", Math.round(slippage.constSlip * 200)), 1000, 640, true, true,
        returns);
  }
}
