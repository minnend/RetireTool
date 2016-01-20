package org.minnen.retiretool;

import java.io.File;
import java.io.IOException;
import java.time.Month;
import java.time.ZoneId;

import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.ConfigMixed;
import org.minnen.retiretool.predictor.config.ConfigMulti;
import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.MixedPredictor;
import org.minnen.retiretool.predictor.daily.MultiPredictor;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.predictor.daily.TimeCode;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

public class Dashboard
{
  public final static SequenceStore store          = new SequenceStore();

  /** Dimension to use in the price sequence for SMA predictions. */
  public static int                 iPriceSMA      = 0;

  public static final Slippage      slippage       = Slippage.None;
  public static final LinearFunc    PriceSDev      = LinearFunc.Zero;

  public static final int           maxDelay       = 0;
  public static final boolean       bBuyAtNextOpen = true;
  public static final long          gap            = 2 * TimeLib.MS_IN_DAY;

  public static final String        riskyName      = "stock";
  public static final String        safeName       = "cash";
  public static final String[]      assetNames     = new String[] { riskyName, safeName };

  public static void setupData() throws IOException
  {
    File dataDir = new File("g:/research/finance/yahoo/");
    if (!dataDir.exists()) {
      dataDir.mkdirs();
    }

    String symbol = "^GSPC";
    File file = DataIO.getYahooFile(dataDir, symbol);
    DataIO.updateDailyDataFromYahoo(file, symbol, 8 * TimeLib.MS_IN_HOUR);

    Sequence stock = DataIO.loadYahooData(file);
    System.out.printf("S&P (Daily): [%s] -> [%s]\n", TimeLib.formatDate(stock.getStartMS()),
        TimeLib.formatDate(stock.getEndMS()));
    store.add(stock, "stock");

    // Add integral sequence for stock data.
    Sequence stockIntegral = stock.getIntegralSeq();
    store.add(stockIntegral);
  }

  public static void runFiveTwoMix(Simulation sim, File dir) throws IOException
  {
    // Buy-and-Hold.
    PredictorConfig configRisky = new ConfigConst(riskyName);
    Predictor predRisky = configRisky.build(sim.broker.accessObject, assetNames);
    // ConstPredictor predSafe = new ConstPredictor(new ConfigConst(1), sim.broker.accessObject, assetNames);
    // MixedPredictor predBaseline = new MixedPredictor(new Predictor[] { predRisky, predSafe }, new
    // DiscreteDistribution(
    // 0.8, 0.2), sim.broker.accessObject);
    sim.run(predRisky);
    Sequence baselineReturns = sim.returnsDaily;
    CumulativeStats stats = CumulativeStats.calc(sim.returnsMonthly);
    System.out.println(stats);

    // List of all single-SMA configs.
    PredictorConfig[] singleConfigs = new PredictorConfig[] { new ConfigSMA(20, 0, 240, 150, 0.25, FinLib.Close, gap),
        new ConfigSMA(20, 0, 250, 50, 1.0, FinLib.Close, gap), new ConfigSMA(50, 0, 180, 30, 1.0, FinLib.Close, gap),
        new ConfigSMA(10, 0, 220, 0, 2.0, FinLib.Close, gap), new ConfigSMA(10, 0, 230, 40, 2.0, FinLib.Close, gap) };

    // One config that holds all of them to make it easier to get results.
    PredictorConfig configSingles = new ConfigMulti(-1, singleConfigs);
    MultiPredictor predSingles = (MultiPredictor) configSingles.build(sim.broker.accessObject, assetNames);
    sim.run(predSingles);

    // Actual predictors.
    PredictorConfig[] multiConfigs = new PredictorConfig[] {
        new ConfigMulti(254, new PredictorConfig[] { singleConfigs[0], singleConfigs[2], singleConfigs[3] }),
        new ConfigMulti(254, new PredictorConfig[] { singleConfigs[0], singleConfigs[2], singleConfigs[4] }) };

    PredictorConfig configMultis = new ConfigMulti(-1, multiConfigs);
    MultiPredictor predMultis = (MultiPredictor) configMultis.build(sim.broker.accessObject, assetNames);
    sim.run(predMultis);

    DiscreteDistribution mix = new DiscreteDistribution(0.5, 0.5);
    PredictorConfig configMix = new ConfigMixed(mix, multiConfigs);
    MixedPredictor predMix = (MixedPredictor) configMix.build(sim.broker.accessObject, assetNames);
    sim.run(predMix);
    Sequence strategyReturns = sim.returnsDaily;
    assert strategyReturns.matches(baselineReturns);

    stats = CumulativeStats.calc(sim.returnsMonthly);
    System.out.println(stats);

    final String green = "1E2";
    final String red = "D12";
    final String orange = "E90";
    final String sRowGap = "<td style=\"width: 4;\">&nbsp;</td>";

    try (Writer f = new Writer(new File(dir, "dashboard.html"))) {
      f.write("<html><head>\n");
      f.write("<title>Dashboard</title>\n");
      f.write("<script src=\"http://code.jquery.com/jquery.min.js\"></script>\n");
      f.write("<link rel=\"stylesheet\" href=\"dashboard.css\">\n");
      f.write("</head>\n");
      f.write("<table>\n");

      // Pre-compute top-level color strings.
      String[] topColors = new String[predMultis.timeCodes.size()];
      for (int i = 0; i < predMultis.timeCodes.size(); ++i) {
        switch (predMultis.timeCodes.get(i).code) {
        case 0:
          topColors[i] = red;
          break;
        case 1:
        case 2:
          topColors[i] = orange;
          break;
        case 3:
          topColors[i] = green;
          break;
        }
      }

      for (int iCode = predSingles.timeCodes.size() - 1; iCode >= 0; --iCode) {
        TimeCode timeCodeSingles = predSingles.timeCodes.get(iCode);

        long prevTime = baselineReturns.getStartMS();
        if (iCode > 0) {
          prevTime = predSingles.timeCodes.get(iCode - 1).time;
        }

        long nextTime = baselineReturns.getEndMS();
        if (iCode < predSingles.timeCodes.size() - 1) {
          nextTime = predSingles.timeCodes.get(iCode + 1).time;
        }

        // Date column.
        f.write("<tr><td class=\"history\">%s</td>", TimeLib.formatDate(timeCodeSingles.time));
        f.write(sRowGap);

        // Single SMA predictors.
        for (int i = 0; i < singleConfigs.length; ++i) {
          int mask = 1 << (singleConfigs.length - i - 1);
          boolean b = ((timeCodeSingles.code & mask) != 0);
          f.write("<td style=\"width: 16; background: #%s;\">&nbsp;</td>", b ? green : red);
        }

        // Multi-predictors.
        f.write(sRowGap);
        int iTopCode = TimeCode.indexForTime(timeCodeSingles.time, predMultis.timeCodes);
        int iPrevTop = TimeCode.indexForTime(prevTime, predMultis.timeCodes);
        TimeCode timeCodeMultis = predMultis.timeCodes.get(iTopCode);
        for (int i = 0; i < multiConfigs.length; ++i) {
          int mask = 1 << (multiConfigs.length - i - 1);
          boolean b = ((timeCodeMultis.code & mask) != 0);
          f.write("<td style=\"width: 16; background: #%s;\">&nbsp;</td>", b ? green : red);
        }

        // Final decision.
        f.write(sRowGap);
        f.write("<td style=\"width: 16; background: #%s;\">&nbsp;</td>", topColors[iTopCode]);

        // Return for this time period (single change).
        int index1 = baselineReturns.getClosestIndex(timeCodeSingles.time);
        int index2 = baselineReturns.getClosestIndex(nextTime);
        double totalMul = FinLib.getTotalReturn(baselineReturns, index1, index2);
        f.write("<td>%.2f</td>", FinLib.mul2ret(totalMul));

        // Return for this time period (top-level change).
        boolean bTopChange = (iCode == 0 || iCode == predSingles.timeCodes.size() - 1 || iTopCode != iPrevTop);
        long nextTopTime = baselineReturns.getEndMS();
        if (iTopCode < predMultis.timeCodes.size() - 1) {
          nextTopTime = predMultis.timeCodes.get(iTopCode + 1).time;
        }
        index2 = baselineReturns.getClosestIndex(nextTopTime);
        totalMul = FinLib.getTotalReturn(baselineReturns, index1, index2);
        f.write(sRowGap);
        f.write("<td>%s</td>", bTopChange ? String.format("%.2f", FinLib.mul2ret(totalMul)) : "");

        // Value of S&P and Strategy
        f.write(sRowGap);
        f.write("<td>%.2f</td>", baselineReturns.get(index1, 0));
        f.write(sRowGap);
        f.write("<td>%.2f</td>", strategyReturns.get(index1, 0));

        f.write("</tr>\n");

      }
      f.write("</table>\n");
      f.write("</body></html>\n");
    }
  }

  public static void runMulti3(Simulation sim, File dir) throws IOException
  {
    // Buy-and-Hold.
    PredictorConfig configRisky = new ConfigConst(riskyName);
    Predictor predRisky = configRisky.build(sim.broker.accessObject, assetNames);
    // ConstPredictor predSafe = new ConstPredictor(new ConfigConst(1), sim.broker.accessObject, assetNames);
    // MixedPredictor predBaseline = new MixedPredictor(new Predictor[] { predRisky, predSafe }, new
    // DiscreteDistribution(
    // 0.8, 0.2), sim.broker.accessObject);
    sim.run(predRisky);
    Sequence baselineReturns = sim.returnsDaily;
    CumulativeStats stats = CumulativeStats.calc(sim.returnsMonthly);
    System.out.println(stats);

    // List of all single-SMA configs.
    // PredictorConfig[] singleConfigs = new PredictorConfig[] { new ConfigSMA(20, 0, 240, 150, 0.25, FinLib.Close,
    // gap),
    // new ConfigSMA(50, 0, 180, 30, 1.0, FinLib.Close, gap), new ConfigSMA(10, 0, 220, 0, 2.0, FinLib.Close, gap) };

    PredictorConfig[] singleConfigs = new PredictorConfig[] { new ConfigSMA(20, 0, 240, 150, 0.25, FinLib.Close, gap),
        new ConfigSMA(25, 0, 155, 125, 0.75, FinLib.Close, gap), new ConfigSMA(5, 0, 165, 5, 0.5, FinLib.Close, gap) };

    // Multi-predictor to make final decisions.
    PredictorConfig configStrategy = new ConfigMulti(254, singleConfigs);
    MultiPredictor predStrategy = (MultiPredictor) configStrategy.build(sim.broker.accessObject, assetNames);
    sim.run(predStrategy);
    Sequence strategyReturns = sim.returnsDaily;
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
      f.write("<link rel=\"stylesheet\" href=\"dashboard.css\">\n");
      f.write("</head><body>\n");

      // Table is Column 1.
      f.write("<div class=\"column\">\n");
      f.write("<table cellspacing=\"0\">\n");
      for (int iCode = predStrategy.timeCodes.size() - 1; iCode >= 0; --iCode) {
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
        int iPrevTop = TimeCode.indexForTime(prevTime, predStrategy.timeCodes);
        TimeCode timeCodeStrategy = predStrategy.timeCodes.get(iTopCode);
        TimeCode prevCodeStrategy = predStrategy.timeCodes.get(iPrevTop);

        f.write(sRowGap);
        boolean b = (timeCodeStrategy.code != 0);
        f.write("<td><div class=\"color\" style=\"background: #%s;\">&nbsp;</div></td>", b ? green : red);
        // Return for this time period (single change).
        int index1 = baselineReturns.getClosestIndex(timeCodeSingles.time);
        int index2 = baselineReturns.getClosestIndex(nextTime);
        double totalMul = FinLib.getTotalReturn(baselineReturns, index1, index2);
        f.write("<td>%.2f</td>", FinLib.mul2ret(totalMul));

        // Return for this time period (top-level change).
        boolean b1 = (timeCodeStrategy.code != 0);
        boolean b2 = (prevCodeStrategy.code != 0);
        boolean bTopChange = (iCode == 0 || iCode == predStrategy.timeCodes.size() - 1 || (b1 ^ b2));

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

        // Value of S&P and Strategy
        // f.write(sRowGap);
        // double baselineTR = baselineReturns.get(index1, 0);
        // double strategyTR = strategyReturns.get(index1, 0);
        // f.write("<td>%.1f%%</td>", FinLib.mul2ret(strategyTR / baselineTR));

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
      f.write("</div>\n");
      f.write("</body></html>\n");
    }
  }

  public static void main(String[] args) throws IOException
  {
    File dir = new File("g:/web/");
    assert dir.isDirectory();

    setupData();

    Sequence stock = store.get(riskyName);
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 365 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    Simulation sim = new Simulation(store, guideSeq, slippage, maxDelay, bBuyAtNextOpen);
    runMulti3(sim, dir);

    int[][] allParams = new int[][] { { 20, 0, 240, 150, 25 }, { 50, 0, 180, 30, 100 }, { 10, 0, 200, 0, 200 } };

    // Generate graphs.
    for (int i = 0; i < allParams.length; ++i) {
      int[] params = allParams[i];
      final long startMs = TimeLib.toMs(2015, Month.JANUARY, 1);
      final long endMs = TimeLib.TIME_END;
      Sequence trigger = FinLib.sma(stock, params[0], params[1]).subseq(startMs, endMs);
      Sequence base = FinLib.sma(stock, params[2], params[3]).subseq(startMs, endMs);
      Sequence baseLow = base.dup()._mul(1.0 - params[4] / 10000.0).setName("BaseLow");
      Sequence baseHigh = base.dup()._mul(1.0 + params[4] / 10000.0).setName("BaseHigh");
      Sequence raw = stock.subseq(startMs, endMs);
      trigger.setName("Trigger");
      base.setName("Base");
      Chart.saveLineChart(new File(dir, String.format("sma%d.html", i + 1)), String.format("SMA-%d", i + 1), 1000, 600,
          true, false, trigger, baseLow, baseHigh, raw);
    }
  }
}
