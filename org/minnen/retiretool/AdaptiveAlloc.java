package org.minnen.retiretool;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.minnen.retiretool.broker.Broker;
import org.minnen.retiretool.broker.PriceModel;
import org.minnen.retiretool.broker.SimFactory;
import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.broker.TimeInfo;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.ml.RegressionModel;
import org.minnen.retiretool.ml.RegressionExample;
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
import org.minnen.retiretool.predictor.features.FeatureExtractor;
import org.minnen.retiretool.predictor.features.FeatureSet;
import org.minnen.retiretool.predictor.features.Momentum;
import org.minnen.retiretool.predictor.optimize.AdaptiveScanner;
import org.minnen.retiretool.predictor.optimize.ConfigScanner;
import org.minnen.retiretool.predictor.optimize.Optimizer;
import org.minnen.retiretool.stats.BinaryPredictionStats;
import org.minnen.retiretool.stats.ComparisonStats;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.ojalgo.matrix.BasicMatrix;
import org.ojalgo.matrix.PrimitiveMatrix;

public class AdaptiveAlloc
{
  public static final SequenceStore store        = new SequenceStore();

  public static final Slippage      slippage     = new Slippage(0.03, 0.0);

  // These symbols go back to 13 May 1996.
  public static final String[]      fundSymbols  = new String[] { "SPY", "VTSMX", "VBMFX", "VGSIX", "VGTSX", "EWU",
      "EWG", "EWJ", "VGENX", "WHOSX", "FAGIX", "BUFHX", "VFICX", "FNMIX", "DFGBX", "SGGDX", "VGPMX", "USAGX", "FSPCX",
      "FSRBX", "FPBFX", "ETGIX", "VDIGX", "MDY", "VBINX", "^IXIC" };

  // public static final String[] fundSymbols = new String[] { "VTSMX", "VBMFX", "VGSIX", "VGTSX", "VGENX", "WHOSX",
  // "USAGX" };

  // These symbols go back to 27 April 1992.
  // public static final String[] fundSymbols = new String[] { "VTSMX", "VBMFX", "VGENX", "WHOSX", "FAGIX", "DFGBX",
  // "VGPMX", "USAGX", "FSPCX", "FSRBX", "FPBFX" };

  // Funds in the my 401k.
  // public static final String[] fundSymbols = new String[] { "VEMPX", "VIIIX", "VTPSX", "VBMPX", "RGAGX", "CRISX",
  // "DODGX", "FDIKX", "MWTSX", "TISCX", "VGSNX", "VWIAX" };

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
    final int maxOptMonths = 12 * 5;

    // Simulator used for optimizing predictor parameters.
    Simulation simOpt = simFactory.build();
    AdaptiveScanner scanner = new AdaptiveScanner(FinLib.AdjClose);

    // Simulator used for tracking walk-forward results.
    Simulation wfSim = simFactory.build();
    wfSim.setupRun(null, timeSimStart, TimeLib.TIME_END, "WalkForward");

    long testStart = timeSimStart;
    assert testStart >= wfSim.getStartMS();
    final long timeFirstAbleToPredict = TimeLib.toMs(TimeLib.toNextBusinessDay(TimeLib.ms2date(
        store.getCommonStartTime()).plusMonths(6))); // TODO pull requirements from config/predictor
    System.out.printf("First Able to Predict: [%s]\n", TimeLib.formatDate(timeFirstAbleToPredict));

    while (testStart < wfSim.getEndMS()) {
      // New test period is extends N months beyond test start time.
      final long testEnd = Math.min(
          TimeLib.toPreviousBusinessDay(TimeLib.toMs(TimeLib.ms2date(testStart).plusMonths(stepMonths))),
          wfSim.getEndMS());

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

  public static void genPredictionMap(Sequence guideSeq, File outputDir) throws IOException
  {
    Broker broker = new Broker(store, Slippage.None, guideSeq);
    final long key = Sequence.Lock.genKey();

    final long timeDataStart = store.getCommonStartTime();

    // TODO pull requirements from config/predictor
    final long timeFirstAbleToPredict = TimeLib.toMs(TimeLib.toNextBusinessDay(TimeLib.ms2date(timeDataStart)
        .plusWeeks(49)));
    long today = TimeLib.toLastBusinessDayOfWeek(timeFirstAbleToPredict);

    // Early predictors need some examples so delay simulation.
    final long timeStartSim = TimeLib.toMs(TimeLib.toNextBusinessDay(TimeLib.ms2date(timeFirstAbleToPredict)
        .plusMonths(6)));
    System.out.printf("Prediction Map: [%s] -> [%s]\n", TimeLib.formatDate(today),
        TimeLib.formatDate(guideSeq.getEndMS()));

    Sequence scatterData = new Sequence();
    BinaryPredictionStats stats = new BinaryPredictionStats();
    FeatureSet features = new FeatureSet(broker.accessObject);
    for (int i = 2; i <= 12; ++i) {
      Momentum momentum = new Momentum(20, 1, i * 20, (i - 1) * 20, Momentum.ReturnOrMul.Return,
          Momentum.CompoundPeriod.Weekly, FinLib.AdjClose, broker.accessObject);
      features.add(momentum);
    }
    // features.add(new Momentum(5, 1, 10, 5, FinLib.AdjClose, broker.accessObject));

    RegressionModel model = null;
    List<RegressionExample> examples = new ArrayList<>();
    long ta = TimeLib.getTime();
    while (true) {
      final long timePredictStart = TimeLib.toNextBusinessDay(today);
      final long timePredictEnd = TimeLib.toMs(TimeLib.toNextBusinessDay(TimeLib.toLastBusinessDayOfWeek(TimeLib
          .ms2date(today).plusWeeks(1))));
      if (timePredictEnd > guideSeq.getEndMS()) break;

      // System.out.printf("Learn: [%s] -> [%s]  Predict: [%s] -> [%s]\n", TimeLib.formatDate(timeLearnStart),
      // TimeLib.formatDate(today), TimeLib.formatDate(timePredictStart), TimeLib.formatDate(timePredictEnd));

      broker.setNewDay(new TimeInfo(today));
      for (String assetName : fundSymbols) {
        Sequence seq = store.get(assetName);

        // Calculate actual return of following week.
        int index1 = seq.getIndexAt(timePredictStart);
        int index2 = seq.getIndexAt(timePredictEnd);
        if (index1 < 0 || index2 < 0) {
          // System.out.printf("[%s]=%d  [%s]=%d\n", TimeLib.formatDate(timePredictStart), index1,
          // TimeLib.formatDate(timePredictEnd), index2);
          break;
        }
        double priceFrom = PriceModel.adjOpenModel.getPrice(seq.get(index1));
        double priceTo = PriceModel.adjOpenModel.getPrice(seq.get(index2));
        double actualMul = priceTo / priceFrom;
        double actualReturn = FinLib.mul2ret(actualMul);

        // Extract features for predicting the return.
        // store.lock(TimeLib.TIME_BEGIN, broker.getTime(), key);
        seq.lock(0, seq.getClosestIndex(broker.getTime()), key);
        FeatureVec fv = features.calculate(assetName);
        seq.unlock(key);
        // store.unlock(key);

        if (today >= timeStartSim) {
          double predictedMul = model.predict(fv);
          double predictedReturn = FinLib.mul2ret(predictedMul);

          String title = String.format("%s [%s]", assetName, TimeLib.formatDate(today));
          FeatureVec scatter = new FeatureVec(title, 2, predictedReturn, actualReturn);
          if (stats.size() % 29 == 0) {
            scatterData.addData(scatter, today);
          }
          stats.add(predictedReturn, actualReturn);
        }

        // Add the current week as an example for the future.
        RegressionExample example = new RegressionExample(fv, actualMul);
        // System.out.println(example);
        examples.add(example);
      }
      // model = RegressionModel.learnRidge(examples, 1.0);
      // model = RegressionModel.learnLasso(examples, 20.0);
      // model = RegressionModel.learnRF(examples, 20, -1);
      model = RegressionModel.learnTree(examples);
      System.out.printf("[%s] %s\n", TimeLib.formatDate(today), stats);
      today = TimeLib.toMs(TimeLib.toLastBusinessDayOfWeek(TimeLib.ms2date(today).plusWeeks(1)));
    }
    long tb = TimeLib.getTime();

    System.out.printf("%s  (%s)\n", stats, TimeLib.formatDuration(tb - ta));
    System.out.printf("Paired: %.2f%%\n", stats.weightedPairedAccuracy());

    Sequence scatterAligned = scatterData.dup();
    {
      int nr = scatterData.size();
      BasicMatrix.Builder<PrimitiveMatrix> builder = PrimitiveMatrix.getBuilder(nr, 1);
      for (int i = 0; i < nr; ++i) {
        double v = scatterData.get(i, 0);
        builder.set(i, 0, v);
      }
      PrimitiveMatrix A = builder.build();

      builder = PrimitiveMatrix.getBuilder(nr, 1);
      for (int i = 0; i < nr; ++i) {
        builder.set(i, 0, scatterData.get(i, 1));
      }
      PrimitiveMatrix b = builder.build();

      PrimitiveMatrix x = A.solve(b);
      // System.out.println(x);

      PrimitiveMatrix m = A.multiply(x);
      double r = 0.0;
      for (int i = 0; i < nr; ++i) {
        double v = m.get(i, 0);
        double diff = v - b.get(i, 0);
        r += diff * diff;
        scatterAligned.get(i).set(0, v);
      }
      System.out.printf("r = %f\n", r);
    }

    Chart.saveScatterPlot(new File(outputDir, "predictions-raw.html"), "Predictions", 1000, 1000, 3, new String[] {
        "Predicted", "Actual" }, scatterData);

    Chart.saveScatterPlot(new File(outputDir, "predictions-aligned.html"), "Predictions", 1000, 1000, 3, new String[] {
        "Predicted", "Actual" }, scatterAligned);
  }

  public static void genPredictionData(Sequence guideSeq, List<RegressionExample> pointExamples,
      List<RegressionExample> pairExamples, File outputDir) throws IOException
  {
    final long key = Sequence.Lock.genKey();
    final long timeDataStart = store.getCommonStartTime();

    // TODO pull requirements from config/predictor
    final long timeFirstAbleToPredict = TimeLib.toMs(TimeLib.toNextBusinessDay(TimeLib.ms2date(timeDataStart)
        .plusWeeks(49)));
    long today = TimeLib.toLastBusinessDayOfWeek(timeFirstAbleToPredict);
    System.out.printf("Prediction Data Range: [%s] -> [%s]\n", TimeLib.formatDate(today),
        TimeLib.formatDate(guideSeq.getEndMS()));

    Broker broker = new Broker(store, Slippage.None, guideSeq);
    FeatureSet features = new FeatureSet(broker.accessObject);
    for (int i = 2; i <= 12; ++i) {
      Momentum momentum = new Momentum(20, 1, i * 20, (i - 1) * 20, Momentum.ReturnOrMul.Return,
          Momentum.CompoundPeriod.Weekly, FinLib.AdjClose, broker.accessObject);
      features.add(momentum);
    }
    // features.add(new Momentum(5, 1, 10, 5, FinLib.AdjClose, broker.accessObject));
    System.out.printf("Features: %d\n", features.size());

    while (true) {
      final long timePredictStart = TimeLib.toNextBusinessDay(today);
      final long timePredictEnd = TimeLib.toMs(TimeLib.toNextBusinessDay(TimeLib.toLastBusinessDayOfWeek(TimeLib
          .ms2date(today).plusWeeks(1))));
      if (timePredictEnd > guideSeq.getEndMS()) break;

      broker.setNewDay(new TimeInfo(today));
      List<RegressionExample> examples = new ArrayList<>();
      for (String assetName : fundSymbols) {
        Sequence seq = store.get(assetName);

        // Calculate actual return of following week.
        int index1 = seq.getIndexAt(timePredictStart);
        int index2 = seq.getIndexAt(timePredictEnd);
        if (index1 < 0 || index2 < 0) {
          // System.out.printf("[%s]=%d  [%s]=%d\n", TimeLib.formatDate(timePredictStart), index1,
          // TimeLib.formatDate(timePredictEnd), index2);
          break;
        }
        double priceFrom = PriceModel.adjOpenModel.getPrice(seq.get(index1));
        double priceTo = PriceModel.adjOpenModel.getPrice(seq.get(index2));
        double actualMul = priceTo / priceFrom;
        double actualReturn = FinLib.mul2ret(actualMul);

        // Extract features for predicting the return.
        seq.lock(0, seq.getClosestIndex(broker.getTime()), key);
        FeatureVec fv = features.calculate(assetName);
        seq.unlock(key);

        // Add the current week as an example for the future.
        RegressionExample example = new RegressionExample(fv, actualReturn);
        examples.add(example);
        // System.out.println(example);
      }
      pointExamples.addAll(examples);
      for (int i = 0; i < examples.size(); ++i) {
        RegressionExample exi = examples.get(i);
        for (int j = i + 1; j < examples.size(); ++j) {
          RegressionExample exj = examples.get(j);
          double dy = exj.y - exi.y;
          if (Math.abs(dy) < 0.5) continue;
          FeatureVec dx = exj.x.sub(exi.x);
          RegressionExample pairExample = new RegressionExample(dx, dy);
          pairExamples.add(pairExample);
        }
      }

      today = TimeLib.toMs(TimeLib.toLastBusinessDayOfWeek(TimeLib.ms2date(today).plusWeeks(1)));
    }
    FeatureVec mean = RegressionExample.mean(pointExamples);
    System.out.printf("Pointwise Examples: %d\n", pointExamples.size());
    System.out.printf(" Mean: %s\n", mean);

    mean = RegressionExample.mean(pairExamples);
    System.out.printf("Pairwise Examples: %d\n", pairExamples.size());
    System.out.printf(" Mean: %s\n", mean);

    if (outputDir != null) {
      RegressionExample.save(pointExamples, new File(outputDir, "point-examples.txt"));
      RegressionExample.save(pairExamples, new File(outputDir, "pair-examples.txt"));
    }
  }

  public static void predictRank(List<RegressionExample> examples)
  {
    int kFolds = 5;
    for (int iFold = 0; iFold < kFolds; ++iFold) {
      List<RegressionExample> train = new ArrayList<RegressionExample>();
      List<RegressionExample> test = new ArrayList<RegressionExample>();
      RegressionExample.getFold(iFold, kFolds, train, test, examples);
      RegressionModel model = RegressionModel.learnRF(train, 20, -1, 128);

      int nc = 0;
      for (RegressionExample example : test) {
        double y = model.predict(example.x);
        if (y * example.y > 0) ++nc;
      }
      System.out.printf(" %.3f\n", 100.0 * nc / test.size());
    }
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
    // long commonStart = TimeLib.toMs(2011, Month.AUGUST, 1);
    // long commonEnd = TimeLib.calcCommonEnd(seqs);
    long commonEnd = TimeLib.toMs(2012, Month.DECEMBER, 31);
    store.setCommonTimes(commonStart, commonEnd);
    System.out.printf("Common[%d]: [%s] -> [%s]\n", seqs.size(), TimeLib.formatDate(commonStart),
        TimeLib.formatDate(commonEnd));

    long timeSimStart = TimeLib.toMs(TimeLib.ms2date(commonStart).plusMonths(12)
        .with(TemporalAdjusters.firstDayOfMonth()));
    double nSimMonths = TimeLib.monthsBetween(timeSimStart, commonEnd);
    // System.out.printf("Simulation Start: [%s] (%.1f months)\n", TimeLib.formatDate(timeSimStart), nSimMonths);

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
    SimFactory simFactory = new SimFactory(store, guideSeq, slippage, 0, valueModel, quoteModel);
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
    // Chart.saveLineChart(new File(outputDir, "individual-symbols.html"), "Individual Returns", 1200, 900, true, true,
    // symbolReturns);

    PredictorConfig config;
    Predictor predictor;
    List<Sequence> returns = new ArrayList<>();

    // Lazy 2-fund portfolio.
    // String[] lazy2 = new String[] { "VTSMX", "VBMFX" };
    // config = new ConfigMixed(new DiscreteDistribution(lazy2, new double[] { 0.7, 0.3 }), new ConfigConst(lazy2[0]),
    // new ConfigConst(lazy2[1]));
    // predictor = config.build(sim.broker.accessObject, lazy2);
    // Sequence returnsLazy2 = sim.run(predictor, timeSimStart, "Lazy2");
    // System.out.println(CumulativeStats.calc(returnsLazy2));
    // returns.add(returnsLazy2);
    // Chart.saveHoldings(new File(outputDir, "holdings-lazy2.html"), sim.holdings);

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
    // // String[] lazy4 = new String[] { "VIIIX", "VEMPX", "VBMPX", "VGSNX", "VTPSX" };
    // config = new ConfigMixed(new DiscreteDistribution(lazy4, new double[] { 0.4, 0.2, 0.1, 0.3 }), new ConfigConst(
    // lazy4[0]), new ConfigConst(lazy4[1]), new ConfigConst(lazy4[2]), new ConfigConst(lazy4[3]));
    // predictor = config.build(sim.broker.accessObject, lazy4);
    // Sequence returnsLazy4 = sim.run(predictor, timeSimStart, "Lazy4");
    // System.out.println(CumulativeStats.calc(returnsLazy4));
    // returns.add(returnsLazy4);

    // All stock.
    // PredictorConfig stockConfig = new ConfigConst("VTSMX");
    // predictor = stockConfig.build(sim.broker.accessObject, assetSymbols);
    // Sequence returnsStock = sim.run(predictor, timeSimStart, "Stock");
    // System.out.println(CumulativeStats.calc(returnsStock));
    // returns.add(returnsStock);

    // All bonds.
    // PredictorConfig bondConfig = new ConfigConst("VBMFX");
    // predictor = bondConfig.build(sim.broker.accessObject, assetSymbols);
    // Sequence returnsBonds = sim.run(predictor, timeSimStart, "Bonds");
    // System.out.println(CumulativeStats.calc(returnsBonds));
    // returns.add(returnsBonds);

    // Volatility-Responsive Asset Allocation.
    // predictor = new VolResPredictor("VTSMX", "VBMFX", sim.broker.accessObject);
    // Sequence returnsVolRes = sim.run(predictor, timeSimStart, "VolRes");
    // System.out.println(CumulativeStats.calc(returnsVolRes));
    // returns.add(returnsVolRes);

    // PredictorConfig tacticalConfig = new ConfigTactical(FinLib.AdjClose, "SPY", "VTSMX", "VGSIX", "VGTSX", "EWU",
    // "EWG", "EWJ",
    // "VGENX", "WHOSX", "FAGIX", "BUFHX", "VFICX", "FNMIX", "DFGBX", "SGGDX", "VGPMX", "USAGX", "FSPCX", "FSRBX",
    // "FPBFX", "ETGIX", "VBMFX", "cash");

    // PredictorConfig tacticalConfig = new ConfigTactical(FinLib.AdjClose, "VTSMX", "MDY", "VGSIX", "VGTSX", "VGENX",
    // "WHOSX",
    // "VGPMX",
    // "USAGX", "VBMFX", "cash");

    // PredictorConfig tacticalConfig = new ConfigTactical(FinLib.AdjClose, "VTSMX", "SPY", "VBMFX");

    // predictor = tacticalConfig.build(sim.broker.accessObject, assetSymbols);
    // Sequence returnsTactical = sim.run(predictor, timeSimStart,"Tactical");
    // System.out.println(CumulativeStats.calc(returnsTactical));

    // Run adaptive asset allocation.
    final TradeFreq tradeFreq = TradeFreq.Weekly;
    final int pctQuantum = 2;
    // PredictorConfig minvarConfig = new ConfigAdaptive(15, 0.9, Weighting.MinVar, 20, 100, 80, 0.7, -1, pctQuantum,
    // tradeFreq, FinLib.AdjClose);
    // PredictorConfig ewConfig2 = new ConfigAdaptive(-1, -1, Weighting.Equal, 30, 110, 60, 0.5, 5, pctQuantum,
    // tradeFreq, FinLib.AdjClose);

    // Adaptive Asset Allocation (Equal Weight).
    PredictorConfig equalWeightConfig1 = ConfigAdaptive.buildEqualWeight(40, 100, 80, 0.5, 4, pctQuantum, tradeFreq,
        FinLib.AdjClose);

    // for (int x = 20; x <= 220; x += 20) {
    // System.out.printf("Month: %d\n", x / 20 + 1);
    // config = ConfigAdaptive.buildEqualWeight(20, 120, 100, 0.5, 4, pctQuantum, tradeFreq, FinLib.AdjClose);
    // genPredictionMap(guideSeq, outputDir);

    List<RegressionExample> pointExamples = new ArrayList<>();
    List<RegressionExample> pairExamples = new ArrayList<>();
    genPredictionData(guideSeq, pointExamples, pairExamples, outputDir);

    // predictRank(pairExamples);

    RegressionModel modelRank = RegressionModel.learnRF(pairExamples, 10, -1, 128);

    // PredictorConfig equalWeightConfig2 = new ConfigAdaptive(-1, -1, Weighting.Equal, 20, 120, 100, 0.5, 2,
    // pctQuantum,
    // tradeFreq, FinLib.AdjClose);
    // predictor = equalWeightConfig1.build(sim.broker.accessObject, assetSymbols);
    // // config = new ConfigMixed(new DiscreteDistribution(0.5, 0.5), equalWeightConfig1, equalWeightConfig2);
    // // predictor = config.build(sim.broker.accessObject, assetSymbols);
    // sim.run(predictor, timeSimStart, "Adaptive1");
    // System.out.println(CumulativeStats.calc(sim.returnsMonthly));
    // returns.add(sim.returnsMonthly);
    // Chart.saveHoldings(new File(outputDir, "holdings-adaptive.html"), sim.holdings, sim.store);

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
    // Chart.saveHoldings(new File(outputDir, "holdings-adaptive.html"), wfSim.holdings, wfSim.store);

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

    // Chart.saveLineChart(new File(outputDir, "returns.html"),
    // String.format("Returns (%d\u00A2 Spread)", Math.round(slippage.constSlip * 200)), 1000, 640, true, true,
    // returns);

    // Chart.saveAnnualStatsTable(new File(outputDir, "annual-stats.html"), 1000, false, returns);
    // Chart.saveComparisonTable(new File(outputDir, "comparison.html"), 1000, compStats);

    // Account account = sim.broker.getAccount(0);
    // account.printTransactions();//TimeLib.TIME_BEGIN, TimeLib.toMs(1996, Month.DECEMBER, 31));
  }
}
