package org.minnen.retiretool.explore;

import java.io.File;
import java.io.IOException;
import java.time.Month;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.data.Shiller;
import org.minnen.retiretool.data.fred.FredSeries;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.LinearFunc;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

/**
 * Visualize "single greatest predictor of future stock market returns" from ERN:
 * http://www.philosophicaleconomics.com/2013/12/the-single-greatest-predictor-of-future-stock-market-returns/
 * 
 * @author David Minnen
 */
public class Signal10YearReturnPredictorERN
{
  private static Sequence matchTimestamps(Sequence base, Sequence pool)
  {
    Sequence seq = new Sequence(pool.getName());
    for (FeatureVec x : base) {
      int i = pool.getClosestIndex(x.getTime());
      FeatureVec y = pool.get(i).dup();
      if (Math.abs(x.getTime() - y.getTime()) > TimeLib.MS_IN_DAY) break;
      seq.addData(y, x.getTime());
    }
    return seq;
  }

  private static LinearFunc linearFit(Sequence seq, Sequence target)
  {
    final int n = Math.min(seq.length(), target.length());
    double[] x = seq.extractDim(0, 0, n);
    double[] y = target.extractDim(0, 0, n);
    return Library.linearFit(x, y);
  }

  private static Sequence transform(Sequence seq, LinearFunc linearFunc)
  {
    Sequence adjusted = new Sequence(seq.getName() + " (linear transform)");
    for (FeatureVec x : seq) {
      FeatureVec y = x.mul(linearFunc.mul)._add(linearFunc.add);
      adjusted.addData(y, x.getTime());
    }
    return adjusted;
  }

  public static void main(String[] args) throws IOException
  {
    String[] signalNames = new String[] { "NCBEILQ027S", "FBCELLQ027S", "TCMILBSNNCB", "TCMILBSHNO", "FGTCMDODNS",
        "SLGTCMDODNS", "WCMITCMFODNS" };

    FredSeries[] fredSeries = new FredSeries[signalNames.length];
    Sequence[] signals = new Sequence[signalNames.length];
    for (int i = 0; i < signalNames.length; ++i) {
      fredSeries[i] = FredSeries.fromName(signalNames[i]);
      signals[i] = fredSeries[i].data;
      if (signalNames[i].equals("NCBEILQ027S") || signalNames[i].equals("FBCELLQ027S")) {
        signals[i]._div(1000.0);
      }
      assert signals[i].length() == signals[0].length();
    }
    // TODO smarter handling of missing data at beginning of data sequences.
    long commonStart = TimeLib.toMs(1951, 10, 1); // missing data before this point
    long commonEnd = TimeLib.calcCommonEnd(signals);
    for (int i = 0; i < signalNames.length; ++i) {
      signals[i] = signals[i].subseq(commonStart, commonEnd, EndpointBehavior.Closest);
      assert signals[i].length() == signals[0].length();
    }

    for (Sequence signal : signals) {
      System.out.printf("%14s: [%s] -> [%s]\n", signal.getName(), TimeLib.formatDate(signal.getStartMS()),
          TimeLib.formatDate(signal.getEndMS()));
    }

    // Calculate average investor equity allocation percentage;
    Sequence x = signals[0].add(signals[1]);
    Sequence y = x.add(signals[2]).add(signals[3]).add(signals[4]).add(signals[5]).add(signals[6]);
    Sequence indicator = x.div(y)._mul(100.0);
    indicator.setName("Average Investor Equity Allocation (%)");
    indicator.adjustDatesToEndOfQuarter();

    // Load S&P 500 data.
    Sequence snp = Shiller.loadSNP(new File(DataIO.getFinancePath(), "shiller.csv"), FinLib.DividendMethod.MONTHLY);
    snp.adjustDatesToEndOfMonth();
    System.out.printf("Shiller: [%s] -> [%s]\n", TimeLib.formatDate(snp.getStartMS()),
        TimeLib.formatDate(snp.getEndMS()));

    // Calculate 10-year future returns for S&P.
    Sequence forwardReturns = FinLib.calcReturnsForMonths(snp, 10 * 12);
    System.out.printf("Forward Returns: [%s] -> [%s]\n", TimeLib.formatDate(forwardReturns.getStartMS()),
        TimeLib.formatDate(forwardReturns.getEndMS()));

    // Get forward returns that match the indicator timestamps.
    forwardReturns = matchTimestamps(indicator, forwardReturns);
    System.out.printf("       Adjusted: [%s] -> [%s]\n", TimeLib.formatDate(forwardReturns.getStartMS()),
        TimeLib.formatDate(forwardReturns.getEndMS()));
    forwardReturns.setName("S&P 10-year Forward Returns");

    LinearFunc linearFunc = linearFit(indicator, forwardReturns);
    System.out.println(linearFunc);
    Sequence adjustedIndicator = transform(indicator, linearFunc);
    adjustedIndicator.setName("Prediction (Linear Fit)");

    double corr = Library.correlation(adjustedIndicator.subseq(0, forwardReturns.size()).extractDim(0),
        forwardReturns.extractDim(0));
    System.out.printf("Corr=%f\n", corr);

    File file = new File(DataIO.getOutputPath(), "return-prediction.html");
    Chart.saveLineChart(file, "10-Year Return Prediction", "100%", "600px", ChartScaling.LINEAR, ChartTiming.MONTHLY,
        indicator, forwardReturns, adjustedIndicator);

    file = new File(DataIO.getOutputPath(), "return-predictor-signals.html");
    Chart.saveLineChart(file, "Return Predictor Signals", "100%", "600px", ChartScaling.LINEAR, ChartTiming.MONTHLY, signals);
  }
}
