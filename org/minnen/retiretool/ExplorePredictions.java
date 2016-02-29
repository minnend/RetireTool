package org.minnen.retiretool;

import java.io.File;
import java.io.IOException;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.minnen.retiretool.broker.Broker;
import org.minnen.retiretool.broker.PriceModel;
import org.minnen.retiretool.broker.TimeInfo;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.ml.Example;
import org.minnen.retiretool.predictor.features.FeatureExtractor;
import org.minnen.retiretool.predictor.features.Momentum;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;

public class ExplorePredictions
{
  public static final SequenceStore store        = new SequenceStore();

  // These symbols go back to 13 May 1996.
  // public static final String[] fundSymbols = new String[] { "SPY", "VTSMX", "VBMFX", "VGSIX",
  // "VGTSX", "EWU", "EWG", "EWJ", "VGENX", "WHOSX", "FAGIX", "BUFHX", "VFICX", "FNMIX", "DFGBX", "SGGDX", "VGPMX",
  // "USAGX", "FSPCX", "FSRBX", "FPBFX", "ETGIX", "VDIGX", "MDY", "VBINX", "^IXIC" };

  // public static final String[] fundSymbols = new String[] { "VTSMX", "VBMFX", "VGSIX", "VGTSX",
  // "VGENX", "WHOSX", "USAGX" };

  // public static final String[] fundSymbols = new String[] { "VTSMX", "VBMFX" };

  // These symbols go back to 27 April 1992.
  // public static final String[] fundSymbols = new String[] { "VTSMX", "VBMFX", "VGENX", "WHOSX",
  // "FAGIX", "DFGBX", "VGPMX", "USAGX", "FSPCX", "FSRBX", "FPBFX" };

  // public static final String[] fundSymbols = new String[] { "VTSMX", "VBMFX", "VGENX", "VGPMX", "FPBFX" };
  public static final String[]      fundSymbols  = new String[] { "VTSMX" };

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

  public static void genPredictionData(Sequence guideSeq, List<Example> pointExamples, File outputDir)
      throws IOException
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
    FeatureExtractor features = new Momentum(20, 1, 120, 100, Momentum.ReturnOrMul.Return,
        Momentum.CompoundPeriod.Weekly, FinLib.Close);

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

        String title = String.format("%s [%s]", assetName, TimeLib.formatDate(today));
        fv.setName(title);

        // Add the current week as an example for the future.
        int k = (actualReturn > 0.0 ? 1 : 0);
        if (k == 1) {
          ++nGood;
        }
        Example example = Example.forBoth(fv, actualReturn, k);
        examples.add(example);
        // System.out.println(example);
      }
      pointExamples.addAll(examples);

      today = TimeLib.toMs(TimeLib.toLastBusinessDayOfWeek(TimeLib.ms2date(today).plusWeeks(1)));
    }
    FeatureVec mean = Example.mean(pointExamples);
    System.out.printf("Pointwise Examples: %d (Good: %d = %.1f%%)\n", pointExamples.size(), nGood, 100.0 * nGood
        / pointExamples.size());
    System.out.printf(" Mean: %s\n", mean);

    if (outputDir != null) {
      Example.saveRegression(pointExamples, new File(outputDir, "point-examples.txt"));
    }
  }

  public static double kernelEpanechnikov(double x, double x0, double kw)
  {
    double u = (x - x0) / kw;
    double u2 = u * u;
    if (u2 >= 1.0) return 0.0;
    return 0.75 * (1.0 - u2);
  }

  public static FeatureVec kernelEstimate(double x0, double kw, int iDim, List<Example> examples)
  {
    int nGreater = 0;
    double tr = 1.0;
    double wsum = 0.0;
    double wmean = 0.0;
    List<FeatureVec> support = new ArrayList<>();
    for (Example example : examples) {
      double x = example.x.get(iDim);
      if (x >= x0) {
        ++nGreater;
        tr *= FinLib.ret2mul(example.y);
      }
      double k = kernelEpanechnikov(x, x0, kw);
      if (k > 0.0) {
        wsum += k;
        wmean += k * example.y;
        support.add(new FeatureVec(2, example.y, k));
      }
    }

    double pct5 = 0.0;
    double pct50 = 0.0;
    double pct95 = 0.0;

    if (wsum > 0.0) {
      support.sort(new Comparator<FeatureVec>()
      {
        @Override
        public int compare(FeatureVec a, FeatureVec b)
        {
          double va = a.get(0);
          double vb = b.get(0);
          if (va < vb) return -1;
          if (va > vb) return 1;
          return 0;
        }
      });
      wmean /= wsum;
      double w5 = wsum * 0.05;
      double w50 = wsum * 0.5;

      double cw = 0.0;
      int i = 0;
      for (; i < support.size(); ++i) {
        cw += support.get(i).get(1);
        if (cw >= w5) break;
      }
      pct5 = support.get(i).get(0);

      cw = 0.0;
      i = support.size() - 1;
      for (; i >= 0; --i) {
        cw += support.get(i).get(1);
        if (cw >= w5) break;
      }
      pct95 = support.get(i).get(0);

      cw = 0.0;
      i = support.size() - 1;
      for (; i >= 0; --i) {
        cw += support.get(i).get(1);
        if (cw >= w50) break;
      }
      pct50 = support.get(i).get(0);
    }
    double mr = FinLib.mul2ret(Math.pow(tr, 1.0 / nGreater));
    return new FeatureVec(6, pct5, pct50, pct95, nGreater, tr, mr);
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
    long commonEnd = TimeLib.toMs(2005, Month.DECEMBER, 31); // TODO
    store.setCommonTimes(commonStart, commonEnd);
    System.out.printf("Common[%d]: [%s] -> [%s]\n", seqs.size(), TimeLib.formatDate(commonStart),
        TimeLib.formatDate(commonEnd));

    long timeSimStart = TimeLib.toMs(TimeLib.ms2date(commonStart).plusWeeks(53 + 51)
        .with(TemporalAdjusters.firstDayOfMonth()));
    // long timeSimStart = TimeLib.toMs(2010, Month.JANUARY, 1); // TODO
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

    Sequence guideSeq = store.get(fundSymbols[0]).dup();
    List<Example> examples = new ArrayList<>();
    genPredictionData(guideSeq, examples, outputDir);

    Sequence scatterData = new Sequence();
    for (int i = 0; i < examples.size(); ++i) {
      Example example = examples.get(i);
      FeatureVec scatter = new FeatureVec(example.x.getName(), 2, example.x.get(0), example.y);
      if (i % 1 == 0) {
        scatterData.addData(scatter, example.getTime());
      }
    }

    for (int xi = -100; xi <= 100; xi += 20) {
      double x = xi / 100.0;
      FeatureVec fv = kernelEstimate(x, 0.2, 0, examples);
      System.out.printf("%.2f: %s\n", x, fv);
    }

    Chart.saveScatterPlot(new File(outputDir, "momentum-6.html"), "Predictions", 1000, 1000, 3, new String[] {
        "Predicted", "Actual" }, scatterData);
  }
}
