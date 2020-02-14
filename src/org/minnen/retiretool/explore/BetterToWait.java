package org.minnen.retiretool.explore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.tiingo.TiingoFund;
import org.minnen.retiretool.data.tiingo.TiingoIO;
import org.minnen.retiretool.data.yahoo.YahooIO;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;
import org.minnen.retiretool.viz.PlotLine;

/**
 * Calculate percentage of days that have a close that's lower than all future days. These are the best days to buy in.
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
    TiingoFund fund = TiingoFund.fromSymbol("VFINX", true);
    Sequence data = fund.data;
    System.out.printf("[%s] -> [%s]\n", TimeLib.formatDate(data.getStartMS()), TimeLib.formatDate(data.getEndMS()));
    System.out.printf("dims: %d\n", data.getNumDims());
    Sequence priceSeq = data.extractDims(FinLib.AdjClose);
    double[] prices = priceSeq.extractDim(0);

    long a = TimeLib.getTime();
    int nBuy = 0;
    int nWait = 0;
    int nMinBuybackWait = 0;
    int nMaxBuybackWait = -1; // 52 * 5;
    List<PlotLine> lines = new ArrayList<>();
    for (int i = 0; i < prices.length - nMinBuybackWait - 1; ++i) {
      assert i + nMinBuybackWait + 1 < prices.length;
      boolean foundLower = false;

      int end = prices.length;
      if (nMaxBuybackWait > 0) {
        end = Math.min(end, i + nMaxBuybackWait + 1);
      }
      for (int j = i + nMinBuybackWait + 1; j < end; ++j) {
        if (prices[j] <= prices[i]) {
          foundLower = true;
          break;
        }
      }
      if (foundLower) {
        ++nWait;
      } else {
        ++nBuy;
        lines.add(new PlotLine(i, 0.5, "rgba(0,200,90,0.2)"));
        // System.out.println(TimeLib.formatDate2(priceSeq.getTimeMS(i)));
      }
    }
    long b = TimeLib.getTime();
    System.out.printf("%dms\n", b - a);

    System.out.printf("Wait: %5d  %.1f%%\n", nWait, 100.0 * nWait / (nWait + nBuy));
    System.out.printf(" Buy: %5d  %.1f%%\n", nBuy, 100.0 * nBuy / (nWait + nBuy));

    ChartConfig config = Chart.saveLineChart(new File(DataIO.getOutputPath(), "snp.html"), null, "1800px", "800px",
        ChartScaling.LOGARITHMIC, ChartTiming.DAILY, priceSeq);
    config.addPlotLineX(lines);
    Chart.saveChart(config);
  }
}
