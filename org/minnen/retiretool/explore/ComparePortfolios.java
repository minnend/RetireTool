package org.minnen.retiretool.explore;

import java.io.File;
import java.io.IOException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.StandardPortfolios;
import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.tiingo.Tiingo;
import org.minnen.retiretool.ml.Stump;
import org.minnen.retiretool.predictor.config.ConfigTactical;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.AdaptivePredictor;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.predictor.daily.VolResPredictor;
import org.minnen.retiretool.predictor.features.FeatureExtractor;
import org.minnen.retiretool.predictor.features.Momentum;
import org.minnen.retiretool.stats.ComparisonStats;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.PlotLine;

public class ComparePortfolios
{
  public static final SequenceStore store        = new SequenceStore();
  public static final Slippage      slippage     = new Slippage(0.03, 0.0);

  // public static final String[] fundSymbols = new String[] { "SPY", "VTSMX", "VBMFX", "VGSIX", "VGTSX", "VFISX",
  // "VFSTX", "VBISX", "EWU", "EWG", "EWJ", "VGENX", "WHOSX", "FAGIX", "BUFHX", "VFICX", "FNMIX", "DFGBX", "SGGDX",
  // "VGPMX", "USAGX", "FSPCX", "FSRBX", "FPBFX", "ETGIX", "VDIGX", "MDY", "VBINX", "VWINX", "MCA" };

  // public static final String[] fundSymbols = new String[] { "VTSMX", "VBMFX", "VGSIX", "VGTSX", "VFISX" };

  // TODO use cash instead of VFISX to get data from before 1991.
  // public static final String[] fundSymbols = new String[] { "VFINX", "VBMFX", "VWIGX", "VFISX", "VWINX", "NAESX" };
  public static final String[]      fundSymbols  = new String[] { "VFINX", "VBMFX", "VWIGX", "VWINX", "NAESX", "VGENX",
      "FRESX", "VEXMX",
      // "VIVAX", "VTMSX", "VISVX", "VGSIX", "FSIIX", "VTRIX", "FSCOX", "VEIEX", "FIREX" // merriman
  };

  public static final String[]      assetSymbols = new String[fundSymbols.length + 2];

  static {
    // Add "cash" as the last asset since it's not a fund in fundSymbols.
    System.arraycopy(fundSymbols, 0, assetSymbols, 0, fundSymbols.length);
    assetSymbols[assetSymbols.length - 2] = "cash";
    assetSymbols[assetSymbols.length - 1] = "3-Month Treasuries";
  }

  public static void main(String[] args) throws IOException
  {
    Sequence tb3mo = FinLib.inferAssetFrom3MonthTreasuries();

    double startingBalance = 10000.0;
    double monthlyDeposit = 0.0;
    Simulation sim = Tiingo.setupSimulation(fundSymbols, startingBalance, monthlyDeposit, slippage, null, store, tb3mo);

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
    Predictor predictor;
    List<Sequence> returns = new ArrayList<>();

    String stockSymbol = "VFINX"; // "VTSMX";
    String stockExtendedSymbol = "VEXMX";
    String bondSymbol = "VBMFX";
    String intlSymbol = "VWIGX"; // "VGTSX";
    String reitSymbol = "FRESX"; // VGSIX;
    String smallCapSymbol = "NAESX";
    String energySymbol = "VGENX";
    String cashSymbol = "3-Month Treasuries"; // "VFISX" // "cash"

    StandardPortfolios portfolios = new StandardPortfolios(sim);

    predictor = portfolios.passive(stockSymbol, stockSymbol);
    Sequence returnsStock = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsStock);

    predictor = portfolios.passive("Short-term Treasuries", cashSymbol);
    returns.add(portfolios.run(predictor, timeSimStart, timeSimEnd));

    predictor = portfolios.passive("Wellesley", "VWINX");
    returns.add(portfolios.run(predictor, timeSimStart, timeSimEnd));

    predictor = portfolios.passive("International Growth", intlSymbol);
    returns.add(portfolios.run(predictor, timeSimStart, timeSimEnd));

    predictor = portfolios.passive("Small-Cap", smallCapSymbol);
    returns.add(portfolios.run(predictor, timeSimStart, timeSimEnd));

    predictor = portfolios.passive("REITs", reitSymbol);
    returns.add(portfolios.run(predictor, timeSimStart, timeSimEnd));

    predictor = portfolios.passive("Extended Market", stockExtendedSymbol);
    returns.add(portfolios.run(predictor, timeSimStart, timeSimEnd));

    predictor = portfolios.passive("Energy", energySymbol);
    returns.add(portfolios.run(predictor, timeSimStart, timeSimEnd));

    predictor = portfolios.passive("Bonds", bondSymbol);
    Sequence returnsBonds = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsBonds);

    // Total Market
    predictor = portfolios.passive("Total Market [70/30]", new String[] { stockSymbol, stockExtendedSymbol }, 0.7, 0.3);
    Sequence returnsTotal = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsTotal);

    // Lazy 2-fund portfolio.
    predictor = portfolios.passive("Lazy2 [70/30]", new String[] { stockSymbol, bondSymbol }, 0.7, 0.3);
    Sequence returnsLazy2 = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsLazy2);

    // Lazy 3-fund portfolio.
    predictor = portfolios.passiveEqualWeight("Lazy3", new String[] { stockSymbol, bondSymbol, intlSymbol });
    Sequence returnsLazy3 = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsLazy3);

    // Lazy 4-fund portfolio.
    predictor = portfolios.passive("Lazy4", new String[] { bondSymbol, stockSymbol, intlSymbol, reitSymbol }, 0.2, 0.4,
        0.3, 0.1);
    Sequence returnsLazy4 = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsLazy4);

    // 50/200 SMA cross.
    predictor = portfolios.simpleSMA(50, 200, stockSymbol, cashSymbol);
    Sequence returnsCross = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsCross);

    // Merriman Aggressive.
    // predictor = portfolios.merrimanAggressive();
    // Sequence returnsMerriman = portfolios.run(predictor, timeSimStart, timeSimEnd);
    // returns.add(returnsMerriman);

    // Set up defenders for comparison analysis based on previous portfolios.
    List<ComparisonStats> compStats = new ArrayList<>();
    Sequence[] defenders = new Sequence[] { returnsTotal, returnsStock, returnsLazy2, returnsLazy3, returnsLazy4,
        returnsBonds };
    for (Sequence ret : defenders) {
      compStats.add(ComparisonStats.calc(ret, 0.5, defenders));
    }

    // 10-month SMA.
    predictor = portfolios.simpleSMA(10, stockSymbol, cashSymbol);
    Sequence returnsSMA10 = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsSMA10);
    compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, defenders));

    // Tactical Allocation
    PredictorConfig tacticalConfig = new ConfigTactical(FinLib.Close, stockSymbol, cashSymbol);
    Predictor tacticalPred = tacticalConfig.build(sim.broker.accessObject, assetSymbols);
    sim.run(tacticalPred, timeSimStart, timeSimEnd, "Tactical");
    System.out.println(CumulativeStats.calc(sim.returnsMonthly));
    returns.add(sim.returnsMonthly);
    compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, defenders));

    // Dual Momentum
    Predictor dualMomPred = portfolios.dualMomentum(1, cashSymbol, stockSymbol, intlSymbol);
    Sequence returnsDualMom = portfolios.run(dualMomPred, timeSimStart, timeSimEnd);
    returns.add(returnsDualMom);
    compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, defenders));
    Chart.saveHoldings(new File(DataIO.outputPath, "holdings-dual-momentum.html"), sim.holdings, sim.store);

    // Multi-sector based on dual momentum
    // int nMaxKeep = 3;
    // int nBaseA = 240;
    // int nBaseB = nBaseA - 20;
    // FeatureExtractor feMom = new Momentum(20, 1, nBaseA, nBaseB, Momentum.ReturnOrMul.Return,
    // Momentum.CompoundPeriod.Weekly, FinLib.Close);
    // int dualMomAge = (nBaseA + nBaseB + 20) / 40;
    // Stump stump = new Stump(0, 0.0, false, 5.0);
    // Predictor multiSectoMom = new AdaptivePredictor(feMom, stump, nMaxKeep, cashSymbol, sim.broker.accessObject,
    // cashSymbol, stockSymbol, intlSymbol, bondSymbol, reitSymbol, energySymbol, smallCapSymbol);
    // multiSectoMom.name = String.format("MultiSectorMom[%d]", dualMomAge);
    // Sequence returnsMultiSectorMomPred = portfolios.run(multiSectoMom, timeSimStart, timeSimEnd);
    // returns.add(returnsMultiSectorMomPred);
    // compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, defenders));
    // Chart.saveHoldings(new File(DataIO.outputPath, "holdings-multi-sector-mom.html"), sim.holdings, sim.store);

    // Volatility-Responsive Asset Allocation.
    // Predictor volres = new VolResPredictor(60, stockSymbol, bondSymbol, sim.broker.accessObject, FinLib.Close);
    // Sequence returnsVolRes = portfolios.run(volres, timeSimStart, timeSimEnd);
    // returns.add(returnsVolRes);
    // compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, defenders));
    // Chart.saveHoldings(new File(DataIO.outputPath, "holdings-volres.html"), sim.holdings, sim.store);

    // Save reports: graph of returns + comparison summary.
    String title = String.format("Returns (%d\u00A2 Spread)", Math.round(slippage.constSlip * 200));
    Chart.saveLineChart(new File(DataIO.outputPath, "returns.html"), title, "100%", "640px", ChartScaling.LOGARITHMIC,
        ChartTiming.MONTHLY, returns);

    Chart.saveComparisonTable(new File(DataIO.outputPath, "comparison.html"), "1000px", compStats);

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
      ChartConfig chartConfig = Chart.saveLineChart(file, title, "100%", "640px", ChartScaling.LINEAR, ChartTiming.MONTHLY,
          durationalReturns);
      chartConfig.addPlotLineY(new PlotLine(0, 2, "black"));
      Chart.saveChart(chartConfig);
    }
  }
}
