package org.minnen.retiretool.explore;

import java.io.File;
import java.io.IOException;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.TiingoIO;
import org.minnen.retiretool.data.YahooIO;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.Chart.ChartScaling;
import org.minnen.retiretool.viz.Chart.ChartTiming;

/**
 * Calculate what percentage of days has a close that's lower than all future days. These are the best days to buy in.
 * For all other days, you could wait and get a better price later.
 * 
 * @author David Minnen
 */
public class BetterToWait
{
  public static void main(String[] args) throws IOException
  {
    // File file = YahooIO.downloadDailyData("^GSPC", 8 * TimeLib.MS_IN_HOUR);
    // Sequence data = YahooIO.loadData(file);
    Sequence data = TiingoIO.loadEodData("VFINX");
    System.out.printf("[%s] -> [%s]\n", TimeLib.formatDate(data.getStartMS()), TimeLib.formatDate(data.getEndMS()));
    Sequence priceSeq = data.extractDims(FinLib.AdjClose);
    double[] prices = priceSeq.extractDim(0);

    int nBuy = 0;
    int nWait = 0;
    double[] smallestAfter = new double[prices.length];
    smallestAfter[prices.length - 1] = Double.POSITIVE_INFINITY;
    for (int i = prices.length - 2; i >= 0; --i) {
      smallestAfter[i] = Math.min(prices[i + 1], smallestAfter[i + 1]);
      if (prices[i] >= smallestAfter[i]) {
        ++nWait;
      } else {
        ++nBuy;
        // System.out.println(TimeLib.formatDate2(priceSeq.getTimeMS(i)));
      }
    }

    System.out.printf("Wait: %5d  %.1f%%\n", nWait, 100.0 * nWait / (nWait + nBuy));
    System.out.printf(" Buy: %5d  %.1f%%\n", nBuy, 100.0 * nBuy / (nWait + nBuy));

    Chart.saveLineChart(new File(DataIO.outputPath, "snp.html"), "Price", 1800, 800, ChartScaling.LOGARITHMIC,
        ChartTiming.DAILY, priceSeq);
  }
}
