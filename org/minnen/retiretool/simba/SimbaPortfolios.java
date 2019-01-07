package org.minnen.retiretool.simba;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
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
 * Inspiration: https://portfoliocharts.com/2016/03/07/the-ultimate-portfolio-guide-for-all-types-of-investors/
 */
public class SimbaPortfolios
{
  public static final int                  nLongYears          = 10;
  public static final boolean              bAdjustForInflation = false;

  public static final boolean              bPrintStartYearInfo = false;
  public static final int                  nSigDig             = 0;
  public static final Map<String, Integer> stat2index          = new HashMap<>();
  public static final String[]             stats;

  public static final Map<String, Integer> symbol2index        = new HashMap<>();
  public static String[]                   symbols;                              // VTSMX, VIVAX, etc.
  public static String[]                   descriptions;                         // TSM, LCV, etc.
  public static Sequence[]                 returnSeqs;                           // one per symbol

  public static final String[]             universe;                             // list of symbols to explore
  public static final Map<String, Integer> symbolCap;                            // max percentage for each symbol
                                                                                 // (optional)

  static {
    // Build reverse map from statistic name to index.
    stats = new String[] { "Worst Period", "10th Percentile", "25th Percentile", "Median", "CAGR", "Std Dev",
        "Worst Year", "Max Drawdown" };
    for (int i = 0; i < stats.length; ++i) {
      stat2index.put(stats[i], i);
    }

    // universe = new String[] { "VBMFX", "VFINX", "VTSMX", "VUSXX", "NAESX", "FSAGX", "GSG", "VGSIX", "VGTSX" };

    universe = new String[] { //
        "VBMFX", "VUSXX", // total bond, treasuries
        "FSAGX", "GSG", // precious metals, commodities
        "VGSIX", // REITs
        "VGTSX", "EFV", // international - total, value
        "VIVAX", "VMVIX", "VISVX", // large, mid, small - value
        "VIGRX", "VMGIX", "VISGX", // large, mid, small - growth
    };

    symbolCap = new HashMap<>();
    symbolCap.put("FSAGX", 10); // additional allocation limit for materials and commodities
    symbolCap.put("GSG", 10);
  }

  /** @return boolean array matching `symbols` where true elements should be avoided (never used in a portfolio). */
  private static boolean[] buildAvoidMask(String... avoidNames)
  {
    boolean[] avoid = new boolean[symbols.length];
    for (String name : avoidNames) {
      avoid[symbol2index.get(name)] = true;
    }
    return avoid;
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

  private static List<DiscreteDistribution> loadPortfolios(File file) throws IOException
  {
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read portfolio file (%s)", file.getPath()));
    }
    System.out.printf("Loading portfolio data file: [%s]\n", file.getPath());
    List<DiscreteDistribution> portfolios = new ArrayList<>();
    Set<String> portfolioNames = new HashSet<>();
    try (BufferedReader in = new BufferedReader(new FileReader(file))) {

      Pattern pattern = Pattern.compile("\\[(.*?)\\]");

      int[] weights = new int[returnSeqs.length];
      String line;
      while ((line = in.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("\"") || line.toLowerCase().startsWith("date")) {
          continue;
        }

        Matcher m = pattern.matcher(line);
        if (!m.find()) continue;

        Arrays.fill(weights, 0);
        String[] assets = m.group(1).split(",");
        for (String s : assets) {
          String[] toks = s.split(":");
          String name = toks[0];
          int weight = Integer.parseInt(toks[1]);
          weights[symbol2index.get(name)] = weight;
        }
        DiscreteDistribution dist = buildDistribution(weights);
        dist.normalize();

        String name = dist.toStringWithNames(0);
        if (portfolioNames.contains(name)) continue;
        portfolioNames.add(name);
        portfolios.add(dist);
      }
    }
    return portfolios;
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
    for (int i = 0; i < returnSeqs.length; ++i) {
      if (returnSeqs[i] != null) return i;
    }
    return -1;
  }

  private static Sequence runPortfolio(DiscreteDistribution targetAllocation)
  {
    int nYears = returnSeqs[getFirstReturnIndex()].size();
    Sequence returns = new Sequence(targetAllocation.toStringWithNames(nSigDig));
    returns.setMeta("portfolio", targetAllocation);
    // TODO allow minimum year to avoid gold weirdness in early 70s.
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

  private static DiscreteDistribution buildDistribution(int[] weights)
  {
    assert weights.length == symbols.length;
    DiscreteDistribution dist = new DiscreteDistribution(symbols);
    for (int i = 0; i < weights.length; ++i) {
      dist.weights[i] = weights[i] / 100.0;
    }
    return dist;
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
    assert weightLeft >= 0;
    if (weightLeft == 0) { // reached the end of this path but result may not be valid
      assert nAssetsSoFar <= maxAssets;
      if (nAssetsSoFar < minAssets) return;

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

  public static void scanDistributionsEW(int minAssets, int maxAssets, boolean[] avoid,
      List<DiscreteDistribution> portfolios)
  {
    int[] weights = new int[returnSeqs.length];
    scanDistributionsEW(weights, 0, 0, minAssets, maxAssets, avoid, portfolios);
  }

  public static void scanDistributionsEW(int[] weights, int index, int nAssetsSoFar, int minAssets, int maxAssets,
      boolean[] avoid, List<DiscreteDistribution> portfolios)
  {
    if (index >= weights.length) {
      if (nAssetsSoFar >= minAssets && nAssetsSoFar <= maxAssets) {
        DiscreteDistribution dist = buildDistribution(weights);
        dist.normalize();
        portfolios.add(dist);
        if (portfolios.size() % 100000 == 0) {
          System.out.printf("%d\n", portfolios.size());
        }
        // System.out.printf("%02d: %s\n", portfolios.size(), dist.toString("%3.0f"));
      }
      return;
    }
    if (nAssetsSoFar > maxAssets) return;

    if (avoid == null || !avoid[index]) {
      // Include asset[index].
      weights[index] = 1;
      scanDistributionsEW(weights, index + 1, nAssetsSoFar + 1, minAssets, maxAssets, avoid, portfolios);
    }

    // Skip asset[index].
    weights[index] = 0;
    scanDistributionsEW(weights, index + 1, nAssetsSoFar, minAssets, maxAssets, avoid, portfolios);
  }

  /** @return true if x dominates y */
  public static boolean dominates(FeatureVec x, FeatureVec y, double[] domdir, double[] threshold)
  {
    int nLoss = 0;
    int nTiePlus = 0;
    int nTieMinus = 0;
    int nWin = 0;
    for (int i = 0; i < domdir.length; ++i) {
      if (Math.abs(domdir[i]) < 0.1) continue; // skip this feature
      double v = x.get(i) - y.get(i);
      if (domdir[i] > 0) {
        if (v < -threshold[i]) ++nLoss;
        else if (v < threshold[i]) {
          if (v >= 0) ++nTiePlus;
          else++nTieMinus;
        } else++nWin;
      } else {
        if (v > threshold[i]) ++nLoss;
        else if (v > -threshold[i]) {
          if (v <= 0) ++nTiePlus;
          else++nTieMinus;
        } else++nWin;
      }
    }
    return nLoss == 0 && nWin > 0 && nTiePlus >= nTieMinus;
  }

  /** @return Sequence containing CAGR for each `nYears` period. */
  public static Sequence calcLongReturns(Sequence cumulativeReturns, int nYears)
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

  public static void findWinners(List<Sequence> durationReturns, double diffMargin)
  {
    int nPortfolios = durationReturns.size();
    int[] wins = new int[nPortfolios];
    int nYears = durationReturns.get(0).size();
    System.out.printf("Start Years: %d\n", nYears);
    for (int iYear = 0; iYear < nYears; ++iYear) {
      double[] returns = new double[nPortfolios];
      for (int i = 0; i < nPortfolios; ++i) {
        returns[i] = durationReturns.get(i).get(iYear, 0);
      }
      double rmax = returns[Library.argmax(returns)];
      for (int i = 0; i < nPortfolios; ++i) {
        if (returns[i] >= rmax - diffMargin) ++wins[i];
      }
    }

    int nWithWins = 0;
    for (int i = 0; i < nPortfolios; ++i) {
      if (wins[i] > 0) ++nWithWins;
    }
    System.out.printf("Portfolios with wins: %d\n", nWithWins);

    int[] ii = Library.sort(wins, false);
    for (int i = 0; i < wins.length && wins[i] > 0; ++i) {
      Sequence returns = durationReturns.get(ii[i]);
      DiscreteDistribution dist = (DiscreteDistribution) returns.getMeta("portfolio");
      System.out.printf("%d: %d  %s\n", i, wins[i], dist.toStringWithNames(nSigDig));
    }
  }

  public static void savePortfolios(File file, Sequence portfolioStats) throws IOException
  {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      for (FeatureVec v : portfolioStats) {
        writer.write(String.format("%-60s %s\n", v.getName(), v));
      }
    }
  }

  public static FeatureVec getStats(DiscreteDistribution dist)
  {
    String name = dist.toStringWithNames(nSigDig);
    Sequence returnSeq = runPortfolio(dist);
    Sequence cumulativeReturns = FinLib.cumulativeFromReturns(returnSeq);
    Sequence longReturns = calcLongReturns(cumulativeReturns, nLongYears);
    ReturnStats rstatsLong = ReturnStats.calc(name, longReturns.extractDim(0));
    ReturnStats rstatsAnnual = ReturnStats.calc(name, returnSeq.extractDim(0));
    CumulativeStats cstats = CumulativeStats.calc(cumulativeReturns, false);
    FeatureVec v = new FeatureVec(name, 8);
    v.set(stat2index.get("Worst Period"), rstatsLong.min);
    v.set(stat2index.get("10th Percentile"), rstatsLong.percentile10);
    v.set(stat2index.get("25th Percentile"), rstatsLong.percentile25);
    v.set(stat2index.get("Median"), rstatsLong.median);
    v.set(stat2index.get("CAGR"), cstats.cagr);
    v.set(stat2index.get("Std Dev"), rstatsLong.sdev);
    v.set(stat2index.get("Worst Year"), rstatsAnnual.min);
    v.set(stat2index.get("Max Drawdown"), -cstats.drawdown);
    return v;
  }

  public static void generate() throws IOException
  {
    List<DiscreteDistribution> portfolios = new ArrayList<>();
    boolean[] avoid = buildUniverseMask(universe);
    // TODO setup scan at top of class (create config object?)
    scanDistributions(1, 99, 10, 30, 10, avoid, portfolios);
    // scanDistributions(3, 5, 10, 40, 10, avoid, portfolios);
    // scanDistributionsEW(1, 8, avoid, portfolios);
    System.out.printf("Portfolios: %d\n", portfolios.size());

    System.out.println("Calculate Returns...");
    long start = TimeLib.getTime();
    Sequence portfolioStats = new Sequence();
    for (int i = 0; i < portfolios.size(); ++i) {
      DiscreteDistribution dist = portfolios.get(i);
      portfolioStats.addData(getStats(dist));
      if (i % 100000 == 0 && i > 0) System.out.printf("%d  (%.1f%%)\n", i, 100.0 * (i + 1) / portfolios.size());
    }
    System.out.printf("Time: %s  (%d)\n", TimeLib.formatDuration(TimeLib.getTime() - start), portfolios.size());

    // findWinners(new ArrayList<>(portfolioLongReturns), 1.0);
    // System.exit(0);

    // int[] indices = new int[] { 7, 1, 3, 0, 5, 4 };
    int[] indices = new int[] { 0, 4, 3, 7, 5, 1 };
    String sPeriodLength = String.format("%d-year Period", nLongYears);
    String[] descriptions = new String[] { "Worst " + sPeriodLength, "10th Percentile for " + sPeriodLength + "s",
        "25th Percentile for " + sPeriodLength + "s", "Median for " + sPeriodLength + "s", "Long-term CAGR",
        "Std Dev (" + sPeriodLength + "s)", "Worst Year", "Max Drawdown" };

    Sequence scatter = new Sequence();
    // System.out.println("Save...");
    // for (FeatureVec v : portfolioStats) {
    // scatter.addData(new FeatureVec(v.getName(), 3, v.get(xindex), v.get(yindex), v.get(zindex)));
    // }

    // Chart.saveScatterPlot(new File(outputDir, "simba-all.html"), descriptions[yindex] + " vs. " +
    // descriptions[xindex],
    // 1200, 600, 1, new String[] { descriptions[xindex], descriptions[yindex], descriptions[zindex] }, scatter);

    // Filter results.
    double[] domdir = new double[] { 1, 1, 1, 1, 0, -1, 1, 1 };
    double[] thresholds = new double[] { 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 2.0 };
    assert domdir.length == thresholds.length;
    assert domdir.length == descriptions.length;
    System.out.println("Filter...");
    start = TimeLib.getTime();
    int n = portfolioStats.size();
    for (int i = 0; i < n; ++i) {
      FeatureVec v1 = portfolioStats.get(i);
      if (v1 == null) continue;
      for (int j = i + 1; j < n; ++j) {
        FeatureVec v2 = portfolioStats.get(j);
        if (v2 == null) continue;

        if (dominates(v1, v2, domdir, thresholds)) {
          portfolioStats.set(j, null);
          continue;
        }

        if (dominates(v2, v1, domdir, thresholds)) {
          portfolioStats.set(i, null);
          break;
        }
      }
      // System.out.printf("%d / %d (%d)\n", i + 1, n, nDrop);
    }
    System.out.printf("Time: %s\n", TimeLib.formatDuration(TimeLib.getTime() - start));

    // Remove all null entries.
    portfolioStats.getData().removeIf(new Predicate<FeatureVec>()
    {
      @Override
      public boolean test(FeatureVec v)
      {
        return v == null;
      }
    });
    System.out.printf("Filtered: %d\n", portfolioStats.size());

    scatter = new Sequence();
    for (FeatureVec v : portfolioStats) {
      scatter.addData(v.subspace(indices));
    }

    // Chart.saveScatterPlot(new File(outputDir, "simba-filtered.html"), "CAGR vs. Standard Deviation", 1200, 600, 3,
    // new String[] { "Std Dev", "CAGR" }, scatter);
    String[] dimNames = new String[indices.length];
    for (int i = 0; i < dimNames.length; ++i) {
      dimNames[i] = descriptions[indices[i]];
    }
    ChartConfig chartConfig = new ChartConfig(new File(DataIO.outputPath, "simba-filtered.html"))
        .setType(ChartConfig.Type.Scatter).setTitle(descriptions[indices[1]] + " vs. " + descriptions[indices[0]])
        .setYAxisTitle(descriptions[indices[1]]).setXAxisTitle(descriptions[indices[0]]).setSize(1200, 900).setRadius(3)
        .setDimNames(dimNames).setData(scatter).showToolTips(true);
    Chart.saveScatterPlot(chartConfig);

    portfolioStats.getData().sort(new Comparator<FeatureVec>()
    {
      @Override
      public int compare(FeatureVec x, FeatureVec y)
      {
        double a = x.get(indices[1]);
        double b = y.get(indices[1]);
        // double sharpe1 = x.get(0) / (x.get(1) + 1e-7);
        // double sharpe2 = y.get(0) / (y.get(1) + 1e-7);
        // if (sharpe1 > sharpe2) return -1;
        // if (sharpe1 < sharpe2) return 1;
        if (a < b) return 1;
        if (a > b) return -1;
        return 0;
      }
    });
    // for (FeatureVec v : portfolioStats) {
    // double sharpe = v.get(2) / (v.get(3) + 1e-7);
    // System.out.printf("%.3f %-32s %s\n", sharpe, v, v.getName());
    // }
    savePortfolios(new File(DataIO.outputPath, "simba-filtered.txt"), portfolioStats);
  }

  private static void filterPortfolios(File file) throws IOException
  {
    List<DiscreteDistribution> portfolios = loadPortfolios(file);
    System.out.printf("Portfolios: %d\n", portfolios.size());

    Sequence portfolioStats = new Sequence();
    for (DiscreteDistribution dist : portfolios) {
      portfolioStats.addData(getStats(dist));
    }

    String[] stats = new String[] { "Max Drawdown", "10th Percentile", "25th Percentile", "Median", "Worst Period",
        "Std Dev", "CAGR", "Worst Year" };
    int[] indices = new int[stats.length];
    Map<String, Integer> name2index = new HashMap<>();
    for (int i = 0; i < stats.length; ++i) {
      indices[i] = stat2index.get(stats[i]);
      name2index.put(stats[i], i);
    }
    Map<String, String> name2desc = new HashMap<>();
    String sPeriodLength = String.format("%d-year Period", nLongYears);
    name2desc.put("Worst Period", "Worst " + sPeriodLength);
    name2desc.put("10th Percentile", "10th Percentile for " + sPeriodLength + "s");
    name2desc.put("25th Percentile", "25th Percentile for " + sPeriodLength + "s");
    name2desc.put("Median", "Median for " + sPeriodLength + "s");
    name2desc.put("CAGR", "Long-term CAGR");
    name2desc.put("Std Dev", "Std Dev (" + sPeriodLength + "s)");
    name2desc.put("Worst Year", "Worst Year");
    name2desc.put("Max Drawdown", "Max Drawdown");

    Sequence scatter = new Sequence();
    for (FeatureVec v : portfolioStats) {
      scatter.addData(v.subspace(indices));
    }

    String[] dimNames = new String[indices.length];
    for (int i = 0; i < dimNames.length; ++i) {
      dimNames[i] = name2desc.get(stats[i]);
    }

    final int[][] goodCharts = new int[][] { { 0, 1 }, { 0, 2 }, { 0, 3 }, { 0, 4 }, { 1, 3 }, { 1, 7 }, { 2, 5 },
        { 3, 4 }, { 3, 7 }, { 4, 7 } };

    for (int iChart = 0; iChart < goodCharts.length; ++iChart) {
      int i = goodCharts[iChart][0];
      int j = goodCharts[iChart][1];
      String descX = name2desc.get(stats[i]);
      String descY = name2desc.get(stats[j]);
      String filename = String.format("simba-filtered-%d-%d.html", i, j);
      ChartConfig chartConfig = new ChartConfig(new File(DataIO.outputPath, filename)).setType(ChartConfig.Type.Scatter)
          .setTitle("<b>" + descY + "</b> vs. <b>" + descX + "</b>").setYAxisTitle(descY).setXAxisTitle(descX)
          .setSize(1200, 800).setRadius(2).setDimNames(dimNames).setData(scatter).showToolTips(true).setIndexXY(i, j);
      Chart.saveScatterPlot(chartConfig);
    }

    scatter = portfolioStats.dup();
    for (int i = 0; i < scatter.length(); ++i) {
      FeatureVec v = scatter.get(i);
      if (v.get(stat2index.get("Max Drawdown")) < -50.0 || v.get(stat2index.get("Median")) < 9.0
          || v.get(stat2index.get("10th Percentile")) < 6.0 || v.get(stat2index.get("25th Percentile")) < 7.0
          || v.get(stat2index.get("Worst Period")) < 3.0) {
        scatter.set(i, null);
      }
    }
    scatter.getData().removeIf(new Predicate<FeatureVec>()
    {
      @Override
      public boolean test(FeatureVec v)
      {
        return v == null;
      }
    });
    for (int i = 0; i < scatter.size(); ++i) {
      scatter.set(i, scatter.get(i).subspace(indices));
    }
    System.out.printf("Portfolios Left: %d\n", scatter.size());

    int xIndex = name2index.get("Max Drawdown");
    int yIndex = name2index.get("Median");
    String descX = name2desc.get(stats[xIndex]);
    String descY = name2desc.get(stats[yIndex]);
    ChartConfig chartConfig = new ChartConfig(new File(DataIO.outputPath, "simba-filtered.html"))
        .setType(ChartConfig.Type.Bubble).setTitle("<b>" + descY + "</b> vs. <b>" + descX + "</b>").setYAxisTitle(descY)
        .setXAxisTitle(descX).setSize(1200, 800).setRadius(3).setBubbleSizes("7", "20").setDimNames(dimNames)
        .setData(scatter).showToolTips(true).setIndexXY(xIndex, yIndex);
    Chart.saveScatterPlot(chartConfig);
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
    for (int i = 0; i < returnSeqs.length; ++i) {
      if (!universeSet.contains(returnSeqs[i].getName())) {
        returnSeqs[i] = null;
      }
    }

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

  public static void main(String[] args) throws IOException
  {
    loadSimbaData(new File(DataIO.financePath, "simba-2018.csv"));
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

    generate();
    // filterPortfolios(new File(outputDir, "simba-filtered-all.txt"));
  }
}
