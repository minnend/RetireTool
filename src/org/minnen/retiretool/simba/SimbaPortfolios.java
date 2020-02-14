package org.minnen.retiretool.simba;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.simba.Portfolio.Strictness;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.stats.ReturnStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

/**
 * Explore passive asset allocations. The "simba data" gives annual returns across a wide range of asset classes. This
 * data can be used to simulate and compare different fixed portfolios.
 *
 * Inspiration: https://portfoliocharts.com/2016/03/07/the-ultimate-portfolio-guide-for-all-types-of-investors/ Simba's
 * data (thread): https://www.bogleheads.org/forum/viewtopic.php?t=2520
 */
public class SimbaPortfolios
{
  public static final Mode                 mode                = Mode.Evaluate;

  public static final int                  nPeriodYears        = 10;
  public static final double               principle           = 1000;
  public static final double               contribution        = 0;
  public static final Strictness           domStrictness       = Strictness.Normal;
  public static final boolean              bAdjustForInflation = false;
  public static final boolean              bPrintStartYearInfo = false;
  public static final int                  nSigDig             = 0;

  public static final Map<String, Integer> stat2index;
  public static final String[]             statNames;

  public static final Map<String, Integer> symbol2index;
  public static String[]                   symbols;                                // VTSMX, VIVAX, etc.
  public static String[]                   descriptions;                           // TSM, LCV, etc.
  public static Sequence[]                 returnSeqs;                             // one per symbol

  public static final String[]             universe;                               // list of symbols to explore
  public static final Map<String, Integer> symbolCap;                              // max percentage for each symbol
  public static final List<Set<String>>    requiredSets;                           // must use one symbol in each set

  public static final double[]             domDir;
  public static final double[]             domThresholds;

  public static final List<Portfolio>      defenders;

  public enum Mode {
    Generate, Filter, Evaluate
  };

  // TODO per-symbol minimum
  // TODO better labels on charts: inflation? start year?

  static {
    symbol2index = new HashMap<>();
    defenders = new ArrayList<>();

    // Build reverse map from statistic name to index.
    statNames = new String[] { "Worst Period", "10th Percentile", "Median", "CAGR", "Std Dev", "Max Drawdown" };
    stat2index = new HashMap<>();
    for (int i = 0; i < statNames.length; ++i) {
      stat2index.put(statNames[i], i);
    }

    domDir = new double[] { 1, 1, 1, 1, 0, 1 }; // ignore std dev
    domThresholds = new double[] { 0.1, 0.1, 0.1, 0.1, 0.1, 1.0 };
    assert domDir.length == domThresholds.length;
    assert domDir.length == statNames.length;

    // Define universe of symbols and constraints on portfolios.
    universe = new String[] { // only symbols in the universe are considered for each portfolio
        "VBMFX", "VCIT", "VUSXX", // total bond, intermediate corp bonds, treasuries
        // "FSAGX", // precious metals
        // "GSG", // commodities
        "VGSIX", // REITs
        "VGTSX", "EFV", // international: total, value
        "VTSMX", // total U.S. market
        "VFINX", // large-cap
        "VIVAX", "VMVIX", "VISVX", // value: large, mid, small
        "VIGRX", "VMGIX", "VISGX", // growth: large, mid, small
        // "BRSIX", // micro-cap
        "VFSVX", // international small-cap (from 1975)
        "VEIEX", // emerging markets (from 1976)
        "VHDYX", // high dividend yield (from 1976)
        // "VGENX", // energy (from 1985)
        // "VGHCX", // health care (from 1985)
    };

    // Additional allocation limits for specific asset classes.
    symbolCap = new HashMap<>();
    String[] symbols = new String[] { "FSAGX", "IAU", "GSG", "BRSIX", "VGENX", "VGHCX", "VUSXX" };
    for (String symbol : symbols) {
      symbolCap.put(symbol, 10); // no more than 10% for each symbol
    }
    // symbols = new String[] { "VGSIX", "VBMFX", "VCIT" };
    // for (String symbol : symbols) {
    // symbolCap.put(symbol, 20); // no more than 20% for each symbol
    // }

    // Create sets of required symbols (at least one per set must be used).
    requiredSets = new ArrayList<>();
    requiredSets.add(new HashSet<>(Arrays.asList("VIVAX", "VIGRX", "VHDYX", "VTSMX", "VFINX"))); // large cap
    requiredSets.add(new HashSet<>(Arrays.asList("VGTSX", "EFV", "VFSVX", "VEIEX"))); // international
    // requiredSets.add(new HashSet<>(Arrays.asList("VBMFX", "VCIT", "VUSXX"))); // cash-like
    // requiredSets.add(new HashSet<>(Arrays.asList("FSAGX", "GSG", "VGSIX"))); // stock/bond alternative
    requiredSets.add(new HashSet<>(Arrays.asList("VGSIX"))); // must have REITs
    // requiredSets.add(new HashSet<>(Arrays.asList("FSAGX"))); // must have gold
  }

  /** @return boolean array matching `symbols` where true elements should be avoided (never used in a portfolio). */
  private static boolean[] buildUniverseMask(String... okNames)
  {
    boolean[] avoid = new boolean[symbols.length];
    Arrays.fill(avoid, true); // to start, assume we'll avoid everything
    for (String name : okNames) {
      avoid[symbol2index.get(name)] = false;
    }
    return avoid;
  }

  private static void loadSimbaData(File file) throws IOException
  {
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read Simba CSV file (%s)", file.getPath()));
    }
    System.out.printf("Loading Simba CSV data file: [%s]\n", file.getPath());
    try (BufferedReader in = new BufferedReader(new FileReader(file))) {
      String line;
      int nLines = 0;
      int nCols = 0;
      int prevYear = -1;
      while ((line = in.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) continue;
        ++nLines;
        String[] toks = line.trim().split(",");
        if (nCols == 0) {
          nCols = toks.length;
        } else if (toks.length != nCols) {
          throw new IOException(
              String.format("Inconsistent number of columns: %d vs. %d (line=%d)\n", toks.length, nCols, nLines));
        }
        // System.out.printf("%d: %d |%s\n", nLines, toks.length, line);

        if (nLines == 1) {
          assert toks[0].equals("Name") || toks[0].isEmpty();
          descriptions = new String[toks.length - 1];
          System.arraycopy(toks, 1, descriptions, 0, descriptions.length);
        } else if (nLines == 2) {
          assert toks[0].equals("Symbol") || toks[0].isEmpty();
          symbols = new String[toks.length - 1];
          System.arraycopy(toks, 1, symbols, 0, descriptions.length);
          returnSeqs = new Sequence[symbols.length];
          for (int i = 0; i < symbols.length; ++i) {
            returnSeqs[i] = new Sequence(symbols[i]);
            symbol2index.put(symbols[i], i);
          }
        } else {
          try {
            int year = Integer.parseInt(toks[0]);
            if (prevYear > 0 && year != prevYear + 1) {
              throw new IOException(
                  String.format("Non-consecutive year: %d after %d (line=%d)\n", year, prevYear, nLines));
            }
            for (int i = 1; i < toks.length; ++i) {
              if (toks[i].isEmpty()) continue;
              double r = Double.parseDouble(toks[i]);
              double m = FinLib.ret2mul(r); // easier to work with multipliers (1.4 = 40% growth)
              long ms = TimeLib.toMs(year, Month.DECEMBER, 31);
              if (!returnSeqs[i - 1].isEmpty()) {
                long prevMs = returnSeqs[i - 1].getEndMS();
                LocalDate prevDate = TimeLib.ms2date(prevMs);
                assert prevDate.getYear() + 1 == year;
              }
              returnSeqs[i - 1].addData(m, ms);
            }
          } catch (NumberFormatException e) {
            System.err.printf("Error parsing CSV data: [%s]\n", line);
            break;
          }
        }
      }
    }
  }

  /** @return time (in ms) for the given year index */
  private static long index2ms(int iYear)
  {
    return returnSeqs[getFirstReturnIndex()].getTimeMS(iYear);
  }

  /** Adjust returns (in-place) to account for inflation. */
  private static void adjustForInflation(Map<Integer, Double> inflation)
  {
    for (int i = 0; i < returnSeqs.length; ++i) {
      for (FeatureVec x : returnSeqs[i]) {
        int year = TimeLib.ms2date(x.getTime()).getYear();
        assert inflation.containsKey(year);
        double cpi = inflation.get(year);
        x._div(cpi);
      }
    }
  }

  /** @return Return multiplier (1.4 = 40%) for the allocation int the given year. */
  private static double returnForYear(int iYear, DiscreteDistribution allocation)
  {
    double m = 0.0;
    for (int i = 0; i < allocation.size(); ++i) {
      double w = allocation.weights[i];
      if (w < 1e-9) continue;
      Sequence assetReturns = returnSeqs[symbol2index.get(allocation.names[i])];
      m += w * assetReturns.get(iYear, 0);
    }
    return m;
  }

  /** @return index of first non-null return sequence. */
  private static int getFirstReturnIndex()
  {
    // TODO cache result or simplify data by dropping unused symbols.
    for (int i = 0; i < returnSeqs.length; ++i) {
      if (returnSeqs[i] != null) return i;
    }
    return -1;
  }

  /** @return sequence of returns (1.4 = 1.4%, not cumulative) per year for the given allocation. */
  private static Sequence runPortfolio(DiscreteDistribution targetAllocation)
  {
    int nYears = returnSeqs[getFirstReturnIndex()].size();
    String name = targetAllocation.toStringWithNames(nSigDig);
    Sequence returns = new Sequence(name);
    returns.setMeta("allocation", targetAllocation);
    for (int iYear = 0; iYear < nYears; ++iYear) {
      double m = returnForYear(iYear, targetAllocation);
      returns.addData(FinLib.mul2ret(m), index2ms(iYear));
    }
    return returns;
  }

  /** @return true if the `weights` are valid according to the min/max restrictions. */
  private static boolean isValid(int[] weights, int minAssets, int maxAssets, int minWeight, int maxWeight)
  {
    int totalWeight = 0;
    int nAssets = 0;
    for (int i = 0; i < weights.length; ++i) {
      assert weights[i] >= 0 && weights[i] <= 100;
      if (weights[i] == 0) continue;

      ++nAssets;
      totalWeight += weights[i];
      if (weights[i] < minWeight || weights[i] > maxWeight) return false;
      if (weights[i] > symbolCap.getOrDefault(symbols[i], 100)) return false;
    }
    if (totalWeight != 100) return false;
    if (nAssets < minAssets || nAssets > maxAssets) return false;
    return true;
  }

  /** @return DiscreteDistribution object representing the given `weights`. */
  public static DiscreteDistribution buildDistribution(int[] weights)
  {
    assert weights.length == symbols.length;
    DiscreteDistribution dist = new DiscreteDistribution(symbols);
    for (int i = 0; i < weights.length; ++i) {
      dist.weights[i] = weights[i] / 100.0;
    }
    return dist;
  }

  /** @return true if the portfolio represented by `weights` meets the `requiredSets` constraints. */
  private static boolean hasRequiredSets(int[] weights)
  {
    // Build set for symbols with positive weight.
    Set<String> symbolsInPortfolio = new HashSet<String>();
    for (int i = 0; i < weights.length; ++i) {
      if (weights[i] <= 0) continue;
      symbolsInPortfolio.add(symbols[i]);
    }

    for (Set<String> set : requiredSets) {
      set = new HashSet<String>(set); // copy set to avoid modifying the original
      set.retainAll(symbolsInPortfolio); // calculate intersection
      if (set.isEmpty()) return false;
    }
    return true;
  }

  /** Generates all valid portfolios and stores them in the `portfolios` list. */
  private static void scanDistributions(int minAssets, int maxAssets, int minWeight, int maxWeight, int weightStep,
      boolean[] avoid, List<DiscreteDistribution> portfolios)
  {
    int[] weights = new int[returnSeqs.length];
    scanDistributionsHelper(weights, 0, 0, 100, minAssets, maxAssets, minWeight, maxWeight, weightStep, avoid,
        portfolios);
  }

  /** Helper function for scanDistributions(). */
  private static void scanDistributionsHelper(int[] weights, int index, int nAssetsSoFar, int weightLeft, int minAssets,
      int maxAssets, int minWeight, int maxWeight, int weightStep, boolean[] avoid,
      List<DiscreteDistribution> portfolios)
  {
    assert weightLeft >= 0 && weightLeft <= 100;
    if (weightLeft == 0) { // reached the end of this path but result may not be valid
      assert nAssetsSoFar <= maxAssets;
      if (nAssetsSoFar < minAssets) return;
      if (!hasRequiredSets(weights)) return;

      assert isValid(weights, minAssets, maxAssets, minWeight, maxWeight);
      DiscreteDistribution dist = buildDistribution(weights);
      portfolios.add(dist);
      if (portfolios.size() % 100000 == 0) {
        System.out.printf("%d\n", portfolios.size());
      }
    }

    if (index >= weights.length) return; // no more assets
    if (weightLeft < minWeight) return; // not enough weight left to meet min constraint
    if (nAssetsSoFar >= maxAssets) return; // will break the max assets constraint with any more assets

    // More weight (portfolio percentage) to distribute so continue DFS.
    if (avoid == null | !avoid[index]) {
      // Try assigning all valid weights to asset[index].
      String symbol = symbols[index];
      int maxWeightForAsset = Library.min(maxWeight, weightLeft, symbolCap.getOrDefault(symbol, 100));
      for (int w = maxWeightForAsset; w >= minWeight; w -= weightStep) {
        weights[index] = w;
        scanDistributionsHelper(weights, index + 1, nAssetsSoFar + 1, weightLeft - w, minAssets, maxAssets, minWeight,
            maxWeight, weightStep, avoid, portfolios);
      }
    }

    // Skip asset[index].
    weights[index] = 0;
    scanDistributionsHelper(weights, index + 1, nAssetsSoFar, weightLeft, minAssets, maxAssets, minWeight, maxWeight,
        weightStep, avoid, portfolios);
  }

  /** @return Sequence containing CAGR for each `nYears` period. */
  private static Sequence calcPeriodReturns(Sequence cumulativeReturns, int nYears)
  {
    Sequence seq = new Sequence(cumulativeReturns.getName() + String.format(" %d year returns", nYears));
    seq.copyMeta(cumulativeReturns);
    int nMonths = (int) Math
        .round(TimeLib.monthsBetween(cumulativeReturns.getTimeMS(0), cumulativeReturns.getTimeMS(nYears)));
    assert nMonths == nYears * 12;
    final int n = cumulativeReturns.length();
    for (int i = 0; i + nYears < n; ++i) {
      double tr = FinLib.getTotalReturn(cumulativeReturns, i, i + nYears);
      double cagr = FinLib.getAnnualReturn(tr, nMonths);
      seq.addData(cagr, cumulativeReturns.getTimeMS(i));
    }
    return seq;
  }

  /** Simulate the given portfolio and calculate metrics. */
  private static FeatureVec getStats(DiscreteDistribution allocation)
  {
    return getStats(allocation, false);
  }

  private static FeatureVec getStats(DiscreteDistribution allocation, boolean bSaveAllAsMeta)
  {
    String name = allocation.toStringWithNames(nSigDig);
    Sequence annualReturns = runPortfolio(allocation);
    Sequence cumulativeReturns = FinLib.cumulativeFromReturns(annualReturns, principle, contribution);
    Sequence periodReturns = calcPeriodReturns(cumulativeReturns, nPeriodYears);
    ReturnStats rstatsPeriod = ReturnStats.calc(name, periodReturns.extractDim(0));
    CumulativeStats cstats = CumulativeStats.calc(cumulativeReturns, false);
    FeatureVec v = new FeatureVec(name, statNames.length);
    v.setMeta("allocation", allocation);
    v.set(stat2index.get("Worst Period"), rstatsPeriod.min);
    v.set(stat2index.get("10th Percentile"), rstatsPeriod.percentile10);
    v.set(stat2index.get("Median"), rstatsPeriod.median);
    v.set(stat2index.get("CAGR"), cstats.cagr);
    v.set(stat2index.get("Std Dev"), rstatsPeriod.sdev);
    v.set(stat2index.get("Max Drawdown"), -cstats.drawdown);

    if (bSaveAllAsMeta) {
      v.setMeta("annualReturns", annualReturns);
      v.setMeta("cumulativeReturns", cumulativeReturns);
      v.setMeta("cumulativestats", cstats);
    }

    return v;
  }

  private static List<Portfolio> generate() throws IOException
  {
    List<DiscreteDistribution> allocations = new ArrayList<>();
    boolean[] avoid = buildUniverseMask(universe);
    // TODO setup scan at top of class (create config object?)
    scanDistributions(3, 6, 10, 30, 10, avoid, allocations);
    System.out.printf("Portfolios: %d\n", allocations.size());
    if (allocations.isEmpty()) return new ArrayList<>();

    System.out.println("Calculate Returns...");
    long start = TimeLib.getTime();
    List<Portfolio> portfolios = new ArrayList<>();
    for (int i = 0; i < allocations.size(); ++i) {
      DiscreteDistribution allocation = allocations.get(i);
      FeatureVec stats = getStats(allocation);
      portfolios.add(new Portfolio(allocation, stats));
      if (i % 100000 == 0 && i > 0) System.out.printf("%d  (%.1f%%)\n", i, 100.0 * (i + 1) / allocations.size());
    }
    System.out.printf("Time: %s  (%d)\n", TimeLib.formatDuration(TimeLib.getTime() - start), allocations.size());

    // Filter results by removing portfolios that are "dominated" by another portfolio.
    System.out.println("Remove dominated portfolios...");
    portfolios = Portfolio.removeNonDominators(portfolios, domDir, domThresholds, domStrictness);
    System.out.printf("Filtered: %d\n", portfolios.size());

    portfolios = blendPortfolios(portfolios);
    System.out.printf("Blended: %d\n", portfolios.size());

    // Sort portfolios and save description and metrics to a text file.
    Collections.sort(portfolios);
    Portfolio.savePortfolios(getDominatingFile(), portfolios);

    return portfolios;
  }

  private static void genCharts(List<Portfolio> portfolios) throws IOException
  {
    if (portfolios == null || portfolios.isEmpty()) return;

    // { "Worst Period", "10th Percentile", "Median", "CAGR", "Std Dev", "Max Drawdown" }
    final int[][] goodCharts = new int[][] { { 0, 3 }, { 1, 3 }, { 2, 3 }, { 5, 2 }, { 5, 3 }, { 0, 2 }, { 0, 1 } };
    Sequence portfolioStats = new Sequence(portfolios.stream().map(Portfolio::getStats).collect(Collectors.toList()));
    for (int iChart = 0; iChart < goodCharts.length; ++iChart) {
      int i = goodCharts[iChart][0];
      int j = goodCharts[iChart][1];
      String descX = statNames[i];
      String descY = statNames[j];
      String filename = String.format("simba-filtered-%d-%d.html", i, j);
      String title = String.format("%s vs. %s (%d-year Periods)", descY, descX, nPeriodYears);
      ChartConfig chartConfig = new ChartConfig(new File(DataIO.getOutputPath(), filename))
          .setType(ChartConfig.Type.Scatter).setTitle(title).setYAxisTitle(descY).setXAxisTitle(descX)
          .setSize("100%", "100%").setRadius(3).setDimNames(statNames).setData(portfolioStats).showToolTips(true)
          .setIndexXY(i, j);
      Chart.saveScatterPlot(chartConfig);
    }
  }

  private static Sequence getForwardReturns(Sequence annualReturns, int nYears)
  {
    Sequence forwardReturns = new Sequence(annualReturns.getName());
    for (int i = 0; i + nYears <= annualReturns.size(); ++i) {
      double m = FinLib.ret2mul(annualReturns.get(i, 0));
      for (int j = 1; j < nYears; ++j) {
        m *= FinLib.ret2mul(annualReturns.get(i + j, 0));
      }

      m = Math.pow(m, 1.0 / nYears); // annualized return
      forwardReturns.addData(FinLib.mul2ret(m), annualReturns.getTimeMS(i));
    }
    return forwardReturns;
  }

  private static WinStats calcWinStats(Portfolio portfolio1, Portfolio portfolio2, String metaName, double threshold)
  {
    Sequence annual1 = (Sequence) portfolio1.getStats().getMeta(metaName);
    Sequence annual2 = (Sequence) portfolio2.getStats().getMeta(metaName);
    assert annual1.matches(annual2) : System.out.printf("%s  %s\n", annual1, annual2);

    int nWin = 0;
    int nLose = 0;
    int nTie = 0;
    int nWinStreak = 0;
    int nLoseStreak = 0;
    int nLongestWinStreak = 0;
    int nLongestLoseStreak = 0;
    for (int i = 0; i < annual1.length(); ++i) {
      double r1 = annual1.get(i, 0);
      double r2 = annual2.get(i, 0);

      double diff = r1 - r2;
      if (diff >= threshold) {
        ++nWin;
        nLoseStreak = 0;
        ++nWinStreak;
      } else if (diff <= -threshold) {
        ++nLose;
        nWinStreak = 0;
        ++nLoseStreak;
      } else {
        ++nTie;

        // Ties extend streak in both directions.
        // TODO could be smarter, e.g. only extend if a real win/loss follows.
        if (nWinStreak > 0) ++nWinStreak;
        else if (nLoseStreak > 0) ++nLoseStreak;
      }

      if (nLoseStreak > nLongestLoseStreak) {
        nLongestLoseStreak = nLoseStreak;
      } else if (nWinStreak > nLongestWinStreak) {
        nLongestWinStreak = nWinStreak;
      }
    }

    return new WinStats(nWin, nTie, nLose, nLongestWinStreak, nLongestLoseStreak);
  }

  private static WinStats printComparison(Portfolio portfolio1, Portfolio portfolio2, String metaName, double threshold)
  {
    WinStats stats = calcWinStats(portfolio1, portfolio2, metaName, threshold);
    System.out.printf(" %s vs. %-10s  WTL=[%d, %d, %d]    streak=[%d, %d]\n", portfolio1.name, portfolio2.name,
        stats.nWin, stats.nTie, stats.nLose, stats.nLongestWinStreak, stats.nLongestLoseStreak);
    return stats;
  }

  private static void genComparisonCharts(List<Portfolio> defenders, List<Portfolio> challengers) throws IOException
  {
    List<Portfolio> portfolios = new ArrayList<>(defenders);
    portfolios.addAll(challengers);

    List<Sequence> cumulatives = new ArrayList<>();
    int[] durations = new int[] { 1, 5, 10, 20 };
    Map<Integer, List<Sequence>> rollingReturns = new TreeMap<>();
    for (int n : durations)
      rollingReturns.put(n, new ArrayList<>());

    for (Portfolio portfolio : portfolios) {
      FeatureVec stats = portfolio.getStats();
      Sequence cumulative = (Sequence) stats.getMeta("cumulativeReturns");
      cumulative.setName(portfolio.name);
      cumulatives.add(cumulative);

      Sequence annual = (Sequence) stats.getMeta("annualReturns");
      annual.setName(portfolio.name);
      rollingReturns.get(1).add(annual);

      for (int years : durations) {
        if (years <= 1) continue;
        Sequence returns = getForwardReturns(annual, years);
        rollingReturns.get(years).add(returns);
        stats.setMeta(String.format("forward-%d", years), returns);
      }
    }

    String metaName = "forward-10";
    for (Portfolio challenger : challengers) {
      System.out.printf("Returns: %s\n", metaName);
      for (Portfolio portfolio : portfolios) {
        // Calculate regret periods.
        if (portfolio != challenger) {
          printComparison(challenger, portfolio, metaName, 0.3);
        }
      }
    }

    // Growth chart.
    Chart.saveLineChart(new File(DataIO.getOutputPath(), "simba-growth-curves.html"), "Passive Growth Curves", "100%",
        "800px", ChartScaling.LOGARITHMIC, ChartTiming.ANNUAL, cumulatives);

    // Bar chart with forward returns.
    for (int years : durations) {
      List<Sequence> returns = rollingReturns.get(years);
      String filename = String.format("simba-returns-%d-year.html", years);
      String title = String.format("Forward %d-year Returns", years);
      ChartConfig config = ChartConfig.buildLine(new File(DataIO.getOutputPath(), filename), title, "100%", "600px",
          ChartScaling.LINEAR, ChartTiming.ANNUAL, returns);
      Chart.saveChart(config);
    }
  }

  public static File getDominatingFile()
  {
    return new File(DataIO.getFinancePath(), "simba-dominating.txt");
  }

  public static File getFinalFile()
  {
    return new File(DataIO.getFinancePath(), "simba-final.txt");
  }

  /** @return map from year to inflation. */
  private static Map<Integer, Double> buildInflationMap()
  {
    Map<Integer, Double> inflation = new HashMap<Integer, Double>();
    int index = symbol2index.get("CPI-U");
    for (FeatureVec x : returnSeqs[index]) {
      LocalDate date = TimeLib.ms2date(x.getTime());
      inflation.put(date.getYear(), x.get(0));
    }
    return inflation;
  }

  /** @return map from year to list of symbols that have data for that year. */
  private static Map<Integer, List<String>> buildYearMap()
  {
    // Build map of year to symbols that start in that year.
    Map<Integer, List<String>> startToSymbols = new TreeMap<>();
    for (Sequence seq : returnSeqs) {
      int year = getStartYear(seq);
      if (!startToSymbols.containsKey(year)) {
        startToSymbols.put(year, new ArrayList<String>());
      }
      List<String> symbols = startToSymbols.get(year);
      symbols.add(seq.getName());
    }

    // Build map of year to all symbols that start in or before that year.
    List<String> prevSymbols = new ArrayList<>();
    Map<Integer, List<String>> yearToSymbols = new TreeMap<>();
    for (Map.Entry<Integer, List<String>> x : startToSymbols.entrySet()) {
      int year = x.getKey();
      List<String> symbols = new ArrayList<>(prevSymbols);
      symbols.addAll(x.getValue());
      yearToSymbols.put(year, symbols);
      prevSymbols = symbols;
      System.out.printf("%d: %2d %2d  ", year, x.getValue().size(), symbols.size());
      System.out.println(String.join(" ", x.getValue()));
    }
    return yearToSymbols;
  }

  /** Ensure that all sequences in the universe have the same start/end times. */
  private static void prepareUniverse()
  {
    System.out.printf("Universe: %d  [%s]\n", universe.length, String.join(" ", universe));

    // Find first year that has data for all symbols.
    Set<String> universeSet = new HashSet<>();
    int lastStartYear = -1;
    for (String symbol : universe) {
      universeSet.add(symbol);
      int index = symbol2index.getOrDefault(symbol, -1);
      if (index < 0) {
        throw new IllegalArgumentException(String.format("Missing symbol: %s\n", symbol));
      }
      int year = getStartYear(returnSeqs[index]);
      if (year > lastStartYear) lastStartYear = year;
    }
    System.out.printf("Start year: %d\n", lastStartYear);

    // Clear all sequences not in universe.
    int nAssetsLeft = 0;
    for (int i = 0; i < returnSeqs.length; ++i) {
      if (!universeSet.contains(returnSeqs[i].getName())) {
        returnSeqs[i] = null;
        symbols[i] = descriptions[i] = null;
      } else {
        ++nAssetsLeft;
      }
    }
    assert nAssetsLeft == universe.length;

    // Rebuild arrays with only the remaining assets.
    // if (universe.length < symbols.length) {
    // descriptions = condense(descriptions, universe.length);
    // symbols = condense(symbols, universe.length);
    // returnSeqs = condense(returnSeqs, universe.length);
    // }

    // Adjust all sequence to have the same start year (i.e. truncate older sequences).
    long startTime = TimeLib.toMs(lastStartYear, Month.DECEMBER, 31);
    for (String symbol : universe) {
      int index = symbol2index.get(symbol);
      Sequence seq = returnSeqs[index];
      assert seq.getStartMS() <= startTime;
      if (seq.getStartMS() < startTime) {
        seq = seq.subseq(startTime, TimeLib.TIME_END);
        returnSeqs[index] = seq;
      }
    }

    // Verify that all sequences match.
    for (int i = 1; i < universe.length; ++i) {
      int a = symbol2index.get(universe[i - 1]);
      int b = symbol2index.get(universe[i]);
      assert returnSeqs[a].matches(returnSeqs[b]);
    }

    // Create baseline portfolios (defenders).
    Map<String, String> portfolioDefs = new LinkedHashMap<>();
    portfolioDefs.put("Bonds", "[VBMFX:100]");
    portfolioDefs.put("S&P 500", "[VFINX:100]");
    // portfolioDefs.put("90/10", "[VTSMX:90,VBMFX:10]");
    portfolioDefs.put("60/40", "[VTSMX:60,VBMFX:40]");
    // portfolioDefs.put("70/30", "[VTSMX:70,VBMFX:30]");
    portfolioDefs.put("Core Three", "[VTSMX:40,VGTSX:30,VBMFX:30]");
    portfolioDefs.put("Core Four", "[VTSMX:50,VGTSX:20,VBMFX:20,VGSIX:10]");

    for (Map.Entry<String, String> entry : portfolioDefs.entrySet()) {
      DiscreteDistribution allocation = DiscreteDistribution.fromStringWithNames(entry.getValue());
      assert allocation.isNormalized();
      FeatureVec stats = getStats(allocation, true);
      Portfolio portfolio = new Portfolio(entry.getKey(), allocation, stats);
      defenders.add(portfolio);
    }

  }

  /** @return year of first data point for the given sequence. */
  private static int getStartYear(Sequence seq)
  {
    return TimeLib.ms2date(seq.getStartMS()).getYear();
  }

  /** @return list of portfolios formed by blending two existing porfolios (50/50 average) . */
  private static List<Portfolio> blendPortfolios(List<Portfolio> portfolios) throws IOException
  {
    List<Portfolio> blends = new ArrayList<>(portfolios);
    for (int i = 0; i < portfolios.size(); ++i) {
      Portfolio p1 = portfolios.get(i);
      for (int j = i + 1; j < portfolios.size(); ++j) {
        Portfolio p2 = portfolios.get(j);
        DiscreteDistribution allocation = p1.allocation.blend(p2.allocation);
        assert allocation.isNormalized();
        FeatureVec stats = getStats(allocation);
        blends.add(new Portfolio(allocation, stats));
      }
    }
    System.out.printf("Blends (all): %d\n", blends.size());

    blends = Portfolio.removeNonDominators(blends, domDir, domThresholds, domStrictness);
    blends = Portfolio.removeDuplicates(blends);
    return blends;
  }

  private static List<Portfolio> filterPortfolios(List<Portfolio> portfolios) throws IOException
  {
    // TODO explore best thresholds
    double minCAGR = 0; // 12.6;
    double minWorst = 0; // 6.0;
    double minMedian = 0; // 13.0;
    double minTenth = 0; // 7.7;

    // { "Worst Period", "10th Percentile", "Median", "CAGR", "Std Dev", "Max Drawdown" };
    int iCAGR = stat2index.get("CAGR");
    portfolios = portfolios.stream().filter(x -> x.stats.get(iCAGR) >= minCAGR).collect(Collectors.toList());
    System.out.printf("CAGR > %.1f: %d\n", minCAGR, portfolios.size());

    int iWorst = stat2index.get("Worst Period");
    portfolios = portfolios.stream().filter(x -> x.stats.get(iWorst) >= minWorst).collect(Collectors.toList());
    System.out.printf("Worst > %.1f: %d\n", minWorst, portfolios.size());

    int iMedian = stat2index.get("Median");
    portfolios = portfolios.stream().filter(x -> x.stats.get(iMedian) >= minMedian).collect(Collectors.toList());
    System.out.printf("Median > %.1f: %d\n", minMedian, portfolios.size());

    int iTenth = stat2index.get("10th Percentile");
    portfolios = portfolios.stream().filter(x -> x.stats.get(iTenth) >= minTenth).collect(Collectors.toList());
    System.out.printf("10th Percentile > %.1f: %d\n", minTenth, portfolios.size());

    // Calculate rolling returns for each portfolio.
    int years = 5; // TODO 5, 10, or 20? make a parameter.
    String name = String.format("forward-returns-%d", years);
    for (Portfolio portfolio : defenders) {
      FeatureVec stats = portfolio.getStats();
      Sequence annual = (Sequence) stats.getMeta("annualReturns");
      annual.setName(portfolio.name);
      stats.setMeta(name, getForwardReturns(annual, years));
    }

    // Calculate rolling returns for each portfolio.
    for (Portfolio portfolio : portfolios) {
      FeatureVec stats = getStats(portfolio.allocation, true);
      portfolio.stats = stats;
      Sequence annual = (Sequence) stats.getMeta("annualReturns");
      annual.setName(portfolio.name);
      stats.setMeta(name, getForwardReturns(annual, years));

      WinStats winStats = new WinStats();
      for (Portfolio defender : defenders) {
        winStats.add(calcWinStats(portfolio, defender, name, 0.3));
      }
      stats.setMeta("WinStats", winStats);
      double score = winStats.nWin + 0.1 * winStats.nTie - 1.5 * winStats.nLose;
      stats.setMeta("WinScore", score);
    }

    // Sort portfolios and save description and metrics to a text file.
    Collections.sort(portfolios);
    Portfolio.savePortfolios(getFinalFile(), portfolios);

    Collections.sort(portfolios, new Comparator<Portfolio>()
    {
      @Override
      public int compare(Portfolio a, Portfolio b)
      {
        Double x = (Double) a.stats.getMeta("WinScore");
        Double y = (Double) b.stats.getMeta("WinScore");
        return x.compareTo(y);
      }
    });

    for (Portfolio portfolio : portfolios) {
      WinStats winStats = (WinStats) portfolio.stats.getMeta("WinStats");
      double score = (Double) portfolio.stats.getMeta("WinScore");
      System.out.printf("%6.2f  %20s  %s\n", score, winStats, portfolio);
    }

    return portfolios;
  }

  public static void main(String[] args) throws IOException
  {
    loadSimbaData(new File(DataIO.getFinancePath(), "simba-2018b.csv"));
    int nMonths = (int) Math.round(TimeLib.monthsBetween(returnSeqs[0].getStartMS(), returnSeqs[0].getEndMS()));
    System.out.printf("Assets: %d  [%s] -> [%s]  (%d months)\n", returnSeqs.length,
        TimeLib.formatDate(returnSeqs[0].getStartMS()), TimeLib.formatDate(returnSeqs[0].getEndMS()), nMonths);

    if (bAdjustForInflation) {
      Map<Integer, Double> inflation = buildInflationMap();
      adjustForInflation(inflation);
    }

    if (bPrintStartYearInfo) {
      buildYearMap();

      for (int i = 0; i < returnSeqs.length; ++i) {
        Sequence seq = returnSeqs[i];
        System.out.printf("%02d| %5s [%s] -> [%s]\n", i, seq.getName(), TimeLib.formatDate(seq.getStartMS()),
            TimeLib.formatDate(seq.getEndMS()));
      }
    }

    prepareUniverse();

    if (mode == Mode.Generate) {
      List<Portfolio> portfolios = generate();
      genCharts(portfolios);
    } else if (mode == Mode.Filter) {
      List<Portfolio> portfolios = Portfolio.loadPortfolios(getDominatingFile());
      System.out.printf("Portfolios: %d\n", portfolios.size());
      portfolios = filterPortfolios(portfolios);
      System.out.printf("Filtered: %d\n", portfolios.size());
      genCharts(portfolios);
    } else if (mode == Mode.Evaluate) {
      String[] challengerDefs = new String[] { // list of string definitions for interesting allocations
          "[VMVIX:25,VISVX:15,VHDYX:20,VMGIX:5,VEIEX:10,VFINX:5,VCIT:10,VIGRX:10]",

          "[VMVIX:30,VISVX:5,VGSIX:10,VHDYX:25,VCIT:10,VIGRX:10,VEIEX:10]",
          "[VTSMX:10,VMVIX:15,VISVX:15,VHDYX:15,VIGRX:15,VBMFX:10,VGSIX:10,VEIEX:10]",
          // "[VMVIX:50,VHDYX:20,VCIT:5,VIGRX:10,VBMFX:5,VEIEX:10]",
          "[VMVIX:20,VISVX:5,VGSIX:20,VHDYX:20,VCIT:10,VMGIX:5,VIGRX:10,VEIEX:10]",
          "[VMVIX:30,VISVX:25,VHDYX:30,VMGIX:5,VEIEX:5,VFINX:5]",
          "[VMVIX:30,VISVX:20,VHDYX:15,VIGRX:10,VBMFX:15,VEIEX:5,VFINX:5]",
          // "[VTSMX:10,VHDYX:10,VMVIX:20,VISVX:20,VGSIX:10,VBMFX:10,VGTSX:10,VEIEX:10]",
          // "[VTSMX:10,VGTSX:5,VIGRX:5,VMVIX:25,VISVX:10,VGSIX:10,VHDYX:15,VBMFX:10,VEIEX:10]",
      };
      List<Portfolio> challengers = new ArrayList<>();
      for (String def : challengerDefs) {
        DiscreteDistribution allocation = DiscreteDistribution.fromStringWithNames(def);
        assert allocation.isNormalized();
        FeatureVec stats = getStats(allocation, true);
        Portfolio challenger = new Portfolio(allocation.toStringWithNames(0), allocation, stats);
        challengers.add(challenger);
      }

      defenders.stream().forEach(System.out::println);
      challengers.stream().forEach(System.out::println);
      genComparisonCharts(defenders, challengers);
    }
  }
}
