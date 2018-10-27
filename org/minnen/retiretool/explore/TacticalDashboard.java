package org.minnen.retiretool.explore;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.ArrayUtils;
import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.YahooIO;
import org.minnen.retiretool.data.tiingo.TiingoFund;
import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.ConfigMulti;
import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.MultiPredictor;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.predictor.daily.TimeCode;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.LinearFunc;
import org.minnen.retiretool.util.PriceModel;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.Writer;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.Chart.ChartScaling;
import org.minnen.retiretool.viz.Chart.ChartTiming;

public class TacticalDashboard
{
  public final static SequenceStore     store         = new SequenceStore();

  /** Dimension to use in the price sequence for SMA predictions. */
  public static int                     iPriceSMA     = 0;

  public static final Slippage          slippage      = Slippage.None;
  public static final LinearFunc        PriceSDev     = LinearFunc.Zero;

  public static final int               maxDelay      = 0;
  public static final long              gap           = 2 * TimeLib.MS_IN_DAY;
  public static final PriceModel        priceModel    = PriceModel.adjCloseModel;

  public static final String            riskyName     = "stock";
  public static final String            safeName      = "3-month-treasuries";
  public static final String[]          assetNames    = new String[] { riskyName, safeName };

  // public static final PredictorConfig[] singleConfigs = new PredictorConfig[] {
  // new ConfigSMA(20, 0, 240, 150, 0.25, FinLib.Close, gap), new ConfigSMA(50, 0, 180, 30, 1.0, FinLib.Close, gap),
  // new ConfigSMA(10, 0, 220, 0, 2.0, FinLib.Close, gap) };
  // public static final int[][] allParams = new int[][] { { 20, 0, 240, 150, 25 }, { 50, 0, 180, 30, 100 },
  // { 10, 0, 220, 0, 200 } };

  // PredictorConfig[] singleConfigs = new PredictorConfig[] { new ConfigSMA(20, 0, 240, 150, 0.25, FinLib.Close, gap),
  // new ConfigSMA(20, 0, 250, 50, 1.0, FinLib.Close, gap), new ConfigSMA(50, 0, 180, 30, 1.0, FinLib.Close, gap),
  // new ConfigSMA(10, 0, 220, 0, 2.0, FinLib.Close, gap), new ConfigSMA(10, 0, 230, 40, 2.0, FinLib.Close, gap) };

  public static final PredictorConfig[] singleConfigs = new PredictorConfig[] {
      new ConfigSMA(20, 0, 240, 150, 0.25, FinLib.Close, gap), new ConfigSMA(25, 0, 155, 125, 0.75, FinLib.Close, gap),
      new ConfigSMA(5, 0, 165, 5, 0.5, FinLib.Close, gap) };
  public static final int[][]           allParams     = new int[][] { { 20, 0, 240, 150, 25 }, { 25, 0, 155, 125, 75 },
      { 5, 0, 165, 5, 50 } };

  public static void setupData() throws IOException
  {
    String symbol = "^GSPC";
    Sequence stock = null;

    if (symbol == "^GSPC") {
      File file = YahooIO.downloadDailyData(symbol, 8 * TimeLib.MS_IN_HOUR);
      stock = YahooIO.loadData(file);
    } else {
      TiingoFund fund = TiingoFund.fromSymbol(symbol, true);
      stock = fund.data;
    }

    System.out.printf("S&P (Daily): [%s] -> [%s]\n", TimeLib.formatDate(stock.getStartMS()),
        TimeLib.formatDate(stock.getEndMS()));
    store.add(stock, "stock");

    Sequence tb3mo = FinLib.inferAssetFrom3MonthTreasuries();
    store.add(tb3mo, "3-month-treasuries");
  }

  private static String genCodeStatsHtml(Map<Integer, List<Double>> returnsByCode, int currentCode)
  {
    StringWriter sw = new StringWriter();
    try (Writer writer = new Writer(sw)) {
      writer.write("<table id=\"decadeComparisonTable\" cellspacing=\"0\"><thead>\n");
      writer.write(
          "<tr><th>Code</th><th>Count</th><th>Total<br/>Return</th><th>Win<br/>Percent</th><th>Min</th><th>Mean</th><th>Max</th>\n");
      writer.write("</thead><tbody>\n");

      int iRow = 0;
      for (Map.Entry<Integer, List<Double>> entry : returnsByCode.entrySet()) {
        double[] returns = ArrayUtils.toPrimitive(entry.getValue().toArray(new Double[entry.getValue().size()]));
        Arrays.sort(returns);
        final int n = returns.length;
        double total = 1.0;
        int nLose = 0;
        for (double r : returns) {
          total *= r;
          if (r < 1.0) ++nLose;
        }
        String className = (iRow % 2 == 0 ? "evenRow" : "oddRow");
        if (entry.getKey() == currentCode) {
          className += "Bold";
        }
        writer.write(
            " <tr class=\"%s\"><td>%d</td><td>%d</td><td>%.2f</td><td>%.1f</td><td>%.2f</td><td>%.2f</td><td>%.2f</td></tr>\n",
            className, entry.getKey(), returns.length, FinLib.mul2ret(total), 100.0 - 100.0 * nLose / n,
            FinLib.mul2ret(returns[0]), FinLib.mul2ret(Library.mean(returns)), FinLib.mul2ret(returns[n - 1]));

        ++iRow;
      }
      writer.write("</tbody>\n</table>\n");
    } catch (IOException e) {}

    return sw.toString();
  }

  public static void runMulti3(Simulation sim, File dir) throws IOException
  {
    // Buy-and-Hold 100% stock.
    PredictorConfig configRisky = new ConfigConst(riskyName);
    Predictor predRisky = configRisky.build(sim.broker.accessObject, assetNames);
    sim.run(predRisky, "Buy & Hold");
    Sequence baselineReturns = sim.returnsDaily;
    Sequence m1 = sim.returnsMonthly;
    CumulativeStats stats = CumulativeStats.calc(sim.returnsMonthly);
    System.out.println(stats);

    // Multi-predictor to make final decisions.
    PredictorConfig configStrategy = new ConfigMulti(254, singleConfigs);
    MultiPredictor predStrategy = (MultiPredictor) configStrategy.build(sim.broker.accessObject, assetNames);
    sim.run(predStrategy, "Tactical");
    Sequence strategyReturns = sim.returnsDaily;
    Sequence m2 = sim.returnsMonthly;
    assert strategyReturns.matches(baselineReturns);

    stats = CumulativeStats.calc(sim.returnsMonthly);
    System.out.println(stats);

    final String green = "1E2";
    final String red = "D12";
    final String sRowGap = "<td class=\"hgap\">&nbsp;</td>";

    try (Writer f = new Writer(new File(dir, "dashboard.html"))) {
      f.write("<html><head>\n");
      f.write("<title>Dashboard</title>\n");
      f.write("<script src=\"http://code.jquery.com/jquery.min.js\"></script>\n");
      f.write("<link rel=\"stylesheet\" href=\"css/dashboard.css\">\n");
      f.write("</head><body>\n");

      // Table is Column 1.
      f.write("<div class=\"column\">\n");
      f.write("<table cellspacing=\"0\">\n");
      List<TimeCode> exitDates = new ArrayList<>();
      List<TimeCode> reenterDates = new ArrayList<>();
      Map<Integer, List<Double>> returnsByCode = new TreeMap<>();
      int nCodes = predStrategy.timeCodes.size();
      for (int iCode = nCodes - 1; iCode >= 0; --iCode) {
        TimeCode timeCodeSingles = predStrategy.timeCodes.get(iCode);

        long prevTime = baselineReturns.getStartMS();
        if (iCode > 0) {
          prevTime = predStrategy.timeCodes.get(iCode - 1).time;
        }

        long nextTime = baselineReturns.getEndMS();
        if (iCode < predStrategy.timeCodes.size() - 1) {
          nextTime = predStrategy.timeCodes.get(iCode + 1).time;
        }

        // Date column.
        f.write("<tr class=\"%s\"><td class=\"history\">%s</td>", iCode % 2 == 0 ? "evenRow" : "oddRow",
            TimeLib.formatDate(timeCodeSingles.time));
        f.write(sRowGap);

        // Single SMA predictors.
        for (int i = 0; i < singleConfigs.length; ++i) {
          int mask = 1 << (singleConfigs.length - i - 1);
          boolean b = ((timeCodeSingles.code & mask) != 0);
          f.write("<td><div class=\"color\" style=\"background: #%s;\">&nbsp;</div></td>", b ? green : red);
        }

        // Final decision.
        int iTopCode = TimeCode.indexForTime(timeCodeSingles.time, predStrategy.timeCodes);
        assert iTopCode == iCode;
        int iPrevTop = TimeCode.indexForTime(prevTime, predStrategy.timeCodes);
        assert iPrevTop <= iTopCode;
        TimeCode timeCodeStrategy = predStrategy.timeCodes.get(iTopCode);
        TimeCode prevCodeStrategy = predStrategy.timeCodes.get(iPrevTop);

        f.write(sRowGap);
        boolean b = (timeCodeStrategy.code != 0);
        f.write("<td><div class=\"color\" style=\"background: #%s;\">&nbsp;</div></td>", b ? green : red);
        // Return for this time period (single change).
        int index1 = baselineReturns.getClosestIndex(timeCodeSingles.time);
        int index2 = baselineReturns.getClosestIndex(nextTime);
        double totalMul = FinLib.getTotalReturn(baselineReturns, index1, index2);
        // System.out.printf("%d = %f\n", timeCodeSingles.code, totalMul);
        List<Double> returns = returnsByCode.get(timeCodeSingles.code);
        if (returns == null) {
          returns = new ArrayList<Double>();
          returnsByCode.put(timeCodeSingles.code, returns);
        }
        returns.add(totalMul);
        f.write("<td>%.2f</td>", FinLib.mul2ret(totalMul));

        // Return for this time period (top-level change).
        boolean b1 = (timeCodeStrategy.code != 0);
        boolean b2 = (prevCodeStrategy.code != 0);
        boolean bTopChange = (iCode == 0 || iCode == predStrategy.timeCodes.size() - 1 || (b1 ^ b2));

        if (bTopChange && !b2) {
          exitDates.add(prevCodeStrategy);
          reenterDates.add(timeCodeStrategy);
        }

        int iNextTop = iTopCode + 1;
        for (; iNextTop < predStrategy.timeCodes.size(); ++iNextTop) {
          boolean ba = (timeCodeStrategy.code != 0);
          boolean bb = (predStrategy.timeCodes.get(iNextTop).code != 0);
          if (bb != ba) break;
        }
        long nextTopTime = (iNextTop >= predStrategy.timeCodes.size() ? baselineReturns.getEndMS()
            : predStrategy.timeCodes.get(iNextTop).time);
        index2 = baselineReturns.getClosestIndex(nextTopTime);
        totalMul = FinLib.getTotalReturn(baselineReturns, index1, index2);
        f.write(sRowGap);
        f.write("<td>%s</td>", bTopChange ? String.format("%.2f", FinLib.mul2ret(totalMul)) : "");
        f.write(sRowGap);
        f.write("</tr>\n");
      }
      f.write("</table></div>\n");

      // Start Column 2.
      f.write("<div class=\"column\">\n");
      f.write("<b>Dates Covered:</b> [%s] &rarr; [%s]<br/><br/>\n", TimeLib.formatDate(sim.getStartMS()),
          TimeLib.formatDate(sim.getEndMS()));
      f.write("<b>Last Updated:</b> %s<br/><br/>\n", TimeLib.formatTime(TimeLib.getTime(), ZoneId.systemDefault()));
      f.write("<b>Column Legend</b><br/>\n");
      f.write("<ol style=\"margin-top: 4px\">\n");
      f.write("<li>Date of event\n");
      f.write("<li>Vote for each of the three SMA predictors\n");
      f.write("<li>Trade decision (combined vote; <font color=\"#%s\">"
          + "Green</font>=S&amp;P, <font color=\"#%s\">Red</font>=Cash)\n", green, red);
      f.write("<li>Price change between events\n");
      f.write("<li>Price change between trades\n");
      f.write("</ol>\n");
      f.write("<p>\n");
      f.write("[<a href=\"sma1.html\">SMA-1</a>]\n");
      f.write("[<a href=\"sma2.html\">SMA-2</a>]\n");
      f.write("[<a href=\"sma3.html\">SMA-3</a>]\n");
      f.write("</p>\n");

      f.write(Chart.genDecadeTable(m2, m1) + "<br/>");

      // List of dates when the strategy moves to cash.
      // f.write("<b>Move to Safe Asset</b>: " + exitDates.size() + "<br/>\n");
      f.write(TacticalDashboard.genCodeStatsHtml(returnsByCode, predStrategy.timeCodes.get(nCodes - 1).code));

      // f.write("<ul>\n");
      // for (int i = 0; i < exitDates.size(); ++i) {
      // TimeCode a = exitDates.get(i);
      // TimeCode b = reenterDates.get(i);
      // f.write("<li>[%s] &rarr; [%s] (%s)</li>\n", TimeLib.formatDate2(a.time), TimeLib.formatDate2(b.time),
      // TimeLib.formatDuration(b.time - a.time));
      //
      // // Sequence seq = store.get(riskyName).subseq(a.time, b.time);
      // // Chart.saveLineChart(
      // // new File(DataIO.outputPath, String.format("exit-%02d-%s.html", i + 1, TimeLib.formatYMD(a.time))),
      // // String.format("Exit %d", i + 1), 1200, 600, false, false, seq);
      // }
      // f.write("</ul>\n");
      f.write("</div>\n");

      f.write("</body></html>\n");
    }
  }

  public static void main(String[] args) throws IOException
  {
    setupData();

    Sequence stock = store.get(riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 365 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    Simulation sim = new Simulation(store, guideSeq, slippage, maxDelay, priceModel, priceModel);
    runMulti3(sim, DataIO.outputPath);

    // Generate graphs.
    for (int i = 0; i < allParams.length; ++i) {
      int[] params = allParams[i];
      // final long startMs = TimeLib.toMs(2014, Month.JANUARY, 1);
      final long startMs = stock.getEndMS() - 365 * 4 * TimeLib.MS_IN_DAY;
      final long endMs = TimeLib.TIME_END;
      Sequence trigger = FinLib.sma(stock, params[0], params[1], FinLib.Close).subseq(startMs, endMs);
      Sequence base = FinLib.sma(stock, params[2], params[3], FinLib.Close).subseq(startMs, endMs);
      Sequence baseLow = base.dup()._mul(1.0 - params[4] / 10000.0).setName("BaseLow");
      Sequence baseHigh = base.dup()._mul(1.0 + params[4] / 10000.0).setName("BaseHigh");
      Sequence raw = stock.subseq(startMs, endMs);
      trigger.setName("Trigger");
      base.setName("Base");
      Chart.saveLineChart(new File(DataIO.outputPath, String.format("sma%d.html", i + 1)),
          String.format("SMA-%d", i + 1), 1200, 600, ChartScaling.LINEAR, ChartTiming.DAILY, trigger, baseLow, baseHigh,
          raw);
    }
  }
}
