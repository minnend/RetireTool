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
import org.minnen.retiretool.broker.BrokerInfoAccess;
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
import org.minnen.retiretool.ml.ClassificationModel;
import org.minnen.retiretool.ml.PositiveQuadrant;
import org.minnen.retiretool.ml.RegressionModel;
import org.minnen.retiretool.ml.Example;
import org.minnen.retiretool.ml.Stump;
import org.minnen.retiretool.ml.rank.ColleyRanker;
import org.minnen.retiretool.ml.rank.Ranker;
import org.minnen.retiretool.predictor.config.ConfigAdaptive;
import org.minnen.retiretool.predictor.config.ConfigAdaptive.TradeFreq;
import org.minnen.retiretool.predictor.config.ConfigAdaptive.Weighting;
import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.ConfigMixed;
import org.minnen.retiretool.predictor.config.ConfigTactical;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.AdaptiveMomentumPredictor;
import org.minnen.retiretool.predictor.daily.AdaptivePredictor;
import org.minnen.retiretool.predictor.daily.MixedPredictor;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.predictor.daily.VolResPredictor;
import org.minnen.retiretool.predictor.features.FeatureExtractor;
import org.minnen.retiretool.predictor.features.FeatureSet;
import org.minnen.retiretool.predictor.features.Momentum;
import org.minnen.retiretool.predictor.features.BasicStats;
import org.minnen.retiretool.predictor.features.RiskAdjustedReturn;
import org.minnen.retiretool.predictor.features.StdDev;
import org.minnen.retiretool.predictor.optimize.AdaptiveScanner;
import org.minnen.retiretool.predictor.optimize.ConfigScanner;
import org.minnen.retiretool.predictor.optimize.Optimizer;
import org.minnen.retiretool.stats.BinaryPredictionStats;
import org.minnen.retiretool.stats.ComparisonStats;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.stats.ReturnStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Histogram;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.Chart.ChartType;
import org.ojalgo.matrix.BasicMatrix;
import org.ojalgo.matrix.PrimitiveMatrix;

public class AdaptiveAlloc
{
  public static final SequenceStore store                    = new SequenceStore();

  public static final Slippage      slippage                 = new Slippage(0.03, 0.0);
  public static final double        POINTWISE_THRESHOLD      = 0.1;
  public static final double        PAIRWISE_THRESHOLD       = 0.0;
  public static final double        MIN_PAIRWISE_RETURN_DIFF = 0.5;

  public static final boolean       GENERATE_PAIRWISE_DATA   = false;

  // These symbols go back to 13 May 1996.
  public static final String[]      fundSymbols              = new String[] { "SPY", "VTSMX", "VBMFX", "VGSIX",
      "VGTSX", "EWU", "EWG", "EWJ", "VGENX", "WHOSX", "FAGIX", "BUFHX", "VFICX", "FNMIX", "DFGBX", "SGGDX", "VGPMX",
      "USAGX", "FSPCX", "FSRBX", "FPBFX", "ETGIX", "VDIGX", "MDY", "VBINX", "VWINX", "^IXIC" };

  // public static final String[] fundSymbols = FinLib.VANGUARD_INVESTOR_FUNDS;

  // public static final String[] fundSymbols = new String[] { "VTSMX", "VBMFX", "VGTSX", "VWINX",
  // "FPBFX", "USAGX" };

  // public static final String[] fundSymbols = new String[] { "VTSMX", "VBMFX", "VGSIX",
  // "VGTSX", "VGENX", "WHOSX", "FAGIX", "DFGBX", "VGPMX", "VDIGX", "USAGX", "FSPCX", "FSRBX", "FPBFX" };

  // public static final String[] fundSymbols = new String[] { "VTSMX", "VBMFX", "VGSIX", "VGTSX",
  // "VGENX", "WHOSX", "USAGX" };

  // public static final String[] fundSymbols = new String[] { "VTSMX", "VGTSX", "VBMFX" };

  // These symbols go back to 27 April 1992.
  // public static final String[] fundSymbols = new String[] { "VTSMX", "VBMFX", "VGENX", "WHOSX",
  // "FAGIX", "DFGBX", "VGPMX", "USAGX", "FSPCX", "FSRBX", "FPBFX", "VWINX" };

  // public static final String[] fundSymbols = new String[] { "VTSMX", "VBMFX", "VGENX", "WHOSX",
  // "USAGX", "FPBFX", "VWINX" };

  // Funds in the my 401k.
  // public static final String[] fundSymbols = new String[] { "VEMPX", "VIIIX", "VTPSX", "VBMPX",
  // "RGAGX", "CRISX", "DODGX", "FDIKX", "MWTSX", "TISCX", "VGSNX", "VWIAX" };

  // QQQ, XLK, AAPL, MSFT
  public static final String[]      assetSymbols             = new String[fundSymbols.length + 1];

  static {
    // Add "cash" as the last asset since it's not a fund in fundSymbols.
    System.arraycopy(fundSymbols, 0, assetSymbols, 0, fundSymbols.length);
    assetSymbols[assetSymbols.length - 1] = "cash";

    // Silence debug spew from JOptimizer.
    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
  }

  public static Simulation walkForwardOptimizationOld(long timeSimStart, SimFactory simFactory)
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
      System.out.printf("Stats: [%s] -> [%s]: %.3f, %.2f  %s\n", TimeLib.formatDate(testStart),
          TimeLib.formatDate(testEnd), stats.cagr, stats.drawdown, config);

      // Advance test start time by N months.
      testStart = TimeLib.toMs(TimeLib.ms2date(testStart).plusMonths(stepMonths)
          .with(TemporalAdjusters.firstDayOfMonth()));
    }
    wfSim.finishRun();
    return wfSim;
  }

  public static void extendPredictionData(long timeStart, long timeEnd, FeatureExtractor featureExtractor,
      List<Example> pointExamples)
  {
    final long key = Sequence.Lock.genKey();
    long today = TimeLib.toLastBusinessDayOfWeek(timeStart);
    assert today < timeEnd;

    long lastTime = TimeLib.TIME_ERROR;
    if (!pointExamples.isEmpty()) {
      lastTime = pointExamples.get(pointExamples.size() - 1).getTime();
      assert lastTime != TimeLib.TIME_ERROR;
      long time = TimeLib.toLastBusinessDayOfWeek(lastTime);
      if (time <= lastTime) {
        time = TimeLib.toMs(TimeLib.ms2date(lastTime).plusWeeks(1));
        time = TimeLib.toLastBusinessDayOfWeek(time);
      }
      assert time > lastTime;
      today = Math.max(today, time);
    }
    // System.out.printf("Gen Data Range: [%s] -> [%s]\n", TimeLib.formatDate(today), TimeLib.formatDate(timeEnd));

    Broker broker = new Broker(store, Slippage.None, today);
    while (true) {
      final long timePredictStart = TimeLib.toNextBusinessDay(today);
      final long timePredictEnd = TimeLib.toMs(TimeLib.toNextBusinessDay(TimeLib.toLastBusinessDayOfWeek(TimeLib
          .ms2date(today).plusWeeks(1))));
      if (timePredictEnd > timeEnd) break;

      // System.out.printf("GenPredData: [%s] -> [%s]\n", TimeLib.formatDate(timePredictStart),
      // TimeLib.formatDate(timePredictEnd));

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
        seq.lock(0, seq.getClosestIndex(broker.getTime()), key);
        FeatureVec fv = featureExtractor.calculate(broker.accessObject, assetName);
        seq.unlock(key);
        assert fv.getTime() == today;
        assert fv.getName().equals(assetName);

        // Add the current week as an example for the future.
        int k = (actualReturn > POINTWISE_THRESHOLD ? 1 : 0);
        Example example = Example.forBoth(fv, actualReturn, k);
        pointExamples.add(example);
      }
      today = TimeLib.toMs(TimeLib.toLastBusinessDayOfWeek(TimeLib.ms2date(today).plusWeeks(1)));
    }
  }

  public static void reweightExamples(List<Example> examples)
  {
    final int N = examples.size();
    // final int nMonthsFlatWeight = 36;
    // final long timeStart = examples.get(0).getTime();
    // final long timeEnd = examples.get(N - 1).getTime();

    for (int i = 0; i < N; ++i) {
      Example example = examples.get(i);
      // long time = example.getTime();
      // long timeStartDip = TimeLib.toMs(TimeLib.ms2date(timeEnd).minusMonths(nMonthsFlatWeight));
      // double timeWeight = 1.0;
      // if (time < timeStartDip) {
      // long totalDipTime = timeStartDip - timeStart;
      // long d2 = timeStartDip - time;
      // double alpha = (double) d2 / totalDipTime;
      // double beta = 1.0 - alpha;
      // timeWeight = alpha * 0.5 + beta;
      // }
      double absReturn = Math.abs(example.y);
      // double returnWeight = 10.0 + Math.sqrt(1.0 + absReturn);
      double returnWeight = 1.0;// 10.0 + absReturn;
      double w = returnWeight; // * timeWeight;
      example.x.setWeight(w);
    }
  }

  public static Simulation walkForwardOptimization(long timeSimStart, long timeSimEnd, SimFactory simFactory)
  {
    final int stepMonths = 1;
    final int maxOptMonths = 12 * 50;
    final boolean useWeights = true;

    // Simulator used for tracking walk-forward results.
    Simulation sim = simFactory.build();
    sim.setupRun(null, timeSimStart, TimeLib.TIME_END, "Adaptive.WF");

    long testStart = timeSimStart;
    assert testStart >= sim.getStartMS();
    final long timeFirstAbleToPredict = TimeLib.toMs(TimeLib.toFirstBusinessDayOfMonth(TimeLib.ms2date(
        store.getCommonStartTime()).plusWeeks(53))); // TODO pull requirements from config/predictor
    System.out.printf("First Able to Predict: [%s]\n", TimeLib.formatDate(timeFirstAbleToPredict));

    FeatureExtractor featureExtractor = getFeatureExtractor();
    List<Example> pointExamples = new ArrayList<>();

    // extendPredictionData(timeFirstAbleToPredict, timeSimEnd, featureExtractor, pointExamples);
    // System.out.printf("#Examples: %d\n", pointExamples.size());
    // ClassificationModel absoluteClassifier = ClassificationModel.learnRF(pointExamples, 100, -1, 64);

    // For comparison to non-wf version.
    // PositiveStump stump = new PositiveStump(0, 0.0, 1.0);
    // Predictor adaptive = new AdaptivePredictor(featureExtractor, new ClassificationModel(null, stump),
    // sim.broker.accessObject, assetSymbols);
    // sim.setPredictor(adaptive);

    while (testStart < sim.getEndMS()) {
      // New test period is extends N months beyond test start time.
      final long testEnd = Math.min(
          TimeLib.toPreviousBusinessDay(TimeLib.toMs(TimeLib.ms2date(testStart).plusMonths(stepMonths))),
          sim.getEndMS());

      // Optimization period extends backward from test start time.
      final long optEnd = TimeLib.toPreviousBusinessDay(testStart);
      final long optStart = Math.max(timeFirstAbleToPredict,
          TimeLib.toMs(TimeLib.ms2date(optEnd).minusMonths(maxOptMonths).with(TemporalAdjusters.firstDayOfMonth())));
      // System.out.printf("Optm: [%s] -> [%s]\n", TimeLib.formatDate(optStart), TimeLib.formatDate(optEnd));
      // System.out.printf("Test: [%s] -> [%s]\n", TimeLib.formatDate(testStart), TimeLib.formatDate(testEnd));
      assert optStart < optEnd;
      assert optEnd < testStart;

      // Find best predictor parameters.
      extendPredictionData(optStart, optEnd, featureExtractor, pointExamples);
      reweightExamples(pointExamples);
      // System.out.printf("#Examples: %d\n", pointExamples.size());
      // ClassificationModel absoluteClassifier = ClassificationModel.learnRF(pointExamples, 100, -1, 32);
      int nDims = pointExamples.get(0).x.getNumDims();
      ClassificationModel absoluteClassifier = ClassificationModel.learnStump(pointExamples, -1, useWeights);
      // ClassificationModel absoluteClassifier = ClassificationModel.learnQuadrant(pointExamples, 2, -1, useWeights);
      // ClassificationModel absoluteClassifier = ClassificationModel.learnBaggedQuadrant(pointExamples, 20, 2, 10,
      // useWeights);
      Predictor adaptive = new AdaptivePredictor(featureExtractor, absoluteClassifier, "cash", sim.broker.accessObject,
          assetSymbols);

      // Run the predictor over the test period.
      sim.setPredictor(adaptive);
      sim.runTo(Math.min(testEnd, timeSimEnd));

      // Report results for this test period.
      CumulativeStats stats = CumulativeStats.calc(sim.returnsMonthly);
      System.out.printf("Stats: [%s] -> [%s]: %.2f, %.2f\n", TimeLib.formatDate(testStart),
          TimeLib.formatDate(testEnd), stats.cagr, stats.drawdown);

      // Advance test start time by N months.
      testStart = TimeLib.toMs(TimeLib.ms2date(testStart).plusMonths(stepMonths)
          .with(TemporalAdjusters.firstDayOfMonth()));
    }
    sim.finishRun();
    return sim;
  }

  public static FeatureExtractor getFeatureExtractor()
  {
    FeatureSet features = new FeatureSet();

    // Add monthly momentum features.
    int[] ii = new int[] { 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
    for (int i = 0; i < ii.length; ++i) {
      int x = ii[i];
      Momentum momentum = new Momentum(20, 1, x * 20, (x - 1) * 20, Momentum.ReturnOrMul.Return,
          Momentum.CompoundPeriod.Weekly, FinLib.AdjClose);
      features.add(momentum);
    }

    // // Add weekly momentum features.
    // for (int i = 2; i <= 4; ++i) {
    // Momentum momentum = new Momentum(5, 1, i * 5, (i - 1) * 5, Momentum.ReturnOrMul.Return,
    // Momentum.CompoundPeriod.Weekly, FinLib.AdjClose);
    // features.add(momentum);
    // }
    //
    // // Add daily momentum features.
    // features
    // .add(new Momentum(2, 1, 5, 4, Momentum.ReturnOrMul.Return, Momentum.CompoundPeriod.Weekly, FinLib.AdjClose));

    // features.add(new Momentum(30, 1, 110, 60, Momentum.ReturnOrMul.Return, Momentum.CompoundPeriod.Weekly,
    // FinLib.AdjClose));
    // features.add(new Momentum(40, 1, 100, 80, Momentum.ReturnOrMul.Return, Momentum.CompoundPeriod.Weekly,
    // FinLib.AdjClose));

    // StdDev fe = new StdDev(60, Math.sqrt(252), FinLib.AdjClose);
    // RiskAdjustedReturn fe = new RiskAdjustedReturn(90, 1.0, FinLib.Close);
    // features.add(fe);

    return features;
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
    FeatureExtractor features = getFeatureExtractor();

    RegressionModel model = null;
    List<Example> examples = new ArrayList<>();
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
        FeatureVec fv = features.calculate(broker.accessObject, assetName);
        seq.unlock(key);
        // store.unlock(key);

        String title = String.format("%s [%s]", assetName, TimeLib.formatDate(today));
        fv.setName(title);

        if (today >= timeStartSim) {
          double predictedMul = model.predict(fv);
          double predictedReturn = FinLib.mul2ret(predictedMul);

          FeatureVec scatter = new FeatureVec(fv.getName(), 2, predictedReturn, actualReturn);
          if (stats.size() % 29 == 0) {
            scatterData.addData(scatter, today);
          }
          stats.add(predictedReturn, actualReturn);
        }

        // Add the current week as an example for the future.
        Example example = Example.forRegression(fv, actualMul);
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

  public static void genPredictionData(Sequence guideSeq, List<Example> pointExamples, List<Example> pairExamples,
      File outputDir) throws IOException
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
    FeatureExtractor features = getFeatureExtractor();
    System.out.printf("Features: %d\n", features.size());

    int nGood = 0;
    while (true) {
      final long timePredictStart = TimeLib.toNextBusinessDay(today);
      final long timePredictEnd = TimeLib.toMs(TimeLib.toNextBusinessDay(TimeLib.toLastBusinessDayOfWeek(TimeLib
          .ms2date(today).plusWeeks(1))));
      if (timePredictEnd > guideSeq.getEndMS()) break;

      broker.setNewDay(new TimeInfo(today));
      List<Example> examples = new ArrayList<>();
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
        FeatureVec fv = features.calculate(broker.accessObject, assetName);
        seq.unlock(key);

        // Add the current week as an example for the future.
        int k = (actualReturn > POINTWISE_THRESHOLD ? 1 : 0);
        if (k == 1) {
          ++nGood;
        }
        Example example = Example.forBoth(fv, actualReturn, k);
        // TODO better results with unequal classes
        examples.add(example);
        // System.out.println(example);
      }
      pointExamples.addAll(examples);

      if (GENERATE_PAIRWISE_DATA) {
        for (int i = 0; i < examples.size(); ++i) {
          Example exi = examples.get(i);
          for (int j = i + 1; j < examples.size(); ++j) {
            Example exj = examples.get(j);
            double dy = exj.y - exi.y;
            if (Math.abs(dy) < MIN_PAIRWISE_RETURN_DIFF) continue; // TODO
            FeatureVec dx = exj.x.sub(exi.x);
            int k = (dy > PAIRWISE_THRESHOLD ? 1 : 0);
            Example pairExample = Example.forBoth(dx, dy, k);
            pairExamples.add(pairExample);
            // TODO also add inverse?
            // TODO weighted examples?
          }
        }
      }

      today = TimeLib.toMs(TimeLib.toLastBusinessDayOfWeek(TimeLib.ms2date(today).plusWeeks(1)));
    }
    FeatureVec mean = Example.mean(pointExamples);
    System.out.printf("Pointwise Examples: %d (Good: %d = %.1f%%)\n", pointExamples.size(), nGood, 100.0 * nGood
        / pointExamples.size());
    System.out.printf(" Mean: %s\n", mean);

    if (GENERATE_PAIRWISE_DATA) {
      mean = Example.mean(pairExamples);
      System.out.printf("Pairwise Examples: %d\n", pairExamples.size());
      System.out.printf(" Mean: %s\n", mean);
    }

    if (outputDir != null) {
      Example.saveRegression(pointExamples, new File(outputDir, "point-examples.txt"));
      if (GENERATE_PAIRWISE_DATA) {
        Example.saveRegression(pairExamples, new File(outputDir, "pair-examples.txt"));
      }
    }
  }

  public static void predictRank(List<Example> examples)
  {
    int kFolds = 5;
    double accuracySum = 0.0;
    for (int iFold = 0; iFold < kFolds; ++iFold) {
      List<Example> train = new ArrayList<Example>();
      List<Example> test = new ArrayList<Example>();
      Example.getFold(iFold, kFolds, train, test, examples);
      // RegressionModel model = RegressionModel.learnRF(train, 10, -1, 128);
      ClassificationModel model = ClassificationModel.learnRF(train, 10, -1, 128);

      int nc = 0;
      for (Example example : test) {
        assert example.supportsClassification();
        int k = model.predict(example.x);
        if (k == example.k) ++nc;
      }
      double accuracy = 100.0 * nc / test.size();
      accuracySum += accuracy;
      System.out.printf(" %.3f  (%d / %d)\n", accuracy, nc, test.size());
    }
    accuracySum /= kFolds;
    System.out.printf("Mean: %.3f\n", accuracySum);
  }

  public static void predictAbsolute(List<Example> examples)
  {
    int kFolds = 5;
    double accuracySum = 0.0;
    for (int iFold = 0; iFold < kFolds; ++iFold) {
      List<Example> train = new ArrayList<Example>();
      List<Example> test = new ArrayList<Example>();
      Example.getFold(iFold, kFolds, train, test, examples);
      ClassificationModel model = ClassificationModel.learnRF(train, 10, -1, 128);

      int nc = 0;
      for (Example example : test) {
        assert example.supportsClassification();
        int k = model.predict(example.x);
        // int k = (example.x.get(13) >= 0.0 ? 1 : 0);
        if (k == example.k) ++nc;
      }
      double accuracy = 100.0 * nc / test.size();
      accuracySum += accuracy;
      System.out.printf(" %.3f  (%d / %d)\n", accuracy, nc, test.size());
    }
    accuracySum /= kFolds;
    System.out.printf("Mean: %.3f\n", accuracySum);
  }

  public static void explore(String assetName, File outputDir) throws IOException
  {
    Sequence seq = store.get(assetName);
    System.out.printf("%s: [%s] -> [%s]\n", assetName, TimeLib.formatDate(seq.getStartMS()),
        TimeLib.formatDate(seq.getEndMS()));
    final int N = seq.length();
    Sequence returnsWeekly = new Sequence(assetName + " Weekly Returns");
    int iPrice = FinLib.Close;
    for (int i = 5; i < N; ++i) {
      double p1 = seq.get(i - 5, iPrice);
      double p2 = seq.get(i, iPrice);
      double mul = p2 / p1;
      double r = FinLib.mul2ret(mul);
      returnsWeekly.addData(r, seq.getTimeMS(i));
    }

    ReturnStats stats = ReturnStats.calc(returnsWeekly.getName(), returnsWeekly.extractDim(0));
    System.out.println(" " + stats.toLongString());
    System.out.printf(" Mean up/down: %.2f, %.2f\n", stats.meanUp, stats.meanDown);

    // // Daily returns.
    // Sequence histogram = Histogram.computeHistogram(returnsDaily, -2.0, 2.0, 0.1, 0.0, 0);
    // String[] labels = Histogram.getLabelsFromHistogram(histogram);
    // Chart.saveHighChart(new File(outputDir, "histogram-daily-" + assetName + ".html"), ChartType.Bar,
    // returnsDaily.getName(), labels, null, 1000, 800, Double.NaN, Double.NaN, 1.0, false, false, 2, histogram);
    //
    // // Weekly returns.
    // histogram = Histogram.computeHistogram(returnsWeekly, -2.0, 2.0, 0.1, 0.0, 0);
    // labels = Histogram.getLabelsFromHistogram(histogram);
    // Chart.saveHighChart(new File(outputDir, "histogram-weekly-" + assetName + ".html"), ChartType.Bar,
    // returnsWeekly.getName(), labels, null, 1000, 800, Double.NaN, Double.NaN, 1.0, false, false, 2, histogram);

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
    long commonEnd = TimeLib.calcCommonEnd(seqs);
    commonEnd = TimeLib.toMs(2012, Month.DECEMBER, 31); // TODO
    store.setCommonTimes(commonStart, commonEnd);
    System.out.printf("Common[%d]: [%s] -> [%s]\n", seqs.size(), TimeLib.formatDate(commonStart),
        TimeLib.formatDate(commonEnd));

    long timeSimStart = TimeLib.toMs(TimeLib.ms2date(commonStart).plusWeeks(53 + 51)
        .with(TemporalAdjusters.firstDayOfMonth()));
    // timeSimStart = TimeLib.toMs(1998, Month.MAY, 1); // TODO
    long timeSimEnd = commonEnd; // TimeLib.toMs(2005, Month.DECEMBER, 31); // TODO
    double nSimMonths = TimeLib.monthsBetween(timeSimStart, timeSimEnd);
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
    PriceModel valueModel = PriceModel.adjCloseModel;
    PriceModel quoteModel = new PriceModel(PriceModel.Type.Open, true);
    double startingBalance = 10000.0;
    double monthlyDeposit = 0.0;
    SimFactory simFactory = new SimFactory(store, guideSeq, slippage, 0, startingBalance, monthlyDeposit, valueModel,
        quoteModel);
    Simulation sim = simFactory.build();

    // for (String assetName : assetSymbols) {
    // if (assetName.equals("cash")) continue;
    // explore(assetName, outputDir);
    // }

    // Run simulation for buy-and-hold of individual assets.
    // List<Sequence> symbolReturns = new ArrayList<>();
    // for (int i = 0; i < fundSymbols.length; ++i) {
    // PredictorConfig config = new ConfigConst(fundSymbols[i]);
    // Predictor predictor = config.build(sim.broker.accessObject, assetSymbols);
    // sim.run(predictor, timeSimStart, timeSimEnd, fundSymbols[i]);
    // symbolReturns.add(sim.returnsMonthly);
    // System.out.println(CumulativeStats.calc(sim.returnsMonthly));
    // }
    // Chart.saveLineChart(new File(outputDir, "individual-symbols.html"), "Individual Returns", 1200, 900, true, true,
    // symbolReturns);

    PredictorConfig config;
    Predictor predictor;
    List<Sequence> returns = new ArrayList<>();

    // Lazy 2-fund portfolio.
    String[] assetsLazy2 = new String[] { "VTSMX", "VBMFX" };
    config = new ConfigMixed(new DiscreteDistribution(assetsLazy2, new double[] { 0.7, 0.3 }), new ConfigConst(
        assetsLazy2[0]), new ConfigConst(assetsLazy2[1]));
    Predictor lazy2 = config.build(sim.broker.accessObject, assetsLazy2);
    Sequence returnsLazy2 = sim.run(lazy2, timeSimStart, timeSimEnd, "Lazy2");
    System.out.println(CumulativeStats.calc(returnsLazy2));
    returns.add(returnsLazy2);

    // Lazy 3-fund portfolio.
    // String[] lazy3 = new String[] { "VTSMX", "VBMFX", "VGTSX" };
    // config = new ConfigMixed(new DiscreteDistribution(lazy3, new double[] { 0.34, 0.33, 0.33 }), new ConfigConst(
    // lazy3[0]), new ConfigConst(lazy3[1]), new ConfigConst(lazy3[2]));
    // predictor = config.build(sim.broker.accessObject, lazy3);
    // Sequence returnsLazy3 = sim.run(predictor, timeSimStart, timeSimEnd, "Lazy3");
    // System.out.println(CumulativeStats.calc(returnsLazy3));
    // returns.add(returnsLazy3);

    // Lazy 4-fund portfolio.
    // String[] lazy4 = new String[] { "VTSMX", "VBMFX", "VGSIX", "VGTSX" };
    // // String[] lazy4 = new String[] { "VIIIX", "VEMPX", "VBMPX", "VGSNX", "VTPSX" };
    // config = new ConfigMixed(new DiscreteDistribution(lazy4, new double[] { 0.4, 0.2, 0.1, 0.3 }), new ConfigConst(
    // lazy4[0]), new ConfigConst(lazy4[1]), new ConfigConst(lazy4[2]), new ConfigConst(lazy4[3]));
    // predictor = config.build(sim.broker.accessObject, lazy4);
    // Sequence returnsLazy4 = sim.run(predictor, timeSimStart, timeSimEnd, "Lazy4");
    // System.out.println(CumulativeStats.calc(returnsLazy4));
    // returns.add(returnsLazy4);

    // All stock.
    PredictorConfig stockConfig = new ConfigConst("VTSMX");
    predictor = stockConfig.build(sim.broker.accessObject, assetSymbols);
    Sequence returnsStock = sim.run(predictor, timeSimStart, timeSimEnd, "Stock");
    System.out.println(CumulativeStats.calc(returnsStock));
    returns.add(returnsStock);

    // All bonds.
    PredictorConfig bondConfig = new ConfigConst("VBMFX");
    predictor = bondConfig.build(sim.broker.accessObject, assetSymbols);
    Sequence returnsBonds = sim.run(predictor, timeSimStart, timeSimEnd, "Bonds");
    System.out.println(CumulativeStats.calc(returnsBonds));
    returns.add(returnsBonds);

    // Volatility-Responsive Asset Allocation.
    Predictor volres = new VolResPredictor(60, "VTSMX", "VBMFX", sim.broker.accessObject, FinLib.Close);
    Sequence returnsVolRes = sim.run(volres, timeSimStart, timeSimEnd, "VolRes");
    System.out.println(CumulativeStats.calc(returnsVolRes));
    returns.add(returnsVolRes);
    // Chart.saveHoldings(new File(outputDir, "holdings-volres.html"), sim.holdings, sim.store);

    List<ComparisonStats> compStats = new ArrayList<>();
    // Sequence[] defenders = new Sequence[] { returnsStock, returnsBonds, returnsLazy2, returnsLazy3, returnsLazy4 };
    Sequence[] defenders = new Sequence[] { returnsStock, returnsBonds, returnsLazy2, returnsVolRes };
    for (Sequence ret : defenders) {
      compStats.add(ComparisonStats.calc(ret, 0.5, defenders));
    }

    // PredictorConfig tacticalConfig = new ConfigTactical(FinLib.AdjClose, "SPY", "VTSMX", "VGSIX", "VGTSX", "EWU",
    // "EWG", "EWJ", "VGENX", "WHOSX", "FAGIX", "BUFHX", "VFICX", "FNMIX", "DFGBX", "SGGDX", "VGPMX", "USAGX",
    // "FSPCX", "FSRBX", "FPBFX", "ETGIX", "VBMFX", "cash");

    // PredictorConfig tacticalConfig = new ConfigTactical(FinLib.AdjClose, "VTSMX", "MDY", "VGSIX", "VGTSX", "VGENX",
    // "WHOSX", "VGPMX", "USAGX", "VBMFX", "cash");

    PredictorConfig tacticalConfig = new ConfigTactical(FinLib.Close, "VTSMX", "VBMFX");

    Predictor tactical = tacticalConfig.build(sim.broker.accessObject, assetSymbols);
    sim.run(tactical, timeSimStart, timeSimEnd, "Tactical");
    System.out.println(CumulativeStats.calc(sim.returnsMonthly));
    returns.add(sim.returnsMonthly);
    compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, defenders));

    // Run adaptive asset allocation.
    final TradeFreq tradeFreq = TradeFreq.Weekly;
    final int pctQuantum = 1;
    // PredictorConfig minvarConfig = new ConfigAdaptive(15, 0.9, Weighting.MinVar, 20, 100, 80, 0.7, -1, pctQuantum,
    // tradeFreq, FinLib.AdjClose);
    // PredictorConfig ewConfig2 = new ConfigAdaptive(-1, -1, Weighting.Equal, 30, 110, 60, 0.5, 5, pctQuantum,
    // tradeFreq, FinLib.AdjClose);

    // Adaptive Asset Allocation (Equal Weight).
    // for (int x = 20; x <= 220; x += 20) {
    // System.out.printf("Month: %d\n", x / 20 + 1);
    // config = ConfigAdaptive.buildEqualWeight(20, 120, 100, 0.5, 4, pctQuantum, tradeFreq, FinLib.AdjClose);
    // genPredictionMap(guideSeq, outputDir);

    // List<Example> pointExamples = new ArrayList<>();
    // List<Example> pairExamples = new ArrayList<>();
    // genPredictionData(guideSeq, pointExamples, pairExamples, outputDir);

    // predictRank(pairExamples);
    // predictAbsolute(pointExamples);

    // System.out.printf("Learn absolute classifier...\n");
    // ClassificationModel absoluteClassifier = ClassificationModel.learnRF(pointExamples, 100, -1, 64);
    // ClassificationModel absoluteClassifier = new ClassificationModel(new Stump(10, 0.0, 1.0));
    // // System.out.printf("Learn pairwise classifier...\n");
    // ClassificationModel pairwiseClassifier = null; // ClassificationModel.learnRF(pairExamples, 100, -1, 128);
    // Ranker ranker = null; // new ColleyRanker();
    // FeatureExtractor features = getFeatureExtractor();
    // predictor = new AdaptivePredictor(features, absoluteClassifier, pairwiseClassifier, ranker,
    // sim.broker.accessObject, assetSymbols);
    // sim.run(predictor, timeSimStart, timeSimEnd, "Adaptive");
    // // sim.broker.getAccount(Simulation.AccountName).printTransactions();
    // System.out.println(CumulativeStats.calc(sim.returnsMonthly));
    // returns.add(sim.returnsMonthly);
    // Chart.saveHoldings(new File(outputDir, "holdings-adaptive-prelearn.html"), sim.holdings, sim.store);

    // FeatureExtractor features = getFeatureExtractor();
    // RiskAdjustedReturn fe = new RiskAdjustedReturn(120, 1.0, FinLib.AdjClose);
    FeatureExtractor fe1 = new Momentum(40, 1, 110, 70, Momentum.ReturnOrMul.Return, Momentum.CompoundPeriod.Weekly,
        FinLib.Close);
    FeatureExtractor fe2 = new Momentum(10, 1, 180, 140, Momentum.ReturnOrMul.Return, Momentum.CompoundPeriod.Weekly,
        FinLib.Close);
    FeatureExtractor fe3 = new Momentum(40, 1, 220, 180, Momentum.ReturnOrMul.Return, Momentum.CompoundPeriod.Weekly,
        FinLib.Close);
    Stump stump = new Stump(0, 0.0, false, 5.0);
    String safeAsset = "VBMFX";
    Predictor predictor1 = new AdaptivePredictor(fe1, stump, safeAsset, sim.broker.accessObject, assetSymbols);
    Predictor predictor2 = new AdaptivePredictor(fe2, stump, safeAsset, sim.broker.accessObject, assetSymbols);
    Predictor predictor3 = new AdaptivePredictor(fe3, stump, safeAsset, sim.broker.accessObject, assetSymbols);
    Predictor[] predictors = new Predictor[] { predictor1, predictor2, predictor3, tactical };
    DiscreteDistribution distribution = new DiscreteDistribution(0.2, 0.2, 0.3, 0.3);
    // DiscreteDistribution distribution = new DiscreteDistribution(0.2, 0.5, 0.3, 0.0);
    predictor = new MixedPredictor(predictors, distribution, sim.broker.accessObject, assetSymbols);

    sim.run(predictor1, timeSimStart, timeSimEnd, fe1.name);
    System.out.println(CumulativeStats.calc(sim.returnsMonthly));
    // returns.add(sim.returnsMonthly);
    compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, defenders));

    sim.run(predictor2, timeSimStart, timeSimEnd, fe2.name);
    System.out.println(CumulativeStats.calc(sim.returnsMonthly));
    // returns.add(sim.returnsMonthly);
    compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, defenders));

    sim.run(predictor3, timeSimStart, timeSimEnd, fe3.name);
    System.out.println(CumulativeStats.calc(sim.returnsMonthly));
    // returns.add(sim.returnsMonthly);
    compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, defenders));

    sim.run(predictor, timeSimStart, timeSimEnd, "Mixed");
    System.out.println(CumulativeStats.calc(sim.returnsMonthly));
    returns.add(sim.returnsMonthly);
    Chart.saveHoldings(new File(outputDir, "holdings.html"), sim.holdings, sim.store);
    // sim.broker.getAccount(Simulation.AccountName).printTransactions();
    compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, defenders));

    // PredictorConfig equalWeightConfig1 = ConfigAdaptive.buildEqualWeight(40, 100, 80, 0.5, 4, pctQuantum, tradeFreq,
    // FinLib.AdjClose);
    // PredictorConfig equalWeightConfig2 = ConfigAdaptive.buildEqualWeight(20, 120, 100, 0.5, 4, pctQuantum, tradeFreq,
    // FinLib.AdjClose);
    // predictor = equalWeightConfig2.build(sim.broker.accessObject, assetSymbols);
    // // // config = new ConfigMixed(new DiscreteDistribution(0.5, 0.5), equalWeightConfig1, equalWeightConfig2);
    // // // predictor = config.build(sim.broker.accessObject, assetSymbols);
    // sim.run(predictor, timeSimStart, timeSimEnd, "Adaptive-6mo");// -40/80/100");
    // // System.out.printf("Days: %d\n", sim.days.size());
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
    // Sequence ret = sim.run(predictor, timeSimStart, timeSimEnd, name);
    // returns.add(ret);
    // System.out.println(CumulativeStats.calc(ret));
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
    // Sequence ret = sim.run(predictor, timeSimStart, timeSimEnd, name);
    // returns.add(ret);
    // // compStats.add(ComparisonStats.calc(ret, 0.5, defenders));
    // System.out.println(CumulativeStats.calc(ret));
    // }

    // Simulation wfSim = walkForwardOptimization(timeSimStart, timeSimEnd, simFactory);
    // // System.out.printf("Days: %d\n", wfSim.days.size());
    // System.out.println(CumulativeStats.calc(wfSim.returnsMonthly));
    // returns.add(wfSim.returnsMonthly);
    // Chart.saveHoldings(new File(outputDir, "holdings-adaptive-wf.html"), wfSim.holdings, wfSim.store);

    // AdaptiveScanner scanner = new AdaptiveScanner();
    // List<CumulativeStats> cstats = new ArrayList<CumulativeStats>();
    // while (true) {
    // config = scanner.get();
    // if (config == null) break;
    // // System.out.println(config);
    // predictor = config.build(sim.broker.accessObject, assetSymbols);
    // Sequence ret = sim.run(predictor, timeSimStart, timeSimEnd, config.toString());
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
    Chart.saveComparisonTable(new File(outputDir, "comparison.html"), 1000, compStats);

    // Account account = sim.broker.getAccount(0);
    // account.printTransactions();//TimeLib.TIME_BEGIN, TimeLib.toMs(1996, Month.DECEMBER, 31));
  }
}
