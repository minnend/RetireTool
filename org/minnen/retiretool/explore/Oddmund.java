package org.minnen.retiretool.explore;

import java.io.File;
import java.io.IOException;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.broker.SimFactory;
import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.YahooIO;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.PriceModel;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.Chart.ChartScaling;
import org.minnen.retiretool.viz.Chart.ChartTiming;

/**
 * Test strategies from the quantifiedstrategies.com blog. http://www.quantifiedstrategies.com/category/strategies/
 */
public class Oddmund
{
  public static void main(String[] args) throws IOException
  {
    File outputDir = new File("g:/web");

    // Make sure we have the latest data.
    String symbol = "SPY";
    YahooIO.updateDailyData(symbol, 8 * TimeLib.MS_IN_HOUR);
    Sequence rawPrices = YahooIO.loadData(YahooIO.getFile(symbol));

    long commonStart = TimeLib.toMs(1995, Month.JANUARY, 1);
    long commonEnd = TimeLib.toMs(2015, Month.DECEMBER, 31);
    rawPrices = rawPrices.subseq(commonStart, commonEnd, EndpointBehavior.Inside);

    // Build adjusted sequence.
    Sequence prices = new Sequence(rawPrices.getName());
    for (int i = 0; i < rawPrices.length(); ++i) {
      FeatureVec raw = rawPrices.get(i);
      prices.addData(PriceModel.adjust(raw));
    }
    List<Sequence> returns = new ArrayList<>();
    Sequence seq = prices.extractDims(FinLib.Close);
    returns.add(seq._div(seq.getFirst(0)));

    // Setup simulation.
    double slippage = 0.01;
    double balance = 10000.0;// prices.getFirst(FinLib.Close);
    int nDays = 3;
    seq = new Sequence(nDays + "DaysDown");
    for (int t = 0; t < prices.length() - 1; ++t) {
      if (t >= nDays) {
        int nDown = 0;
        for (int i = t - 2; i <= t; ++i) {
          double p1 = prices.get(i - 1, FinLib.Close);
          double p2 = prices.get(i, FinLib.Close);
          if (p2 < p1) ++nDown;
          else break;
        }
        if (nDown == nDays) {
          double prev = balance;
          double buy = prices.get(t, FinLib.Close);
          double sell = prices.get(t + 1, FinLib.Open);
          balance *= (sell - slippage) / (buy + slippage);
        }
      }
      seq.addData(new FeatureVec(1, balance), prices.getTimeMS(t));
    }
    seq._div(seq.getFirst(0));
    returns.add(seq);

    Chart.saveLineChart(new File(outputDir, "returns-oddmund.html"), "Returns", 1000, 640, ChartScaling.LOGARITHMIC,
        ChartTiming.DAILY, returns);
  }
}
