package org.minnen.retiretool.explore;

import java.io.File;
import java.io.IOException;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.minnen.retiretool.StandardPortfolios;
import org.minnen.retiretool.broker.Simulation;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.data.tiingo.Tiingo;
import org.minnen.retiretool.data.tiingo.TiingoFund;
import org.minnen.retiretool.data.tiingo.TiingoFundFilter;
import org.minnen.retiretool.predictor.config.ConfigTactical;
import org.minnen.retiretool.predictor.config.PredictorConfig;
import org.minnen.retiretool.predictor.daily.GatedPredictor;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.stats.ComparisonStats;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Slippage;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.Chart.ChartScaling;
import org.minnen.retiretool.viz.Chart.ChartTiming;

// NOBL - based on dividend aristocrats
// https://www.suredividend.com/wp-content/uploads/2016/07/NOBL-Index-Historical-Constituents.pdf

public class DividendAristocrats
{
  public static final SequenceStore store          = new SequenceStore();

  // Dividend Aristocrats for 2018 from: https://www.retirebeforedad.com/dividend-aristocrats/
  // 2017 list + changes back to 2011: https://investorjunkie.com/3974/dividend-aristocrats/

  // Dropped ABBC since data only goes 2013 when it spun off from ABT.
  // TODO use historical data to avoid "future knowledge" (look-ahead / survivorship bias).
  public static final String[]      allAristocrats = new String[] { "AFL", "T", "NUE", "CAH", "TROW", "WBA", "BEN",
      "PNR", "CVX", "DOV", "PPG", "SWK", "XOM", "APD", "GD", "MDT", "ABT", "ADM", "GWW", "MKC", "PG", "TGT", "BDX",
      "ED", "GPC", "AOS", "ECL", "HRL", "JNJ", "KMB", "LEG", "LOW", "SYY", "EMR", "SHW", "WMT", "CINF", "CTAS", "PX",
      "ROP", "SPG", "ITW", "MCD", "PEP", "VFC", "ADP", "CL", "CLX", "FRT", "KO", "MMM", "BF-A" };

  // Dividend Champions for 2018 from: http://www.dripinvesting.org/tools/tools.asp
  public static final String[]      allChampions   = new String[] { "SRCE", "MMM", "AOS", "ABM", "AFL", "APD", "MO",
      "AWR", "WTR", "ADM", "T", "ATO", "ADP", "BMI", "BDX", "BMS", "BKH", "BRC", "BF-B", "CWT", "TYCB", "CSL", "CPKF",
      "CVX", "CINF", "CTAS", "CLX", "KO", "CL", "CBSH", "CBU", "CTBI", "CSVI", "CTWS", "ED", "DCI", "DOV", "EFSI", "EV",
      "ECL", "EMR", "ERIE", "XOM", "FMCB", "FRT", "THFF", "FELE", "BEN", "GD", "GPC", "GRC", "FUL", "HP", "HRL", "ITW",
      "JKHY", "JNJ", "KMB", "LANC", "LEG", "LOW", "MKC", "MCD", "MGRC", "MDU", "MDT", "MCY", "MDP", "MGEE", "MSEX",
      "MSA", "NC", "NFG", "NNN", "NDSN", "NWN", "NUE", "ORI", "PH", "PNR", "PBCT", "PEP", "PPG", "PX", "PG", "O", "RLI",
      "ROP", "RPM", "SPGI", "SEIC", "SHW", "SJW", "SON", "SWK", "SCL", "SYK", "SYY", "TROW", "TGT", "TDS", "TNC", "TMP",
      "TR", "UGI", "UMBF", "UBSI", "UVV", "UHT", "VVC", "VFC", "GWW", "WBA", "WMT", "WST", "WABC", "WEYS", "WGL" };

  // Dividend Contenders for 2018 from: http://www.dripinvesting.org/tools/tools.asp
  public static final String[]      allContenders  = new String[] { "AAN", "ACN", "ACU", "ALB", "LNT", "AEL", "AFG",
      "AWK", "APU", "AMP", "ABC", "AFSI", "ADI", "ANDE", "ATR", "AMNF", "AROW", "ARTNA", "AIZ", "ATRI", "AUBN", "AVA",
      "AXS", "BANF", "BMRC", "OZRK", "BKUTK", "BHB", "BBY", "BOKF", "EAT", "BR", "BIP", "BRO", "BPL", "BG", "CHRW",
      "CATC", "CNI", "CAH", "CASY", "CASS", "CAT", "CCFN", "CNP", "CPK", "CB", "CHD", "CZFS", "CMS", "COLM", "CMCSA",
      "CMP", "COST", "CBRL", "CSX", "CFR", "CMI", "CVS", "DLR", "D", "DGICA", "DGICB", "DUK", "DNB", "EBMT", "EIX",
      "ENB", "ETE", "ETP", "ENSG", "EBTC", "EPD", "ELS", "ESS", "EVR", "ES", "EXPD", "FDS", "FMAO", "FAST", "FDX",
      "FINL", "FFMR", "FLIC", "FRFC", "FLO", "FLS", "GIS", "GGG", "THG", "HRS", "HAS", "HWKN", "HDB", "HCSG", "HEI",
      "HI", "HIFS", "HEP", "HONT", "HUBB", "IPCC", /* "IMASF", */ "IBM", "IFF", "ISCA", "ISBA", "JJSF", "SJM", "JBHT",
      "JW-A", "K", "KR", "LLL", "LARK", "LSTR", "LAZ", "LMNR", "LECO", "LNN", "LMT", "LYBC", "MMP", "MATW", "MXIM",
      "MCK", "MCHP", "MSFT", "MNRO", "MSM", "MYBF", "NKSH", "NHI", "NHC", "NJR", "NEU", "NEE", "NKE", "NIDB", "NOC",
      "NWE", "NWFL", "NUS", "OXY", "OGE", "ODC", "OHI", "OKE", "OMI", "PRGO", "PETS", "PM", "PII", "POR", "PPL", "PFG",
      "PB", "PSBQ", "QNTO", "KWR", "QCOM", "RTN", "RBC", "RNR", "RBCAA", "RSG", "RGCO", "RBA", "RHI", "ROL", "ROST",
      "RGLD", "R", "SCG", "SRE", "SXT", "SHPG", "SLGN", "SJI", "SO", "SBSI", "SWX", "SEP", "SR", "STE", "SKT", "TCP",
      "TXN", "TPL", "THVB", "TRI", "TIF", "TJX", "TMK", "TLP", "TRV", "UNP", "UTX", "UBA", "UTMD", "VGR", "VZ", "V",
      "VSEC", "WPC", "WRB", "WM", "WEC", "HCN", "WR", "WES", "WLK", "WHG", "WSM", "XEL", "XLNX", "YORW" };

  public static final String[]      indexFunds     = new String[] { "VFINX", "VBMFX", "VWIGX", "VFISX" };
  public static final String[]      allSymbols;

  static {
    // Combine all symbols into one list.
    String[][] lists = new String[][] { allAristocrats, allChampions, allContenders, indexFunds };
    Set<String> symbols = new LinkedHashSet<String>();
    for (String[] list : lists) {
      for (String symbol : list) {
        symbols.add(symbol);
      }
    }
    allSymbols = new String[symbols.size()];
    symbols.toArray(allSymbols);
  }

  private static String[] filterList(String[] all, SequenceStore store)
  {
    List<String> keep = new ArrayList<String>();
    for (String name : all) {
      if (store.hasName(name)) keep.add(name);
    }
    return keep.toArray(new String[keep.size()]);
  }

  private static String[] merge(String[]... lists)
  {
    Set<String> all = new LinkedHashSet<String>();
    for (String[] list : lists) {
      for (String name : list) {
        all.add(name);
      }
    }
    return all.toArray(new String[all.size()]);
  }

  public static void main(String[] args) throws IOException
  {
    double startingBalance = 10000.0;
    double monthlyDeposit = 0.0;

    final long startThreshold = TimeLib.toMs(1992, Month.JANUARY, 1);
    TiingoFundFilter filter = new TiingoFundFilter()
    {
      @Override
      public boolean accept(TiingoFund fund)
      {
        return TimeLib.toMs(fund.start) < startThreshold;
      }
    };
    Simulation sim = Tiingo.setupSimulation(allSymbols, startingBalance, monthlyDeposit, Slippage.None, filter, store);
    String[] aristocrats = filterList(allAristocrats, store);
    String[] champions = filterList(allChampions, store);
    String[] contenders = filterList(allContenders, store);
    String[] kingdom = merge(aristocrats, champions, contenders);

    System.out.printf("Aristocrats: %d / %d\n", aristocrats.length, allAristocrats.length);
    System.out.printf("Champions: %d / %d\n", champions.length, allChampions.length);
    System.out.printf("Contenders: %d / %d\n", contenders.length, allContenders.length);
    System.out.printf("Kingdom: %d\n", kingdom.length);

    long commonStart = store.getCommonStartTime();
    long commonEnd = store.getCommonEndTime();
    // long timeSimStart = commonStart;
    long timeSimStart = TimeLib
        .toMs(TimeLib.ms2date(commonStart).plusWeeks(53 + 4).with(TemporalAdjusters.firstDayOfMonth()));
    // timeSimStart = TimeLib.toMs(2013, Month.JANUARY, 1);
    long timeSimEnd = commonEnd;
    // timeSimEnd = TimeLib.toMs(2012, Month.DECEMBER, 31);
    double nSimMonths = TimeLib.monthsBetween(timeSimStart, timeSimEnd);
    System.out.printf("Simulation: [%s] -> [%s] (%.1f months total)\n", TimeLib.formatDate(timeSimStart),
        TimeLib.formatDate(timeSimEnd), nSimMonths);
    assert timeSimStart < timeSimEnd;

    // Simulate different portfolios.
    List<Sequence> returns = new ArrayList<>();
    Predictor predictor;
    StandardPortfolios portfolios = new StandardPortfolios(sim);
    List<ComparisonStats> compStats = new ArrayList<>();
    String safeAsset = "VFISX"; // "VBMFX";

    // Baseline = S&P 500.
    predictor = portfolios.passive("S&P 500", "VFINX");
    Sequence returnsStock = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsStock);

    // Dividend Aristocrats using historical data.
    // File file = new File(DataIO.financePath, "historical-aristocrats-returns.txt");
    // Sequence returnsHistoricalAristocrats = DataIO.loadDateValueCSV(file);
    // returnsHistoricalAristocrats = returnsHistoricalAristocrats.matchTimes(returnsStock, 1000);
    // FinLib.normalizeReturns(returnsHistoricalAristocrats);
    // System.out.println(CumulativeStats.calc(returnsHistoricalAristocrats));
    // returns.add(returnsHistoricalAristocrats);
    // compStats.add(ComparisonStats.calc(returnsHistoricalAristocrats, 0.5, returnsStock));

    // Dividend Aristocrats = equal weighting for all funds in the aristocrat list.
    predictor = portfolios.passiveEqualWeight("Aristocrats", aristocrats);
    Sequence returnsAristocrats = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsAristocrats);
    compStats.add(ComparisonStats.calc(returnsAristocrats, 0.5, returnsStock));

    // Dividend Champions = equal weighting for all funds in the champions list.
    predictor = portfolios.passiveEqualWeight("Champions", champions);
    Sequence returnsChampions = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsChampions);
    compStats.add(ComparisonStats.calc(returnsChampions, 0.5, returnsStock));

    // Dividend Contenders = equal weighting for all funds in the contenders list.
    Predictor contenderPred = portfolios.passiveEqualWeight("Contenders", contenders);
    Sequence returnsContenders = portfolios.run(contenderPred, timeSimStart, timeSimEnd);
    returns.add(returnsContenders);
    compStats.add(ComparisonStats.calc(returnsContenders, 0.5, returnsStock));

    // Dividend Kingdom = equal weighting for all funds on all lists.
    Predictor kingdomPred = portfolios.passiveEqualWeight("Kingdom", kingdom);
    Sequence returnsKingdom = portfolios.run(kingdomPred, timeSimStart, timeSimEnd);
    returns.add(returnsKingdom);
    compStats.add(ComparisonStats.calc(returnsKingdom, 0.5, returnsStock));

    // Lazy 2-fund portfolio.
    predictor = portfolios.passive("Lazy2 [80/20]", new String[] { "VFINX", "VBMFX" }, 0.8, 0.2);
    Sequence returnsLazy2 = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsLazy2);
    compStats.add(ComparisonStats.calc(returnsLazy2, 0.5, returnsStock));

    // Lazy 3-fund portfolio.
    predictor = portfolios.passiveEqualWeight("Lazy3", "VFINX", "VBMFX", "VWIGX");
    Sequence returnsLazy3 = portfolios.run(predictor, timeSimStart, timeSimEnd);
    returns.add(returnsLazy3);
    compStats.add(ComparisonStats.calc(returnsLazy3, 0.5, returnsStock));

    // Tactical Allocation (S&P).
    PredictorConfig tacticalConfig = new ConfigTactical(FinLib.Close, "VFINX", safeAsset);
    Predictor tacticalPred = tacticalConfig.build(sim.broker.accessObject, indexFunds);
    sim.run(tacticalPred, timeSimStart, timeSimEnd, "Tactical");
    System.out.println(CumulativeStats.calc(sim.returnsMonthly));
    returns.add(sim.returnsMonthly);
    compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, returnsStock));

    // Dual Momentum.
    Predictor dualMomPred = portfolios.dualMomentum(1, safeAsset, "VFINX", "VWIGX");
    Sequence returnsDualMom = portfolios.run(dualMomPred, timeSimStart, timeSimEnd);
    returns.add(returnsDualMom);
    compStats.add(ComparisonStats.calc(sim.returnsMonthly, 0.5, returnsStock));
    Chart.saveHoldings(new File(DataIO.outputPath, "holdings-dual-momentum.html"), sim.holdings, sim.store);

    // Gated Dividends.
    String[] assets = merge(kingdom, new String[] { "VFINX", safeAsset });
    assert assets[assets.length - 1].equals(safeAsset);
    Predictor gated = new GatedPredictor(kingdomPred, tacticalPred, sim.broker.accessObject, assets);
    gated.name = "Gated Kingdom";
    Sequence returnsGated = portfolios.run(gated, timeSimStart, timeSimEnd);
    returns.add(returnsGated);
    compStats.add(ComparisonStats.calc(returnsGated, 0.5, returnsStock));

    // Save reports: graph of returns + comparison summary.
    Chart.saveLineChart(new File(DataIO.outputPath, "returns.html"), "Total Returns", 1000, 640,
        ChartScaling.LOGARITHMIC, ChartTiming.MONTHLY, returns);
    Chart.saveComparisonTable(new File(DataIO.outputPath, "comparison.html"), 1000, compStats);

    // Report: comparison of returns over next N months.
    for (int nMonths : new int[] { 12 * 5, 12 * 10 }) {
      List<Sequence> durationalReturns = new ArrayList<>();
      for (Sequence r : returns) {
        Sequence seq = FinLib.calcReturnsForMonths(r, nMonths);
        seq.setName(r.getName());
        durationalReturns.add(seq);
      }
      String title = String.format("Total Returns (Next %s)", TimeLib.formatDurationMonths(nMonths));
      File file = new File(DataIO.outputPath, String.format("duration-returns-%d-months.html", nMonths));
      Chart.saveLineChart(file, title, 1000, 640, ChartScaling.LINEAR, ChartTiming.MONTHLY, durationalReturns);
    }

    // TiingoFund fund = TiingoFund.fromSymbol("T", true);
    // for (FeatureVec x : fund.data) {
    // double div = x.get(FinLib.DivCash);
    // double split = x.get(FinLib.SplitFactor);
    // assert div >= 0.0;
    // if (split != 1.0) {
    // System.out.printf("%s: %g (split)\n", TimeLib.formatDate2(x.getTime()), split);
    // }
    // if (div > 0.0) {
    // System.out.printf("%s: %g (div)\n", TimeLib.formatDate2(x.getTime()), div);
    // }
    // }
  }
}
