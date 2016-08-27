package org.minnen.retiretool.simba;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.stats.ReturnStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

public class SimbaPortfolios
{
  public static final Map<String, Integer> name2index = new HashMap<>();
  public static String[]                   names;
  public static String[]                   descriptions;
  public static Sequence[]                 returnData;

  public static void loadSimbaData(File file) throws IOException
  {
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read Simba CSV file (%s)", file.getPath()));
    }
    System.out.printf("Loading Simba CSV data file: [%s]\n", file.getPath());
    BufferedReader in = new BufferedReader(new FileReader(file));

    String line;
    int nLines = 0;
    while ((line = in.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty() || line.startsWith("\"") || line.toLowerCase().startsWith("date")) {
        continue;
      }
      String[] toks = line.trim().split("[,]+");
      ++nLines;
      // System.out.printf("%d: %d |%s\n", nLines, toks.length, line);
      if (nLines == 1) {
        descriptions = new String[toks.length - 1];
        System.arraycopy(toks, 1, descriptions, 0, descriptions.length);
      } else if (nLines == 2) {
        assert toks.length == descriptions.length + 1;
        names = new String[toks.length - 1];
        System.arraycopy(toks, 1, names, 0, descriptions.length);
        returnData = new Sequence[names.length];
        for (int i = 0; i < names.length; ++i) {
          returnData[i] = new Sequence(names[i]);
        }
      } else {
        assert toks.length == descriptions.length + 1;
        try {
          int year = Integer.parseInt(toks[0]);
          for (int i = 1; i < toks.length; ++i) {
            double r = Double.parseDouble(toks[i]);
            double m = FinLib.ret2mul(r);
            returnData[i - 1].addData(m, TimeLib.toMs(year, Month.DECEMBER, 31));
          }
        } catch (NumberFormatException e) {
          System.err.printf("Error parsing CSV data: [%s]\n", line);
          break;
        }
      }
    }
    in.close();
  }

  public static void keepAssets(int nKeep)
  {
    if (nKeep >= names.length) return;
    String[] a = new String[nKeep];
    String[] b = new String[nKeep];
    Sequence[] c = new Sequence[nKeep];
    System.arraycopy(names, 0, a, 0, nKeep);
    System.arraycopy(descriptions, 0, b, 0, nKeep);
    System.arraycopy(returnData, 0, c, 0, nKeep);
    names = a;
    descriptions = b;
    returnData = c;
  }

  public static double returnForYear(int iYear, DiscreteDistribution allocation)
  {
    double r = 0.0;
    for (int i = 0; i < allocation.size(); ++i) {
      double w = allocation.weights[i];
      if (w <= 0.0) continue;
      Sequence assetReturns = returnData[name2index.get(allocation.names[i])];
      r += w * assetReturns.get(iYear, 0);
    }
    return r;
  }

  public static double[] runPortfolio(DiscreteDistribution targetAllocation)
  {
    int nYears = returnData[0].size();
    double[] returns = new double[nYears];
    for (int iYear = 0; iYear < nYears; ++iYear) {
      double r = returnForYear(iYear, targetAllocation);
      returns[iYear] = FinLib.mul2ret(r);
    }
    return returns;
  }

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
    assert weights.length == names.length;
    DiscreteDistribution dist = new DiscreteDistribution(names);
    for (int i = 0; i < weights.length; ++i) {
      dist.weights[i] = weights[i] / 100.0;
    }
    return dist;
  }

  public static void scanDistributions(int minAssets, int maxAssets, int minWeight, int maxWeight, int weightStep,
      List<DiscreteDistribution> portfolios)
  {
    int[] weights = new int[returnData.length];
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
        if (portfolios.size() % 100 == 0) {
          System.out.printf("%d\n", portfolios.size());
        }
        // System.out.printf("%02d: %s\n", portfolios.size(), dist.toString("%3.0f"));
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

  public static void scanDistributionsEW(int minAssets, int maxAssets, List<DiscreteDistribution> portfolios)
  {
    int[] weights = new int[returnData.length];
    scanDistributions(weights, 0, 0, minAssets, maxAssets, portfolios);
  }

  public static void scanDistributions(int[] weights, int index, int nAssetsSoFar, int minAssets, int maxAssets,
      List<DiscreteDistribution> portfolios)
  {
    if (index >= weights.length) {
      if (nAssetsSoFar >= minAssets && nAssetsSoFar <= maxAssets) {
        DiscreteDistribution dist = buildDistribution(weights);
        dist.normalize();
        portfolios.add(dist);
        if (portfolios.size() % 1000 == 0) {
          System.out.printf("%d\n", portfolios.size());
        }
        // System.out.printf("%02d: %s\n", portfolios.size(), dist.toString("%3.0f"));
      }
      return;
    }
    if (nAssetsSoFar > maxAssets) return;

    // Include asset[index].
    weights[index] = 1;
    scanDistributions(weights, index + 1, nAssetsSoFar + 1, minAssets, maxAssets, portfolios);

    // Skip asset[index].
    weights[index] = 0;
    scanDistributions(weights, index + 1, nAssetsSoFar, minAssets, maxAssets, portfolios);
  }

  public static void main(String[] args) throws IOException
  {
    File outputDir = new File("g:/web");
    File dataDir = new File("g:/research/finance/");
    assert dataDir.isDirectory();

    loadSimbaData(new File(dataDir, "simba-1972.csv"));
    keepAssets(10); // TODO for debugging
    System.out.printf("Assets: %d\n", returnData.length);

    names = new String[returnData.length];
    descriptions = new String[returnData.length];
    for (int i = 0; i < returnData.length; ++i) {
      String name = returnData[i].getName();
      name2index.put(name, i);
      names[i] = name;
      System.out.printf("%d: %s\n", i + 1, name);
    }

    List<DiscreteDistribution> portfolios = new ArrayList<>();
    // scanDistributions(1, 5, 20, 50, 20, portfolios);
    scanDistributionsEW(1, 5, portfolios);
    System.out.printf("%d\n", portfolios.size());
    //
    // double[] returns = runPortfolio(dist);
    // // Sequence cumulativeReturns = FinLib.cumulativeFromReturns(returns);
    // ReturnStats rstats = ReturnStats.calc("Test", returns);
    //
    // System.out.printf("%s: %s\n", dist, rstats);
  }
}
