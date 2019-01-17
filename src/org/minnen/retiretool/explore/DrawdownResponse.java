package org.minnen.retiretool.explore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.ArrayUtils;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.yahoo.YahooIO;
import org.minnen.retiretool.stats.ReturnStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

public class DrawdownResponse
{
  public final static SequenceStore store = new SequenceStore();

  private static void setupData() throws IOException
  {
    String symbol = "^GSPC";
    File file = YahooIO.downloadDailyData(symbol, 8 * TimeLib.MS_IN_HOUR);
    Sequence stock = YahooIO.loadData(file);

    System.out.printf("S&P (Daily): [%s] -> [%s]\n", TimeLib.formatDate(stock.getStartMS()),
        TimeLib.formatDate(stock.getEndMS()));
    store.add(stock, "stock");
  }

  private static double calcDrawdown(double peak, double[] prices, int iStart)
  {
    double bottom = prices[iStart];
    for (int t = iStart + 1; t < prices.length; ++t) {
      double price = prices[t];
      if (price >= peak) break; // returned to (or exceeded) original price => no more DD
      if (price < bottom) bottom = price; // new low
    }
    return 100.0 * (peak - bottom) / peak;
  }

  public static void main(String[] args) throws IOException
  {
    setupData();

    Sequence stock = store.get("stock");
    Sequence seqDrawdown = new Sequence("Drawdown");
    Sequence seqFutureDD = new Sequence("Future Drawdown");
    int nDown = 0;
    double[] prices = stock.extractDim(FinLib.Close);
    double peak = prices[0];
    for (int t = 1; t < prices.length; ++t) {
      long time = stock.getTimeMS(t);
      double price = prices[t];
      if (price < peak) {
        double drawdown = 100.0 * (peak - price) / peak;
        ++nDown;
        // System.out.printf("%s: %.2f%%\n", TimeLib.formatDate2(x.getTime()), drawdown);
        seqDrawdown.addData(-drawdown, time);
        // TODO only add point if point is a recent low

        double prevMin = Library.min(prices, Math.max(0, t - 20), t - 1);
        if (price < prevMin) {
          double totalDD = calcDrawdown(peak, prices, t);
          FeatureVec p = new FeatureVec(2, drawdown, totalDD - drawdown).setName(TimeLib.formatDate(time));
          seqFutureDD.addData(p, time);
        }
      } else {
        peak = price;
        seqDrawdown.addData(0, time);
      }
    }
    System.out.printf("#down=%d  total=%d  => %.2f%%\n", nDown, stock.length(), 100.0 * nDown / stock.length());

    // Bin extra drawdown based on rounded initial drawdown.
    Map<Integer, List<Double>> ddBins = new TreeMap<>();
    for (FeatureVec x : seqFutureDD) {
      int i = (int) Math.round(x.get(0));
      List<Double> list = null;
      if (!ddBins.containsKey(i)) {
        list = new ArrayList<Double>();
        ddBins.put(i, list);
      } else {
        list = ddBins.get(i);
      }
      list.add(x.get(1));
    }
    for (Map.Entry<Integer, List<Double>> entry : ddBins.entrySet()) {
      int initialDD = entry.getKey();
      double[] extraDD = entry.getValue().stream().mapToDouble(x -> x).toArray();
      ReturnStats stats = ReturnStats.calc("Drawdown " + initialDD, extraDD);
      System.out.printf("%d: %s %d\n", initialDD, stats, stats.count);
    }

    Chart.saveChart(new File(DataIO.getOutputPath(), "drawdown.html"), ChartConfig.Type.Area, "Drawdown", null, null, "100%",
        "700px", Double.NaN, 0, 0, ChartScaling.LINEAR, ChartTiming.DAILY, 0, seqDrawdown);

    Chart.saveScatterPlot(new File(DataIO.getOutputPath(), "future-drawdown.html"), "Future Drawdown", "100%", "900px", 2,
        new String[] { "Curren DD", "Extra DD" }, seqFutureDD);
  }
}
