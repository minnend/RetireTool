package org.minnen.retiretool.simba;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.stats.ReturnStats;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;

public class SimbaPortfolios
{
  public static final Map<String, Integer> name2index = new HashMap<>();
  public static String[]                   names;
  public static String[]                   descriptions;
  public static Sequence[]                 returnData;
  public static double[]                   expenseRatios;
  public static Map<Integer, Double>       inflation;

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
        expenseRatios = new double[toks.length - 1]; // default to 0.0
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
          if (toks[0].equalsIgnoreCase("ER")) {
            for (int i = 1; i < toks.length; ++i) {
              expenseRatios[i - 1] = Double.parseDouble(toks[i]);
            }
          } else {
            int year = Integer.parseInt(toks[0]);
            for (int i = 1; i < toks.length; ++i) {
              double r = Double.parseDouble(toks[i]);
              double m = FinLib.ret2mul(r);
              returnData[i - 1].addData(m, TimeLib.toMs(year, Month.DECEMBER, 31));
            }
          }
        } catch (NumberFormatException e) {
          System.err.printf("Error parsing CSV data: [%s]\n", line);
          break;
        }
      }
    }
    in.close();
  }

  public static void loadInflationData(File file) throws IOException
  {
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read inflation CSV file (%s)", file.getPath()));
    }
    System.out.printf("Loading inflation CSV data file: [%s]\n", file.getPath());
    BufferedReader in = new BufferedReader(new FileReader(file));

    inflation = new HashMap<>();
    String line;
    while ((line = in.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty() || line.startsWith("\"") || line.toLowerCase().startsWith("date")) {
        continue;
      }
      String[] toks = line.trim().split("[,\\s]+");
      assert toks.length == 2;
      try {
        int year = Integer.parseInt(toks[0]);
        double cpi = Double.parseDouble(toks[1]);
        inflation.put(year, cpi);
      } catch (NumberFormatException e) {
        System.err.printf("Error parsing CSV data: [%s]\n", line);
        break;
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
    double[] d = new double[nKeep];
    System.arraycopy(names, 0, a, 0, nKeep);
    System.arraycopy(descriptions, 0, b, 0, nKeep);
    System.arraycopy(returnData, 0, c, 0, nKeep);
    System.arraycopy(expenseRatios, 0, d, 0, nKeep);
    names = a;
    descriptions = b;
    returnData = c;
    expenseRatios = d;
  }

  public static void subtractExpenseRatios()
  {
    for (int i = 0; i < expenseRatios.length; ++i) {
      returnData[i]._sub(FinLib.ret2mul(expenseRatios[i]) - 1.0);
    }
  }

  public static void subtractInflation()
  {
    for (int i = 0; i < returnData.length; ++i) {
      for (FeatureVec x : returnData[i]) {
        int year = TimeLib.ms2date(x.getTime()).getYear();
        assert inflation.containsKey(year);
        double cpi = inflation.get(year);
        x._sub(FinLib.ret2mul(cpi) - 1.0);
      }
    }
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
        if (portfolios.size() % 100000 == 0) {
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
        if (portfolios.size() % 100000 == 0) {
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

  public static boolean dominates(FeatureVec x, FeatureVec y, double[] domdir)
  {
    for (int i = 0; i < domdir.length; ++i) {
      double v = domdir[i] * (x.get(i) - y.get(i));
      if (v < 0.0) return false;
    }
    return true;
  }

  public static void main(String[] args) throws IOException
  {
    // Inspiration: https://portfoliocharts.com/2016/03/07/the-ultimate-portfolio-guide-for-all-types-of-investors/
    File outputDir = new File("g:/web");
    File dataDir = new File("g:/research/finance/");
    assert dataDir.isDirectory();

    loadSimbaData(new File(dataDir, "simba-1972.csv"));
    loadInflationData(new File(dataDir, "simba-inflation-1972.csv"));
    subtractExpenseRatios();
    subtractInflation();
    // keepAssets(12); // TODO for debugging
    System.out.printf("Assets: %d\n", returnData.length);

    names = new String[returnData.length];
    descriptions = new String[returnData.length];
    for (int i = 0; i < returnData.length; ++i) {
      String name = returnData[i].getName();
      name2index.put(name, i);
      names[i] = name;
      // System.out.printf("%d: %s\n", i + 1, name);
    }

    int nMonths = (int) Math.round(TimeLib.monthsBetween(returnData[0].getStartMS(), returnData[0].getEndMS()));
    System.out.printf("Months: %d\n", nMonths);

    List<DiscreteDistribution> portfolios = new ArrayList<>();
    // scanDistributions(1, 5, 20, 100, 20, portfolios);
    scanDistributionsEW(1, 5, portfolios);
    System.out.printf("Portfolios: %d\n", portfolios.size());

    System.out.println("Calculate Returns...");
    Sequence scatter = new Sequence();
    for (DiscreteDistribution dist : portfolios) {
      // String name = dist.toStringWithNames(-1);
      String name = dist.toStringWithNames(0);
      double[] returns = runPortfolio(dist);
      ReturnStats rstats = ReturnStats.calc(name, returns);
      Sequence cumulativeReturns = FinLib.cumulativeFromReturns(returns);
      double tr = FinLib.getTotalReturn(cumulativeReturns);
      double cagr = FinLib.getAnnualReturn(tr, nMonths);
      // scatter.addData(new FeatureVec(name, 2, rstats.min, cagr));
      scatter.addData(new FeatureVec(name, 2, rstats.sdev, cagr));
    }

    System.out.println("Save...");
    Chart.saveScatterPlot(new File(outputDir, "simba-all.html"), "CAGR vs. Standard Deviation", 1200, 600, 1,
        new String[] { "Std Dev", "CAGR" }, scatter);

    // Filter results.
    double[] domdir = new double[] { -1.0, 1.0 };
    System.out.println("Filter...");
    int n = scatter.size();
    for (int i = 0; i < n; ++i) {
      FeatureVec v1 = scatter.get(i);
      if (v1 == null) continue;
      for (int j = i + 1; j < n; ++j) {
        FeatureVec v2 = scatter.get(j);
        if (v2 == null) continue;

        if (dominates(v1, v2, domdir)) {
          scatter.set(j, null);
          continue;
        }

        if (dominates(v2, v1, domdir)) {
          scatter.set(i, null);
          break;
        }
      }
    }

    // Remove all null entries.
    scatter.getData().removeIf(new Predicate<FeatureVec>()
    {
      @Override
      public boolean test(FeatureVec v)
      {
        return v == null;
      }
    });
    System.out.printf("Filtered: %d\n", scatter.size());

    Chart.saveScatterPlot(new File(outputDir, "simba-filtered.html"), "CAGR vs. Standard Deviation", 1200, 600, 3,
        new String[] { "Std Dev", "CAGR" }, scatter);

    // Sort by Sharpe Ratio.
    scatter.getData().sort(new Comparator<FeatureVec>()
    {
      @Override
      public int compare(FeatureVec x, FeatureVec y)
      {
        double sharpe1 = x.get(1) / (x.get(0) + 1e-7);
        double sharpe2 = y.get(1) / (y.get(0) + 1e-7);
        if (sharpe1 > sharpe2) return -1;
        if (sharpe1 < sharpe2) return 1;
        return 0;
      }
    });
    for (FeatureVec v : scatter) {
      double sharpe = v.get(1) / (v.get(0) + 1e-7);
      System.out.printf("%.3f  (%.2f, %.3f)  %s\n", sharpe, v.get(1), v.get(0), v.getName());
    }
  }
}
