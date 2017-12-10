package org.minnen.retiretool.vanguard;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.stats.DurationalStats;
import org.minnen.retiretool.util.TimeLib;

public class SummaryTools
{
  public static String[]  fundSymbols;

  public static final int CAGR         = 0;
  public static final int DRAWDOWN     = 1;
  public static final int WORST        = 2;
  public static final int PERCENTILE10 = 3;
  public static final int MEDIAN       = 4;

  public static boolean isValid(int[] weights, int minAssets, int maxAssets, int minWeight, int maxWeight)
  {
    int totalWeight = 0;
    int n = 0;
    for (int i = 0; i < weights.length; ++i) {
      assert weights[i] >= 0;
      if (weights[i] > 0) {
        ++n;
        totalWeight += weights[i];
        if (weights[i] < minWeight || weights[i] > maxWeight) return false;
      }
    }
    if (totalWeight != 100) return false;
    if (n < minAssets || n > maxAssets) return false;
    return true;
  }

  public static DiscreteDistribution buildDistribution(int[] weights)
  {
    assert weights.length == fundSymbols.length;
    DiscreteDistribution dist = new DiscreteDistribution(fundSymbols);
    for (int i = 0; i < weights.length; ++i) {
      dist.weights[i] = weights[i] / 100.0;
    }
    return dist;
  }

  public static void scanDistributions(int minAssets, int maxAssets, int minWeight, int maxWeight, int weightStep,
      List<DiscreteDistribution> portfolios)
  {
    int[] weights = new int[fundSymbols.length];
    scanDistributions(weights, 0, 0, 100, minAssets, maxAssets, minWeight, maxWeight, weightStep, portfolios);
  }

  public static void scanDistributions(int[] weights, int index, int nAssetsSoFar, int weightLeft, int minAssets,
      int maxAssets, int minWeight, int maxWeight, int weightStep, List<DiscreteDistribution> portfolios)
  {
    assert weightLeft >= 0;
    if (weightLeft == 0) {
      if (nAssetsSoFar >= minAssets && nAssetsSoFar <= maxAssets) {
        assert isValid(weights, minAssets, maxAssets, minWeight, maxWeight);
        DiscreteDistribution dist = buildDistribution(weights);
        portfolios.add(dist);
        if (portfolios.size() % 200000 == 0) {
          System.out.printf("%d\n", portfolios.size());
        }
      }
      return;
    }
    if (index >= weights.length) return;
    if (weightLeft < minWeight) return;
    if (nAssetsSoFar >= maxAssets) return;

    // Try assigning all valid weights to asset[index].
    for (int w = Math.min(maxWeight, weightLeft); w >= minWeight; w -= weightStep) {
      weights[index] = w;
      scanDistributions(weights, index + 1, nAssetsSoFar + 1, weightLeft - w, minAssets, maxAssets, minWeight,
          maxWeight, weightStep, portfolios);
    }

    // Skip asset[index].
    weights[index] = 0;
    scanDistributions(weights, index + 1, nAssetsSoFar, weightLeft, minAssets, maxAssets, minWeight, maxWeight,
        weightStep, portfolios);
  }

  public static List<FeatureVec> savePortfolioStats(PortfolioRunner runner, File file) throws IOException
  {
    List<DiscreteDistribution> portfolios = new ArrayList<>();
    long a = TimeLib.getTime();
    // TODO create config class for scanning.
    // scanDistributions(1, 10, 10, 100, 10, portfolios);
    // scanDistributions(1, 8, 10, 40, 10, portfolios);
    scanDistributions(4, 6, 5, 30, 5, portfolios);
    // scanDistributions(3, 3, 20, 40, 10, portfolios);
    long b = TimeLib.getTime();
    System.out.printf("Portofolios: %d  (%s)\n", portfolios.size(), TimeLib.formatDuration(b - a));
    List<FeatureVec> stats = new ArrayList<>();
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      a = TimeLib.getTime();
      for (DiscreteDistribution portfolio : portfolios) {
        FeatureVec v = runner.run(portfolio);
        stats.add(v);
        // System.out.printf("%s = %s\n", v.getName(), v);
        writer.write(String.format("%-80s %s\n", v.getName(), v));
        // writer.flush();

        b = TimeLib.getTime();
        if (stats.size() % 10000 == 0) {
          double persec = stats.size() * 1000.0 / (b - a);
          System.out.printf("[%s] %.2f%% @ %.1f/s => %s left\n", TimeLib.formatTime(TimeLib.getTime()),
              100.0 * stats.size() / portfolios.size(), persec,
              TimeLib.formatDuration((long) (1000.0 * (portfolios.size() - stats.size()) / persec), 2));
        }

      }
      System.out.printf("Ran & Saved Portfolios (%s)\n", TimeLib.formatDuration(b - a));
      return stats;
    }
  }

  public static List<FeatureVec> loadPortfolioStats(File file, boolean bInvertDrawdown) throws IOException
  {
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read portfolio file (%s)", file.getPath()));
    }
    System.out.printf("Loading portfolio data file: [%s]\n", file.getPath());
    List<FeatureVec> portfolioStats = new ArrayList<>();
    BufferedReader in = new BufferedReader(new FileReader(file));
    Pattern pattern = Pattern.compile("(\\[.+\\])\\s*\\[(.+)\\]");
    String line;
    while ((line = in.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty() || line.startsWith("\"")) continue;

      Matcher m = pattern.matcher(line);
      if (!m.find()) continue;

      String name = m.group(1);
      String[] toks = m.group(2).split(",");
      double[] stats = new double[toks.length];
      for (int i = 0; i < stats.length; ++i) {
        stats[i] = Double.parseDouble(toks[i].trim());
      }
      if (bInvertDrawdown) {
        stats[DRAWDOWN] = -stats[DRAWDOWN];
      }
      portfolioStats.add(new FeatureVec(name, stats.length, stats));
    }
    in.close();
    return portfolioStats;
  }

  public static List<DiscreteDistribution> loadPortfolios(File file) throws IOException
  {
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read portfolio file (%s)", file.getPath()));
    }
    System.out.printf("Loading portfolio data file: [%s]\n", file.getPath());
    List<DiscreteDistribution> portfolios = new ArrayList<>();
    BufferedReader in = new BufferedReader(new FileReader(file));
    String line;
    while ((line = in.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty() || line.startsWith("#")) continue;
      portfolios.add(DiscreteDistribution.fromStringWithNames(line));
    }
    in.close();
    return portfolios;
  }

  /** @return true if x dominates y */
  public static boolean dominates(FeatureVec x, FeatureVec y)
  {
    int n = x.getNumDims();
    assert n == y.getNumDims();
    for (int i = 0; i < n; ++i) {
      if (x.get(i) <= y.get(i)) return false;
    }
    return true;
  }

  /** Removes portfolios that are "dominated" by another. */
  public static void prunePortfolios(List<FeatureVec> portfolioStats)
  {
    // Filter results.
    int n = portfolioStats.size();
    for (int i = 0; i < n; ++i) {
      FeatureVec v1 = portfolioStats.get(i);
      if (v1 == null) continue;
      for (int j = i + 1; j < n; ++j) {
        FeatureVec v2 = portfolioStats.get(j);
        if (v2 == null) continue;

        if (SummaryTools.dominates(v1, v2)) {
          portfolioStats.set(j, null);
          continue;
        }

        if (SummaryTools.dominates(v2, v1)) {
          portfolioStats.set(i, null);
          break;
        }
      }
    }

    // Remove all null entries.
    removeNulls(portfolioStats);
  }

  public static List<? extends Object> removeNulls(List<? extends Object> list)
  {
    list.removeIf(new Predicate<Object>()
    {
      @Override
      public boolean test(Object v)
      {
        return v == null;
      }
    });
    return list;
  }

  public static FeatureVec calcStats(Sequence cumulativeReturnsMonthly, int durStatsMonths)
  {
    CumulativeStats cstats = CumulativeStats.calc(cumulativeReturnsMonthly);
    DurationalStats dstats = DurationalStats.calc(cumulativeReturnsMonthly, durStatsMonths);
    return new FeatureVec(cumulativeReturnsMonthly.getName(), 5, cstats.cagr, -cstats.drawdown, dstats.min,
        dstats.percentile10, dstats.median);
  }
}
