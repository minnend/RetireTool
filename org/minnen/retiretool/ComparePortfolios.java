package org.minnen.retiretool;

import java.io.File;
import java.io.IOException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.broker.Simulation;
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
import org.minnen.retiretool.viz.ChartConfig;

public class ComparePortfolios
{
  public static final SequenceStore store        = new SequenceStore();
  public static final Slippage      slippage     = new Slippage(0.03, 0.0);

  // public static final String[] fundSymbols = new String[] { "SPY", "VTSMX", "VBMFX", "VGSIX", "VGTSX", "VFISX",
  // "VFSTX", "VBISX", "EWU", "EWG", "EWJ", "VGENX", "WHOSX", "FAGIX", "BUFHX", "VFICX", "FNMIX", "DFGBX", "SGGDX",
  // "VGPMX", "USAGX", "FSPCX", "FSRBX", "FPBFX", "ETGIX", "VDIGX", "MDY", "VBINX", "VWINX", "MCA" };

  // public static final String[] fundSymbols = new String[] { "VTSMX", "VBMFX", "VGSIX", "VGTSX", "VFISX" };

  public static final String[]      fundSymbols  = new String[] { "VFINX", "VBMFX", "VWIGX", "VFISX" };

  public static final String[]      assetSymbols = new String[fundSymbols.length + 1];

  static {
    // Add "cash" as the last asset since it's not a fund in fundSymbols.
    System.arraycopy(fundSymbols, 0, assetSymbols, 0, fundSymbols.length);
    assetSymbols[assetSymbols.length - 1] = "cash";
  }

  public static void main(String[] args) throws IOException
  {
    File outputDir = new File("g:/web");
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
    String intSymbol = "VWIGX"; // "VGTSX";

    // Lazy 2-fund portfolio.
    String[] assetsLazy2 = new String[] { stockSymbol, "VBMFX" };
    config = new ConfigMixed(new DiscreteDistribution(assetsLazy2, new double[] { 0.7, 0.3 }),
        new ConfigConst(assetsLazy2[0]), new ConfigConst(assetsLazy2[1]));
    predictor = config.build(sim.broker.accessObject, assetsLazy2);
    Sequence returnsLazy2 = sim.run(predictor, timeSimStart, timeSimEnd, "Lazy2 [70/30]");
    System.out.println(CumulativeStats.calc(returnsLazy2));
    returns.add(returnsLazy2);

    // Lazy 3-fund portfolio.
    String[] lazy3 = new String[] { stockSymbol, "VBMFX", intSymbol };
    config = new ConfigMixed(new DiscreteDistribution(lazy3, new double[] { 0.34, 0.33, 0.33 }),
        new ConfigConst(lazy3[0]), new ConfigConst(lazy3[1]), new ConfigConst(lazy3[2]));
    predictor = config.build(sim.broker.accessObject, lazy3);
    Sequence returnsLazy3 = sim.run(predictor, timeSimStart, timeSimEnd, "Lazy3");
    System.out.println(CumulativeStats.calc(returnsLazy3));
    returns.add(returnsLazy3);

    // Lazy 4-fund portfolio.
    // String[] lazy4 = new String[] { stockSymbol, "VBMFX", "VGSIX", intSymbol };
    // config = new ConfigMixed(new DiscreteDistribution(lazy4, new double[] { 0.4, 0.2, 0.1, 0.3 }),
    // new ConfigConst(lazy4[0]), new ConfigConst(lazy4[1]), new ConfigConst(lazy4[2]), new ConfigConst(lazy4[3]));
    // predictor = config.build(sim.broker.accessObject, lazy4);
    // Sequence returnsLazy4 = sim.run(predictor, timeSimStart, timeSimEnd, "Lazy4");
    // System.out.println(CumulativeStats.calc(returnsLazy4));
    // returns.add(returnsLazy4);

    // Set up defenders for comparison analysis based on previous portfolios.
    List<ComparisonStats> compStats = new ArrayList<>();
    // Sequence[] defenders = new Sequence[] { returnsStock, returnsBonds, returnsLazy2, returnsLazy3, returnsLazy4 };
    Sequence[] defenders = new Sequence[] { returnsLazy2, returnsLazy3 }; // , returnsLazy4 };
    for (Sequence ret : defenders) {
      compStats.add(ComparisonStats.calc(ret, 0.5, defenders));
    }

    // All stock.
    PredictorConfig stockConfig = new ConfigConst(stockSymbol);
    predictor = stockConfig.build(sim.broker.accessObject, assetSymbols);
    Sequence returnsStock = sim.run(predictor, timeSimStart, timeSimEnd, "Stock");
    System.out.println(CumulativeStats.calc(returnsStock));
    returns.add(returnsStock);
    compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, defenders));

    // All bonds.
    PredictorConfig bondConfig = new ConfigConst("VBMFX");
    predictor = bondConfig.build(sim.broker.accessObject, assetSymbols);
    Sequence returnsBonds = sim.run(predictor, timeSimStart, timeSimEnd, "Bonds");
    System.out.println(CumulativeStats.calc(returnsBonds));
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
    int nBaseA = 240;
    int nBaseB = 220;
    FeatureExtractor feDualMom = new Momentum(20, 1, nBaseA, nBaseB, Momentum.ReturnOrMul.Return,
        Momentum.CompoundPeriod.Weekly, FinLib.Close);
    int dualMomAge = (nBaseA + nBaseB + 20) / 40;
    Stump stump = new Stump(0, 0.0, false, 5.0);
    Predictor dualMom = new AdaptivePredictor(feDualMom, stump, 1, "VFISX", sim.broker.accessObject,
        new String[] { stockSymbol, intSymbol, "VFISX" });
    sim.run(dualMom, timeSimStart, timeSimEnd, String.format("Dual_Momentum[%d]", dualMomAge));
    System.out.println(CumulativeStats.calc(sim.returnsMonthly));
    returns.add(sim.returnsMonthly);
    compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, defenders));
    Chart.saveHoldings(new File(outputDir, "holdings-dual-momentum.html"), sim.holdings, sim.store);

    // Save reports: graph of returns + comparison summary.
    String title = String.format("Returns (%d\u00A2 Spread)", Math.round(slippage.constSlip * 200));
    Chart.saveLineChart(new File(outputDir, "returns.html"), title, 1000, 640, true, true, returns);

    Chart.saveComparisonTable(new File(outputDir, "comparison.html"), 1000, compStats);

    // Report: comparison of returns over next N months.
    int nMonths = 12 * 5;
    List<Sequence> durationalReturns = new ArrayList<>();
    for (Sequence r : returns) {
      Sequence seq = FinLib.calcReturnsForMonths(r, nMonths);
      System.out.printf("Name: %s\n", seq.getName());
      durationalReturns.add(seq);
    }
    title = String.format("Returns (%s, %d\u00A2 Spread)", TimeLib.formatDurationMonths(nMonths),
        Math.round(slippage.constSlip * 200));
    Chart.saveLineChart(new File(outputDir, "duration-returns.html"), title, 1000, 640, false, true, durationalReturns);
  }
}
