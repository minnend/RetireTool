package org.minnen.retiretool.tactical;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.ConfigMulti;
import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.MultiPredictor;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.predictor.daily.TimeCode;
import org.minnen.retiretool.stats.ComparisonStats;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.IntPair;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.LinearFunc;
import org.minnen.retiretool.util.PriceModel;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.Writer;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

public class Dashboard
{
  public final static SequenceStore     store          = new SequenceStore();

  /** Dimension to use in the price sequence for SMA predictions. */
  public static int                     iPriceSMA      = 0;

  public static final Slippage          slippage       = Slippage.None;
  public static final LinearFunc        PriceSDev      = LinearFunc.Zero;

  public static final int               maxDelay       = 0;
  public static final long              gap            = 2 * TimeLib.MS_IN_DAY;
  public static final PriceModel        priceModel     = PriceModel.adjCloseModel;

  public static final String            red            = "D12";
  public static final String            green          = "1E2";

  public static final String            miscDirName    = "misc";
  public static final File              miscPath       = new File(DataIO.outputPath, miscDirName);
  public static final String            miscToBase     = "..";

  // Years of history for SMA graphs.
  public static final int               yearsOfHistory = 3;

  // public static final String symbol = "^GSPC";
  public static final String            symbol         = "VFINX";

  // public static final PredictorConfig[] singleConfigs = new PredictorConfig[] {
  // new ConfigSMA(20, 0, 240, 150, 0.25, FinLib.Close, gap), new ConfigSMA(50, 0, 180, 30, 1.0, FinLib.Close, gap),
  // new ConfigSMA(10, 0, 220, 0, 2.0, FinLib.Close, gap) };
  // public static final int[][] allParams = new int[][] { { 20, 0, 240, 150, 25 }, { 50, 0, 180, 30, 100 },
  // { 10, 0, 220, 0, 200 } };

  // PredictorConfig[] singleConfigs = new PredictorConfig[] { new ConfigSMA(20, 0, 240, 150, 0.25, FinLib.Close, gap),
  // new ConfigSMA(20, 0, 250, 50, 1.0, FinLib.Close, gap), new ConfigSMA(50, 0, 180, 30, 1.0, FinLib.Close, gap),
  // new ConfigSMA(10, 0, 220, 0, 2.0, FinLib.Close, gap), new ConfigSMA(10, 0, 230, 40, 2.0, FinLib.Close, gap) };

  public static final PredictorConfig[] singleConfigs  = new PredictorConfig[3];

  /** Old params found in 2016 */
  // public static final int[][] allParams = new int[][] { { 20, 0, 240, 150, 25 }, { 25, 0, 155, 125, 75 },
  // { 5, 0, 165, 5, 50 } };

  // [15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [5,0] / [178,50] m=145 { 8, 0, 21, 15, 1320 }
  // public static final int[][] allParams = new int[][] { { 15, 0, 259, 125, 21 }, { 15, 0, 259, 125, 21 },
  // { 5, 0, 178, 50, 145 } };

  // [15,0] / [259,125] m=21 | [8,0] / [21,15] m=1320 | [43,0] / [55,4] m=2025
  // public static final int[][] allParams = new int[][] { { 15, 0, 259, 125, 21 }, { 15, 0, 259, 125, 21 },
  // { 15, 0, 259, 125, 21 }, };

  // [29,1] / [269,99] m=138 | [23,1] / [233,106] m=103 | [12,0] / [162,109] m=25
  // public static final int[][] allParams = new int[][] { { 29, 1, 269, 99, 138 },
  // { 23, 1, 233, 106, 103 }, { 12, 0, 162, 109, 25 } };

  // [26,0] / [212,104] m=102 | [39,0] / [107,104] m=243 | [63,0] / [23,14] m=105
  // public static final int[][] allParams = new int[][] { { 26, 0, 212, 104, 102 },
  // { 39, 0, 107, 104, 243 }, { 63, 0, 23, 14, 105 } };

  // [15,0] / [259,125] m=21 | [5,0] / [178,50] m=145 | [19,0] / [213,83] m=269
  public static final int[][]           allParams      = new int[][] { { 15, 0, 259, 125, 21 }, { 5, 0, 178, 50, 145 },
      { 19, 0, 213, 83, 269 } };

  static {
    // Create misc directory if it doesn't exist.
    if (!miscPath.exists()) miscPath.mkdirs();

    assert allParams.length == singleConfigs.length;
    for (int i = 0; i < allParams.length; ++i) {
      int[] p = allParams[i];
      assert p.length == 5;
      singleConfigs[i] = new ConfigSMA(p[0], p[1], p[2], p[3], p[4], FinLib.AdjClose, gap);
      // new ConfigSMA(20, 0, 240, 150, 25, FinLib.Close, gap), new ConfigSMA(25, 0, 155, 125, 75, FinLib.Close, gap),
      // new ConfigSMA(5, 0, 165, 5, 50, FinLib.Close, gap) };
    }
  }

  public static class CodeInfo
  {
    public double returns;
    public double drawdown;
    public long   timeStart, timeEnd;

    public CodeInfo(double returns, double drawdown, long timeStart, long timeEnd)
    {
      this.returns = returns;
      this.drawdown = drawdown;
      this.timeStart = timeStart;
      this.timeEnd = timeEnd;
    }

    public static double[] extract(List<CodeInfo> list, String field)
    {
      boolean extractReturns = (field.equals("returns"));
      double[] r = new double[list.size()];
      for (int i = 0; i < r.length; ++i) {
        CodeInfo info = list.get(i);
        r[i] = extractReturns ? info.returns : info.drawdown;
      }
      return r;
    }

  }

  private static String genGraphFileName(int code)
  {
    return String.format("%s/graphs-code-%02d.html", miscDirName, code);
  }

  private static String genGraphFileName(IntPair codePair)
  {
    return String.format("%s/graphs-pair-%02d-%02d.html", miscDirName, codePair.first, codePair.second);
  }

  private static String genColoredCell(String data, boolean cond, String trueColor, String falseColor)
  {
    final String color = cond ? trueColor : falseColor;
    if (color == null || color.isEmpty()) {
      return String.format("<td>%s</td>", data);
    } else {
      return String.format("<td style=\"color: #%s;\">%s</td>", color, data);
    }
  }

  private static String genTableRow(String tag, String... fields)
  {
    StringBuffer sb = new StringBuffer();
    sb.append("<tr>");
    for (String field : fields) {
      field = field.replaceAll("\\n", "<br/>");
      sb.append(String.format("<%s>%s</%s>", tag, field, tag));
    }
    sb.append("</tr>\n");
    return sb.toString();
  }

  private static Sequence genGrowthSeq(int index, CodeInfo info)
  {
    final Sequence returns = store.get(TacticLib.riskyName);
    final int index1 = returns.getClosestIndex(info.timeStart);
    final int index2 = returns.getClosestIndex(info.timeEnd);
    final int n = index2 - index1 + 1;
    Sequence seq = returns.subseq(index1, n);
    seq = seq.div(seq.getFirst(0));
    for (FeatureVec x : seq) {
      x.set(0, FinLib.mul2ret(x.get(0))); // graph returns, not multipliers
    }
    seq.setName(
        String.format("[%s -> %s] (%d)", TimeLib.formatDate(info.timeStart), TimeLib.formatDate(info.timeEnd), index));
    return seq;
  }

  private static String genCodeStatsHtml(Map<Integer, List<CodeInfo>> returnsByCode, int currentCode) throws IOException
  {
    StringWriter sw = new StringWriter();
    try (Writer writer = new Writer(sw)) {
      writer.write("<table id=\"decadeComparisonTable\" cellspacing=\"0\"><thead>\n");
      writer.write(genTableRow("th", "Code", "Count", "Win\nPercent", "Total\nReturn", "Worst\nDD", "Min\nReturn",
          "Mean\nReturn", "Max\nReturn", ""));
      writer.write("</thead><tbody>\n");

      int iRow = 0;
      for (Map.Entry<Integer, List<CodeInfo>> entry : returnsByCode.entrySet()) {
        double[] returns = CodeInfo.extract(entry.getValue(), "returns");
        Arrays.sort(returns);
        final int n = returns.length;
        double total = 1.0;
        int nLose = 0;
        for (double r : returns) {
          total *= r;
          if (r < 1.0) ++nLose;
        }
        double[] drawdowns = CodeInfo.extract(entry.getValue(), "drawdowns");
        String className = (iRow % 2 == 0 ? "evenRow" : "oddRow");
        if (entry.getKey() == currentCode) {
          className += "Bold";
        }
        double mean = FinLib.mul2ret(Library.mean(returns));
        String sMean = genColoredCell(String.format("%.2f", mean), mean >= 0, null, red);

        double totalReturn = FinLib.mul2ret(total);
        String sTotal = genColoredCell(String.format("%.2f", totalReturn), totalReturn >= 0, null, red);
        writer.write(
            " <tr class=\"%s\"><td>%d</td><td>%d</td><td>%.1f</td>%s<td>%.2f</td><td>%.2f</td>%s<td>%.2f</td><td>%s</td></tr>\n",
            className, entry.getKey(), returns.length, 100.0 - 100.0 * nLose / n, sTotal, Library.min(drawdowns),
            FinLib.mul2ret(returns[0]), sMean, FinLib.mul2ret(returns[n - 1]),
            String.format("<a href=\"%s\">graph</a>", genGraphFileName(entry.getKey())));

        ++iRow;
      }
      writer.write("</tbody>\n</table><br/>\n");
    }

    for (Map.Entry<Integer, List<CodeInfo>> entry : returnsByCode.entrySet()) {
      List<CodeInfo> list = entry.getValue();
      List<Sequence> seqs = new ArrayList<>();
      for (int index = 0; index < list.size(); ++index) {
        seqs.add(genGrowthSeq(index, list.get(index)));
      }

      String title = String.format("Code: %d (n=%d)", entry.getKey(), list.size());
      String filename = genGraphFileName(entry.getKey());
      ChartConfig config = ChartConfig.buildLine(new File(DataIO.outputPath, filename), title, "100%", "900px",
          ChartScaling.LINEAR, ChartTiming.INDEX, seqs);
      config.setPathToBase(miscToBase);
      Chart.saveChart(config);
    }

    return sw.toString();
  }

  private static String genPairStatsHtml(Map<IntPair, List<CodeInfo>> returnsByPair, IntPair currentPair)
      throws IOException
  {
    StringWriter sw = new StringWriter();
    try (Writer writer = new Writer(sw)) {
      writer.write("<table id=\"decadeComparisonTable\" cellspacing=\"0\"><thead>\n");
      writer.write(genTableRow("th", "Previous\nCode", "Current\nCode", "Count", "Win\nPercent", "Total\nReturn",
          "Worst\nDD", "Min\nReturn", "Mean\nReturn", "Max\nReturn", ""));
      writer.write("</thead><tbody>\n");

      int iRow = 0;
      for (Map.Entry<IntPair, List<CodeInfo>> entry : returnsByPair.entrySet()) {
        IntPair pair = entry.getKey();
        double[] returns = CodeInfo.extract(entry.getValue(), "returns");
        Arrays.sort(returns);
        final int n = returns.length;
        double total = 1.0;
        int nLose = 0;
        for (double r : returns) {
          total *= r;
          if (r < 1.0) ++nLose;
        }

        double[] drawdowns = CodeInfo.extract(entry.getValue(), "drawdowns");

        String className = (iRow % 2 == 0 ? "evenRow" : "oddRow");
        if (pair.equals(currentPair)) {
          className += "Bold";
        }
        double mean = FinLib.mul2ret(Library.mean(returns));
        String sMean = genColoredCell(String.format("%.2f", mean), mean >= 0, null, red);
        double totalReturn = FinLib.mul2ret(total);
        String sTotal = genColoredCell(String.format("%.2f", totalReturn), totalReturn >= 0, null, red);
        writer.write(
            " <tr class=\"%s\"><td>%d</td><td>%d</td><td>%d</td><td>%.1f</td>%s<td>%.2f</td><td>%.2f</td>%s<td>%.2f</td><td>%s</td></tr>\n",
            className, pair.first, pair.second, returns.length, 100.0 - 100.0 * nLose / n, sTotal,
            Library.min(drawdowns), FinLib.mul2ret(returns[0]), sMean, FinLib.mul2ret(returns[n - 1]),
            String.format("<a href=\"%s\">graph</a>", genGraphFileName(entry.getKey())));
        ++iRow;
      }
      writer.write("</tbody>\n</table><br/>\n");
    }

    for (Map.Entry<IntPair, List<CodeInfo>> entry : returnsByPair.entrySet()) {
      IntPair codePair = entry.getKey();
      List<CodeInfo> list = entry.getValue();
      List<Sequence> seqs = new ArrayList<>();
      for (int index = 0; index < list.size(); ++index) {
        seqs.add(genGrowthSeq(index, list.get(index)));
      }

      String title = String.format("Code Pair: %d -> %d (n=%d)", codePair.first, codePair.second, list.size());
      String filename = genGraphFileName(codePair);
      ChartConfig config = ChartConfig.buildLine(new File(DataIO.outputPath, filename), title, "100%", "900px",
          ChartScaling.LINEAR, ChartTiming.INDEX, seqs);
      config.setPathToBase(miscToBase);
      Chart.saveChart(config);
    }

    return sw.toString();
  }

  private static String genStatsTableHtml(ComparisonStats comparison, CumulativeStats... strategyStats)
      throws IOException
  {
    StringWriter sw = new StringWriter();
    ComparisonStats.Results[] comparisons = new ComparisonStats.Results[] { comparison.durationToResults.get(5 * 12),
        comparison.durationToResults.get(10 * 12), comparison.durationToResults.get(20 * 12) };
    try (Writer writer = new Writer(sw)) {
      writer.write("<table id=\"decadeComparisonTable\" cellspacing=\"0\"><thead>\n");
      writer.write("<thead>\n");
      writer.write(genTableRow("th", "Strategy", "CAGR", "Std. Dev", "Worst<br/>DD", "Sharpe<br/>Ratio",
          "Median<br/>Return", String.format("Regret<br/>%s", TimeLib.formatDurationMonths(comparisons[0].duration)),
          String.format("Regret<br/>%s", TimeLib.formatDurationMonths(comparisons[1].duration)),
          String.format("Regret<br/>%s", TimeLib.formatDurationMonths(comparisons[2].duration))));
      writer.write("</thead>\n");
      writer.write("<tbody>\n");

      int iRow = 0;
      for (CumulativeStats stats : strategyStats) {
        writer.write("<tr class=\"%s\">\n", iRow % 2 == 0 ? "evenRow" : "oddRow");
        String name = stats.name();
        writer.write(String.format("<td><b>%s</b></td>\n", name));
        writer.write(String.format("<td>%.2f</td>\n", stats.cagr));
        writer.write(String.format("<td>%.2f</td>\n", stats.devAnnualReturn));
        writer.write(String.format("<td>%.2f</td>\n", stats.drawdown));
        double sharpe = FinLib.sharpeDaily(stats.cumulativeReturns, null);
        writer.write(String.format("<td>%.2f</td>\n", sharpe));
        writer.write(String.format("<td>%.2f</td>\n", stats.annualPercentiles[2]));
        // Regret is other strategy's win percent.
        for (int i = 0; i < comparisons.length; ++i) {
          writer.write(String.format("<td>%.1f%%</td>\n", comparisons[i].getWinPercent(1 - iRow)));
        }
        writer.write("</tr>\n");
        ++iRow;
      }
      writer.write("</tbody>\n</table>\n");
    }
    return sw.toString();
  }

  public static void runMulti3(Simulation sim) throws IOException
  {
    // Buy-and-Hold 100% stock.
    PredictorConfig configRisky = new ConfigConst(TacticLib.riskyName);
    Predictor predRisky = configRisky.build(sim.broker.accessObject, TacticLib.assetNames);
    sim.run(predRisky, "Baseline");
    Sequence baselineDailyReturns = sim.returnsDaily;
    Sequence baselineMonthlyReturns = sim.returnsMonthly;
    CumulativeStats statsBaseline = CumulativeStats.calc(sim.returnsDaily);
    System.out.println(statsBaseline);

    // Multi-predictor to make final decisions.
    Set<Integer> contrary = new HashSet<Integer>();
    Set<IntPair> contraryPairs = new HashSet<IntPair>();
    contrary.add(0); // always be safe during code 0
    // if (avoid62) {
    // contraryPairs.add(new IntPair(6, 2));
    // }
    // contraryPairs.add(new IntPair(-1, 0));
    // contraryPairs.add(new IntPair(1, 0));
    // contraryPairs.add(new IntPair(2, 0));
    // contraryPairs.add(new IntPair(6, 2));

    PredictorConfig configStrategy = new ConfigMulti(true, contrary, contraryPairs, singleConfigs);
    MultiPredictor predStrategy = (MultiPredictor) configStrategy.build(sim.broker.accessObject, TacticLib.assetNames);
    sim.run(predStrategy, "Tactical");
    Sequence tacticalDailyReturns = sim.returnsDaily;
    Sequence tacticalMonthlyReturns = sim.returnsMonthly;
    assert tacticalDailyReturns.matches(baselineDailyReturns);
    CumulativeStats statsTactical = CumulativeStats.calc(sim.returnsDaily);
    System.out.println(statsTactical);

    ComparisonStats comparison = ComparisonStats.calc(baselineMonthlyReturns, tacticalMonthlyReturns, 0.25);

    final String sRowGap = "<td class=\"hgap\">&nbsp;</td>";

    try (Writer f = new Writer(new File(DataIO.outputPath, "dashboard-tactical.html"))) {
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
      Map<Integer, List<CodeInfo>> returnsByCode = new TreeMap<>();
      Map<IntPair, List<CodeInfo>> returnsByPair = new TreeMap<>();
      int nCodes = predStrategy.timeCodes.size();
      for (int iCode = nCodes - 1; iCode >= 0; --iCode) {
        TimeCode timeCodeSingles = predStrategy.timeCodes.get(iCode);

        long prevTime = baselineDailyReturns.getStartMS();
        if (iCode > 0) {
          prevTime = predStrategy.timeCodes.get(iCode - 1).time;
        }

        long nextTime = baselineDailyReturns.getEndMS();
        if (iCode < predStrategy.timeCodes.size() - 1) {
          nextTime = predStrategy.timeCodes.get(iCode + 1).time;
        }

        // Date column.
        f.write("<tr class=\"%s\"><td class=\"history\">%s</td>%s<td>%d</td>", iCode % 2 == 0 ? "evenRow" : "oddRow",
            TimeLib.formatDate(timeCodeSingles.time), sRowGap, timeCodeSingles.code);
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
        int index1 = baselineDailyReturns.getClosestIndex(timeCodeSingles.time);
        int index2 = baselineDailyReturns.getClosestIndex(nextTime);
        double totalMul = FinLib.getTotalReturn(baselineDailyReturns, index1, index2);
        double drawdown = FinLib.calcDrawdown(baselineDailyReturns, index1, index2).getMin().get(0);

        // Returns by code.
        List<CodeInfo> returns = returnsByCode.get(timeCodeSingles.code);
        if (returns == null) {
          returns = new ArrayList<CodeInfo>();
          returnsByCode.put(timeCodeSingles.code, returns);
        }
        returns.add(new CodeInfo(totalMul, drawdown, timeCodeSingles.time, nextTime));

        // Returns by code pair.
        if (iCode > 0) {
          TimeCode timeCodePrev = predStrategy.timeCodes.get(iCode - 1);
          IntPair pair = new IntPair(timeCodePrev.code, timeCodeSingles.code);
          returns = returnsByPair.get(pair);
          if (returns == null) {
            returns = new ArrayList<CodeInfo>();
            returnsByPair.put(pair, returns);
          }
          returns.add(new CodeInfo(totalMul, drawdown, timeCodeSingles.time, nextTime));
        }

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
        long nextTopTime = (iNextTop >= predStrategy.timeCodes.size() ? baselineDailyReturns.getEndMS()
            : predStrategy.timeCodes.get(iNextTop).time);
        index2 = baselineDailyReturns.getClosestIndex(nextTopTime);
        totalMul = FinLib.getTotalReturn(baselineDailyReturns, index1, index2);
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
      f.write("<li>Prediction Code\n");
      f.write("<li>Vote for each of the three SMA predictors\n");
      f.write("<li>Trade decision (combined vote: <font color=\"#%s\">"
          + "Green</font>=Risky, <font color=\"#%s\">Red</font>=Safe)\n", green, red);
      f.write("<li>Price change between events\n");
      f.write("<li>Price change between trades\n");
      f.write("</ol>\n");
      f.write("<div><b>Graphs for SMA Predictors:</b>\n");
      f.write("<a href=\"%s/sma1-code4.html\">SMA (4)</a>&nbsp;|&nbsp;\n", miscDirName);
      f.write("<a href=\"%s/sma2-code2.html\">SMA (2)</a>&nbsp;|&nbsp;\n", miscDirName);
      f.write("<a href=\"%s/sma3-code1.html\">SMA (1)</a>\n", miscDirName);
      f.write("</div><br/>\n");

      f.write(genStatsTableHtml(comparison, statsBaseline, statsTactical) + "<br/>");

      f.write(genCodeStatsHtml(returnsByCode, predStrategy.timeCodes.get(nCodes - 1).code));
      if (nCodes > 1) {
        int code = predStrategy.timeCodes.get(nCodes - 1).code;
        int prev = predStrategy.timeCodes.get(nCodes - 2).code;
        f.write(genPairStatsHtml(returnsByPair, new IntPair(prev, code)));
      }
      f.write(Chart.genDecadeTable(tacticalMonthlyReturns, baselineMonthlyReturns) + "<br/>");
      f.write("</div>\n"); // end column 2

      f.write("</body></html>\n");
    }
  }

  public static void main(String[] args) throws IOException
  {
    TacticLib.setupData(symbol, store);

    Sequence stock = store.get(TacticLib.riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 365 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    Simulation sim = new Simulation(store, guideSeq, slippage, maxDelay, priceModel, priceModel);
    runMulti3(sim);

    // Generate graphs.
    for (int i = 0; i < allParams.length; ++i) {
      int[] params = allParams[i];
      // final long startMs = TimeLib.toMs(2014, Month.JANUARY, 1);
      final long startMs = stock.getEndMS() - 365 * yearsOfHistory * TimeLib.MS_IN_DAY;
      final long endMs = TimeLib.TIME_END;
      Sequence trigger = FinLib.sma(stock, params[0], params[1], FinLib.Close).subseq(startMs, endMs);
      Sequence base = FinLib.sma(stock, params[2], params[3], FinLib.Close).subseq(startMs, endMs);
      Sequence baseLow = base.dup()._mul(1.0 - params[4] / 10000.0).setName("BaseLow");
      Sequence baseHigh = base.dup()._mul(1.0 + params[4] / 10000.0).setName("BaseHigh");
      Sequence raw = stock.subseq(startMs, endMs);
      trigger.setName(String.format("Trigger[%d]", params[0]));
      base.setName(String.format("Base[%d]", params[2]));
      int code = 1 << (allParams.length - 1 - i);

      File file = new File(miscPath, String.format("sma%d-code%d.html", i + 1, code));
      String title = String.format("SMA %d (Code: %d)", i + 1, code);
      ChartConfig config = ChartConfig.buildLine(file, title, "100%", "600px", ChartScaling.LINEAR, ChartTiming.DAILY,
          trigger, baseLow, baseHigh, raw);
      config.setPathToBase(miscToBase);
      Chart.saveChart(config);
    }
  }
}
