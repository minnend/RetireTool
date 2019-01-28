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
import java.util.HashMap;
import java.util.HashSet;
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

/**
 * Explore passive asset allocations. The "simba data" gives annual returns across a wide range of asset classes. This
 * data can be used to simulate and compare different fixed portfolios.
 *
 * Inspiration: https://portfoliocharts.com/2016/03/07/the-ultimate-portfolio-guide-for-all-types-of-investors/ Simba's
 * data (thread): https://www.bogleheads.org/forum/viewtopic.php?t=2520
 */
public class SimbaPortfolios
{
  public static final boolean              bLoadPortfolios     = true;

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

  // TODO per-symbol minimum
  // TODO better labels on charts: inflation? start year?

  static {
    symbol2index = new HashMap<>();

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
    symbols = new String[] { "VGSIX", "VBMFX", "VCIT" };
    for (String symbol : symbols) {
      symbolCap.put(symbol, 20); // no more than 20% for each symbol
    }

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
              double m = FinLib.ret2mul(r);
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

  private static double returnForYear(int iYear, DiscreteDistribution allocation)
  {
    double r = 0.0;
    for (int i = 0; i < allocation.size(); ++i) {
      double w = allocation.weights[i];
      if (w < 1e-9) continue;
      Sequence assetReturns = returnSeqs[symbol2index.get(allocation.names[i])];
      r += w * assetReturns.get(iYear, 0);
    }
    return r;
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

  /** @return sequence of returns (as multiplier) per year for the given portfolio. */
  private static Sequence runPortfolio(DiscreteDistribution targetAllocation)
  {
    int nYears = returnSeqs[getFirstReturnIndex()].size();
    Sequence returns = new Sequence(targetAllocation.toStringWithNames(nSigDig));
    returns.setMeta("portfolio", targetAllocation);
    for (int iYear = 0; iYear < nYears; ++iYear) {
      double r = returnForYear(iYear, targetAllocation);
      returns.addData(FinLib.mul2ret(r), index2ms(iYear));
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
    String name = allocation.toStringWithNames(nSigDig);
    Sequence returnSeq = runPortfolio(allocation);
    Sequence cumulativeReturns = FinLib.cumulativeFromReturns(returnSeq, principle, contribution);
    Sequence periodReturns = calcPeriodReturns(cumulativeReturns, nPeriodYears);
    ReturnStats rstatsPeriod = ReturnStats.calc(name, periodReturns.extractDim(0));
    CumulativeStats cstats = CumulativeStats.calc(cumulativeReturns, false);
    FeatureVec v = new FeatureVec(name, statNames.length);
    v.set(stat2index.get("Worst Period"), rstatsPeriod.min);
    v.set(stat2index.get("10th Percentile"), rstatsPeriod.percentile10);
    v.set(stat2index.get("Median"), rstatsPeriod.median);
    v.set(stat2index.get("CAGR"), cstats.cagr);
    v.set(stat2index.get("Std Dev"), rstatsPeriod.sdev);
    v.set(stat2index.get("Max Drawdown"), -cstats.drawdown);
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
    final int[][] goodCharts = new int[][] { { 0, 3 }, { 1, 3 }, { 2, 3 }, { 5, 2 }, { 5, 3 }, { 0, 2 } };
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

  // private static String[] condense(String[] a, int nGood)
  // {
  // String[] b = new String[nGood];
  // int j = 0;
  // for (int i = 0; i < a.length; ++i) {
  // if (a[i] != null) b[j++] = a[i];
  // }
  // assert j == nGood;
  // return b;
  // }
  //
  // private static Sequence[] condense(Sequence[] a, int nGood)
  // {
  // Sequence[] b = new Sequence[nGood];
  // int j = 0;
  // for (int i = 0; i < a.length; ++i) {
  // if (a[i] != null) b[j++] = a[i];
  // }
  // assert j == nGood;
  // return b;
  // }

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
  }

  /** @return year of first data point for the given sequence. */
  private static int getStartYear(Sequence seq)
  {
    return TimeLib.ms2date(seq.getStartMS()).getYear();
  }

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
    return blends;
  }

  private static List<Portfolio> filterPortfolios(List<Portfolio> portfolios) throws IOException
  {
    double minCAGR = 12.7;
    double minWorst = 4.9;
    double minMedian = 13.4;
    double minTenth = 6.8;

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

    // Sort portfolios and save description and metrics to a text file.
    Collections.sort(portfolios);
    Portfolio.savePortfolios(getFinalFile(), portfolios);

    return portfolios;
  }

  public static void main(String[] args) throws IOException
  {
    loadSimbaData(new File(DataIO.getFinancePath(), "simba-2018.csv"));
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

    List<Portfolio> portfolios;
    if (bLoadPortfolios) {
      portfolios = Portfolio.loadPortfolios(getDominatingFile());
      System.out.printf("Portfolios: %d\n", portfolios.size());
      portfolios = filterPortfolios(portfolios);
      System.out.printf("Filtered: %d\n", portfolios.size());
    } else {
      portfolios = generate();
    }

    genCharts(portfolios);
  }
}
