package org.minnen.retiretool;

import java.io.File;
import java.io.IOException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.tiingo.Tiingo;
import org.minnen.retiretool.ml.Stump;
import org.minnen.retiretool.predictor.config.ConfigConst;
import org.minnen.retiretool.predictor.config.ConfigMixed;
import org.minnen.retiretool.predictor.config.ConfigTactical;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.AdaptivePredictor;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.predictor.features.FeatureExtractor;
import org.minnen.retiretool.predictor.features.Momentum;
import org.minnen.retiretool.stats.ComparisonStats;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;

// NOBL - based on dividend aristocrats
// https://www.suredividend.com/wp-content/uploads/2016/07/NOBL-Index-Historical-Constituents.pdf

public class ComparePortfolios
{
  public static final SequenceStore store        = new SequenceStore();
  public static final Slippage      slippage     = new Slippage(0.03, 0.0);

  // public static final String[] fundSymbols = new String[] { "SPY", "VTSMX", "VBMFX", "VGSIX", "VGTSX", "VFISX",
  // "VFSTX", "VBISX", "EWU", "EWG", "EWJ", "VGENX", "WHOSX", "FAGIX", "BUFHX", "VFICX", "FNMIX", "DFGBX", "SGGDX",
  // "VGPMX", "USAGX", "FSPCX", "FSRBX", "FPBFX", "ETGIX", "VDIGX", "MDY", "VBINX", "VWINX", "MCA" };

  // public static final String[] fundSymbols = new String[] { "VTSMX", "VBMFX", "VGSIX", "VGTSX", "VFISX" };

  // TODO use cash instead of VFISX to get data from before 1991.
  public static final String[]      fundSymbols  = new String[] { "VFINX", "VBMFX", "VWIGX", "VFISX", "VWINX",
      "NAESX" };

  public static final String[]      assetSymbols = new String[fundSymbols.length + 1];

  static {
    // Add "cash" as the last asset since it's not a fund in fundSymbols.
    System.arraycopy(fundSymbols, 0, assetSymbols, 0, fundSymbols.length);
    assetSymbols[assetSymbols.length - 1] = "cash";
  }

  public static void main(String[] args) throws IOException
  {
    double startingBalance = 10000.0;
    double monthlyDeposit = 0.0;
    Simulation sim = Tiingo.setupSimulation(fundSymbols, startingBalance, monthlyDeposit, slippage, store);

    long commonStart = store.getCommonStartTime();
    long commonEnd = store.getCommonEndTime();
    // long timeSimStart = commonStart;
    long timeSimStart = TimeLib
        .toMs(TimeLib.ms2date(commonStart).plusWeeks(53 + 4).with(TemporalAdjusters.firstDayOfMonth()));
    // timeSimStart = TimeLib.toMs(2013, Month.JANUARY, 1); // TODO
    long timeSimEnd = commonEnd;
    // timeSimEnd = TimeLib.toMs(2012, Month.DECEMBER, 31); // TODO
    double nSimMonths = TimeLib.monthsBetween(timeSimStart, timeSimEnd);
    System.out.printf("Simulation: [%s] -> [%s] (%.1f months total)\n", TimeLib.formatDate(timeSimStart),
        TimeLib.formatDate(timeSimEnd), nSimMonths);

    // Simulate different portfolios.
    PredictorConfig config;
    Predictor predictor;
    List<Sequence> returns = new ArrayList<>();

    String stockSymbol = "VFINX"; // "VTSMX";
    String bondSymbol = "VBMFX";
    String intSymbol = "VWIGX"; // "VGTSX";
    String reitSymbol = "VGSIX";

    StandardPortfolios portfolios = new StandardPortfolios(sim);

    // Lazy 2-fund portfolio.
    predictor = portfolios.passive("Lazy2 [70/30]", new String[] { stockSymbol, bondSymbol }, 0.7, 0.3);
    Sequence returnsLazy2 = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsLazy2);

    // Lazy 3-fund portfolio.
    predictor = portfolios.passive("Lazy3", new String[] { stockSymbol, bondSymbol, intSymbol }, 0.34, 0.33, 0.33);
    Sequence returnsLazy3 = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsLazy3);

    // Lazy 4-fund portfolio.
    // predictor = portfolios.passive("Lazy4", new String[] { stockSymbol, bondSymbol, intSymbol, reitSymbol }, 0.4,
    // 0.2,
    // 0.3, 0.1);
    // Sequence returnsLazy4 = portfolios.run(predictor, timeSimStart, timeSimEnd);
    // returns.add(returnsLazy4);

    // Set up defenders for comparison analysis based on previous portfolios.
    List<ComparisonStats> compStats = new ArrayList<>();
    // Sequence[] defenders = new Sequence[] { returnsStock, returnsBonds, returnsLazy2, returnsLazy3, returnsLazy4 };
    Sequence[] defenders = new Sequence[] { returnsLazy2, returnsLazy3 }; // , returnsLazy4 };
    for (Sequence ret : defenders) {
      compStats.add(ComparisonStats.calc(ret, 0.5, defenders));
    }

    // All stock.
    predictor = portfolios.passive("S&P 500", stockSymbol);
    Sequence returnsStock = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsStock);
    compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, defenders));

    // All short-term treasuries.
    predictor = portfolios.passive("Short-term Treasuries", "VFISX");
    returns.add(portfolios.run(predictor, timeSimStart, timeSimEnd));

    predictor = portfolios.passive("Wellesley", "VWINX");
    returns.add(portfolios.run(predictor, timeSimStart, timeSimEnd));

    predictor = portfolios.passive("Small-Cap", "NAESX");
    returns.add(portfolios.run(predictor, timeSimStart, timeSimEnd));

    // All bonds.
    predictor = portfolios.passive("Bonds", bondSymbol);
    Sequence returnsBonds = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsBonds);
    compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, defenders));

    // Tactical Allocation
    PredictorConfig tacticalConfig = new ConfigTactical(FinLib.Close, stockSymbol, "VFISX");
    Predictor tacticalPred = tacticalConfig.build(sim.broker.accessObject, assetSymbols);
    sim.run(tacticalPred, timeSimStart, timeSimEnd, "Tactical");
    System.out.println(CumulativeStats.calc(sim.returnsMonthly));
    returns.add(sim.returnsMonthly);
    compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, defenders));

    // Dual Momentum
    Predictor dualMomPred = portfolios.dualMomentum("VFISX", stockSymbol, intSymbol);
    Sequence returnsDualMom = portfolios.run(dualMomPred, timeSimStart, timeSimEnd);
    returns.add(returnsDualMom);
    compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, defenders));
    Chart.saveHoldings(new File(DataIO.outputPath, "holdings-dual-momentum.html"), sim.holdings, sim.store);

    // Save reports: graph of returns + comparison summary.
    String title = String.format("Returns (%d\u00A2 Spread)", Math.round(slippage.constSlip * 200));
    Chart.saveLineChart(new File(DataIO.outputPath, "returns.html"), title, 1000, 640, true, true, returns);

    Chart.saveComparisonTable(new File(DataIO.outputPath, "comparison.html"), 1000, compStats);

    // Report: comparison of returns over next N months.
    for (int nMonths : new int[] { 12 * 5, 12 * 10 }) {
      List<Sequence> durationalReturns = new ArrayList<>();
      for (Sequence r : returns) {
        Sequence seq = FinLib.calcReturnsForMonths(r, nMonths);
        seq.setName(r.getName());
        durationalReturns.add(seq);
      }
      title = String.format("Returns (%s, %d\u00A2 Spread)", TimeLib.formatDurationMonths(nMonths),
          Math.round(slippage.constSlip * 200));
      File file = new File(DataIO.outputPath, String.format("duration-returns-%d-months.html", nMonths));
      Chart.saveLineChart(file, title, 1000, 640, false, true, durationalReturns);
    }
  }
}
