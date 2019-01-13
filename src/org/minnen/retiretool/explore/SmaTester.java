package org.minnen.retiretool.explore;

import java.io.File;
import java.io.IOException;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;
import org.minnen.retiretool.viz.PlotBand;

/** Simple visualization / testbed for SMA-based strategies. */
public class SmaTester
{
  public final static SequenceStore store    = new SequenceStore();

  public static final long          gap      = 2 * TimeLib.MS_IN_DAY;

  // public static final String symbol = "^GSPC";
  public static final String        symbol   = "GOOG";

  public static final String        safeName = "3-month-treasuries";

  public static void main(String[] args) throws IOException
  {
    Sequence risky = DataIO.loadSymbol(symbol);
    risky = risky.extractDims(FinLib.AdjClose);
    // store.add(risky);
    System.out.printf("%s: [%s] -> [%s]\n", risky.getName(), TimeLib.formatDate(risky.getStartMS()),
        TimeLib.formatDate(risky.getEndMS()));

    // Sequence safe = FinLib.inferAssetFrom3MonthTreasuries();
    // store.add(safe, safeName);
    // System.out.printf("%s: [%s] -> [%s]\n", safe.getName(), TimeLib.formatDate(safe.getStartMS()),
    // TimeLib.formatDate(safe.getEndMS()));

    final int margin = 1;
    ConfigSMA config = new ConfigSMA(50, 0, 200, 0, margin, 0, gap);
    System.out.printf("Strategy (%s): %s\n", config.getClass().getSimpleName(), config);

    // Generate graph.
    Sequence trigger = FinLib.sma(risky, config.nLookbackTriggerA, config.nLookbackTriggerB, config.iPrice);
    Sequence base = FinLib.sma(risky, config.nLookbackBaseA, config.nLookbackBaseB, config.iPrice);
    Sequence raw = risky.dup();
    trigger.setName(String.format("Trigger[%d]", config.nLookbackTriggerA));
    base.setName(String.format("Base[%d]", config.nLookbackBaseA));

    File file = new File(DataIO.getOutputPath(),
        String.format("sma-%d-%d.html", config.nLookbackTriggerA, config.nLookbackBaseA));
    String title = String.format("%s - SMA[%d,%d]", symbol, config.nLookbackTriggerA, config.nLookbackBaseA);

    ChartConfig chart = ChartConfig.buildLine(file, title, "100%", "600px", ChartScaling.LINEAR, ChartTiming.DAILY, trigger,
        base, raw);

    assert trigger.length() == base.length();
    int prevLoc = 0;
    int prevIndex = -1;
    for (int t = 0; t < trigger.length(); ++t) {
      int reloc = trigger.get(t, 0) >= base.get(t, 0) ? 1 : -1;
      if (reloc != prevLoc) {
        if (reloc < 0) {
          prevIndex = t;
        } else if (prevLoc != 0) {
          long prevTime = raw.getTimeMS(prevIndex);
          double prevPrice = raw.get(prevIndex, 0);
          double price = raw.get(t, 0);
          double ret = FinLib.mul2ret(price / prevPrice);
          System.out.printf("[%s] %.2f -> %.2f = %.2f\n", TimeLib.formatDate2(prevTime), prevPrice, price, ret);
          String color;
          if (ret > 2) color = "rgba(255,0,0,0.1)"; // rebuy higher
          else if (ret < -2) color = "rgba(0,255,0,0.1)"; // rebuy lower
          else color = "rgba(0,0,0,0.05)"; // rebuy about the same
          chart.addPlotBandX(new PlotBand(prevIndex, t, color));
        }
      }
      prevLoc = reloc;
    }

    Chart.saveChart(chart);
  }
}
