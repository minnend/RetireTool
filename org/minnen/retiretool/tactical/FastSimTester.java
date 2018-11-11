package org.minnen.retiretool.tactical;

import java.io.IOException;

import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.tiingo.TiingoFund;
import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.ConfigSMA;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

/**
 * Development testbed for a "fast" simulator for simple strategies.
 */
public class FastSimTester
{
  public final static SequenceStore store      = new SequenceStore();
  public static final String        riskyName  = "stock";
  public static final String        safeName   = "3-month-treasuries";
  public static final String[]      assetNames = new String[] { riskyName, safeName };

  private static void setupData() throws IOException
  {
    TiingoFund fund = TiingoFund.fromSymbol("VFINX", true);
    Sequence stock = fund.data;
    System.out.printf("%s: [%s] -> [%s]\n", stock.getName(), TimeLib.formatDate(stock.getStartMS()),
        TimeLib.formatDate(stock.getEndMS()));
    store.add(stock, riskyName);

    Sequence tb3mo = FinLib.inferAssetFrom3MonthTreasuries();
    store.add(tb3mo, safeName);
  }

  public static void main(String[] args) throws IOException
  {
    setupData();

    // Set up baseline (slow) simulator.
    Sequence stock = store.get(riskyName);
    store.add(stock.getIntegralSeq());
    // Note: 470 is large enough for strategy and to skip bad (monthly) data from VFINX from Tiingo.
    final int iStart = stock.getIndexAtOrAfter(stock.getStartMS() + 470 * TimeLib.MS_IN_DAY);
    Sequence guideSeq = stock.subseq(iStart);
    Simulation sim = new Simulation(store, guideSeq);
    sim.setCheckBusinessDays(false); // assume data is correct wrt business days (faster but slightly dangerous)
    FastSim fastSim = new FastSim(store, guideSeq);

    final int nRuns = 10;

    // Buy-and-Hold 100% stock.
    ConfigConst configRisky = new ConfigConst(riskyName);
    Predictor predRisky = configRisky.build(null, assetNames);

    // SMA-based predictor.
    final long gap = 2 * TimeLib.MS_IN_DAY;
    ConfigSMA configStrategy = new ConfigSMA(20, 0, 240, 150, 0.25, FinLib.Close, gap);
    Predictor predStrategy = configStrategy.build(null, assetNames);
    long a, b;
    CumulativeStats statsFast, statsSlow;

    // Pre-runs to minimize first-run effects.
    sim.run(predRisky, "prep");
    sim.run(predStrategy, "prep");
    fastSim.run(predRisky, "prep");
    fastSim.run(predStrategy, "prep");

    // Run buy & hold strategy.
    a = TimeLib.getTime();
    for (int i = 0; i < nRuns; ++i) {
      predRisky.reset();
      fastSim.run(predRisky, "Buy & Hold");
    }
    b = TimeLib.getTime();
    System.out.printf("Fast[%s]: %d ms\n", fastSim.returnsDaily.getName(), b - a);
    statsFast = CumulativeStats.calc(fastSim.returnsMonthly);

    a = TimeLib.getTime();
    for (int i = 0; i < nRuns; ++i) {
      predRisky.reset();
      sim.run(predRisky, "Buy & Hold");
    }
    b = TimeLib.getTime();
    System.out.printf("Slow[%s]: %d ms\n", sim.returnsDaily.getName(), b - a);
    statsSlow = CumulativeStats.calc(sim.returnsMonthly);

    System.out.println();
    System.out.printf("Fast: %s\n", statsFast);
    System.out.printf("Slow: %s\n", statsSlow);
    System.out.println();

    // Run tactical strategy.
    a = TimeLib.getTime();
    for (int i = 0; i < nRuns; ++i) {
      predStrategy.reset();
      fastSim.run(predStrategy, "Tactical");
    }
    b = TimeLib.getTime();
    System.out.printf("Fast[%s]: %d ms\n", fastSim.returnsDaily.getName(), b - a);
    statsFast = CumulativeStats.calc(fastSim.returnsMonthly);

    a = TimeLib.getTime();
    for (int i = 0; i < nRuns; ++i) {
      predStrategy.reset();
      sim.run(predStrategy, "Tactical");
    }
    b = TimeLib.getTime();
    System.out.printf("Slow[%s]: %d ms\n", sim.returnsDaily.getName(), b - a);
    statsSlow = CumulativeStats.calc(sim.returnsMonthly);

    System.out.println();
    System.out.printf("Fast: %s\n", statsFast);
    System.out.printf("Slow: %s\n", statsSlow);
  }
}
