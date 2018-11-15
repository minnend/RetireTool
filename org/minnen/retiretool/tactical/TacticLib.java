package org.minnen.retiretool.tactical;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.YahooIO;
import org.minnen.retiretool.data.tiingo.TiingoFund;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

public class TacticLib
{
  public static final String   riskyName  = "stock";
  public static final String   safeName   = "3-month-treasuries";
  public static final String[] assetNames = new String[] { riskyName, safeName };

  public static void setupData(String symbol, SequenceStore store) throws IOException
  {
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

  public static CumulativeStats eval(PredictorConfig config, String name, int nPerturb, Simulation sim)
  {
    return eval(config, name, nPerturb, sim, null);
  }

  public static CumulativeStats eval(PredictorConfig config, String name, int nPerturb, Simulation sim,
      List<CumulativeStats> allStats)
  {
    Predictor pred = config.build(null, TacticLib.assetNames);
    sim.run(pred, name);
    CumulativeStats worstStats = CumulativeStats.calc(sim.returnsMonthly);
    worstStats.config = config;
    if (allStats != null) {
      allStats.clear();
      allStats.add(worstStats);
    }

    for (int i = 0; i < nPerturb; ++i) {
      PredictorConfig perturbedConfig = config.genPerturbed();
      pred = perturbedConfig.build(null, TacticLib.assetNames);
      sim.run(pred, name);
      CumulativeStats stats = CumulativeStats.calc(sim.returnsMonthly);
      stats.config = perturbedConfig;
      if (allStats != null) allStats.add(stats);
      if (stats.prefer(worstStats) < 0) { // performance of strategy = worst over perturbed params
        worstStats = stats;
      }
    }

    return worstStats;
  }
}
