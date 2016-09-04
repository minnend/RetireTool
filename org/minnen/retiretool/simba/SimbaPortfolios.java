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
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;

// TODO
// Removing wellington and other balanced fund
// look at other period returns -- how much are we giving up by optimizing for worst?
// reduce returns for best years
// generate synthetic data

public class SimbaPortfolios
{
  public static final int                  nLongYears = 15;
  public static final int                  nSigDig    = 0;

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

  /** @return time (in ms) for the given year index */
  public static long index2ms(int iYear)
  {
    return returnData[0].getTimeMS(iYear);
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

  public static Sequence runPortfolio(DiscreteDistribution targetAllocation)
  {
    int nYears = returnData[0].size();
    Sequence returns = new Sequence(targetAllocation.toStringWithNames(nSigDig));
    returns.setMeta("portfolio", targetAllocation);
    for (int iYear = 0; iYear < nYears; ++iYear) {
      double r = returnForYear(iYear, targetAllocation);
      returns.addData(FinLib.mul2ret(r), index2ms(iYear));
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
          else ++nTieMinus;
        } else ++nWin;
      } else {
        if (v > threshold[i]) ++nLoss;
        else if (v > -threshold[i]) {
          if (v <= 0) ++nTiePlus;
          else ++nTieMinus;
        } else ++nWin;
      }
    }
    return nLoss == 0 && nWin > 0 && nTiePlus >= nTieMinus;
  }

  /** @return Sequence containing CAGR for each `nYears` period. */
  public static Sequence calcLongReturns(Sequence cumulativeReturns, int nYears)
  {
    Sequence seq = new Sequence(cumulativeReturns.getName() + String.format(" %d year returns", nYears));
    seq.copyMeta(cumulativeReturns);
    int nMonths = (int) Math.round(TimeLib.monthsBetween(cumulativeReturns.getTimeMS(0),
        cumulativeReturns.getTimeMS(nYears)));
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
    // keepAssets(15); // TODO for debugging
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
    // scanDistributions(3, 5, 10, 40, 5, portfolios);
    scanDistributionsEW(1, 4, portfolios);
    System.out.printf("Portfolios: %d\n", portfolios.size());

    System.out.println("Calculate Returns...");
    long start = TimeLib.getTime();
    Sequence portfolioStats = new Sequence();
    // List<Sequence> portfolioAnnualReturns = new ArrayList<>();
    // List<Sequence> portfolioLongReturns = new ArrayList<>();
    for (int i = 0; i < portfolios.size(); ++i) {
      DiscreteDistribution dist = portfolios.get(i);
      // String name = dist.toStringWithNames(-1);
      String name = dist.toStringWithNames(nSigDig);
      Sequence returnSeq = runPortfolio(dist);
      // portfolioAnnualReturns.add(returnSeq);
      Sequence cumulativeReturns = FinLib.cumulativeFromReturns(returnSeq);
      Sequence longReturns = calcLongReturns(cumulativeReturns, nLongYears);
      // portfolioLongReturns.add(longReturns);
      double worstPeriod = longReturns.getMin().get(0);
      ReturnStats rstats = ReturnStats.calc(name, returnSeq.extractDim(0));
      CumulativeStats cstats = CumulativeStats.calc(cumulativeReturns, false);
      // FeatureVec fv = new FeatureVec(name, 5, cstats.cagr, rstats.sdev, cstats.drawdown, rstats.min, worstPeriod);
      FeatureVec fv = new FeatureVec(name, 5, worstPeriod, cstats.cagr, rstats.sdev, rstats.min, -cstats.drawdown);
      portfolioStats.addData(fv);

      if (i % 100000 == 0 && i > 0) System.out.printf("%d  (%.2f%%)\n", i, 100.0 * (i + 1) / portfolios.size());
    }
    System.out.printf("Time: %s  (%d)\n", TimeLib.formatDuration(TimeLib.getTime() - start), portfolios.size());

    // findWinners(new ArrayList<>(portfolioLongReturns), 1.0);
    // System.exit(0);

    int xindex = 4;
    int yindex = 0;
    int zindex = 1;
    String[] descriptions = new String[] { String.format("Worst %d-year Period", nLongYears), "CAGR", "Std Dev",
        "Worst Year", "Max Drawdown" };

    Sequence scatter = new Sequence();
    // System.out.println("Save...");
    // for (FeatureVec v : portfolioStats) {
    // scatter.addData(new FeatureVec(v.getName(), 3, v.get(xindex), v.get(yindex), v.get(zindex)));
    // }

    // Chart.saveScatterPlot(new File(outputDir, "simba-all.html"), descriptions[yindex] + " vs. " +
    // descriptions[xindex],
    // 1200, 600, 1, new String[] { descriptions[xindex], descriptions[yindex], descriptions[zindex] }, scatter);

    // Filter results.
    double[] domdir = new double[] { 1, 0, 0, 0, 1 };
    double[] thresholds = new double[] { 0.01, 0.01, 0.01, 0.1, 0.1 };
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
      scatter.addData(new FeatureVec(v.getName(), 3, v.get(xindex), v.get(yindex), v.get(zindex)));
    }

    // Chart.saveScatterPlot(new File(outputDir, "simba-filtered.html"), "CAGR vs. Standard Deviation", 1200, 600, 3,
    // new String[] { "Std Dev", "CAGR" }, scatter);
    Chart.saveScatterPlot(new File(outputDir, "simba-filtered.html"), descriptions[yindex] + " vs. "
        + descriptions[xindex], 1200, 600, 3, new String[] { descriptions[xindex], descriptions[yindex],
        descriptions[zindex], descriptions[zindex] }, scatter);

    // Sort by Sharpe Ratio.
    portfolioStats.getData().sort(new Comparator<FeatureVec>()
    {
      @Override
      public int compare(FeatureVec x, FeatureVec y)
      {
        double a = x.get(0);
        double b = y.get(0);
        // double sharpe1 = x.get(0) / (x.get(1) + 1e-7);
        // double sharpe2 = y.get(0) / (y.get(1) + 1e-7);
        // if (sharpe1 > sharpe2) return -1;
        // if (sharpe1 < sharpe2) return 1;
        if (a < b) return 1;
        if (a > b) return -1;
        return 0;
      }
    });
    for (FeatureVec v : portfolioStats) {
      double sharpe = v.get(1) / (v.get(2) + 1e-7);
      System.out.printf("%.3f %-32s %s\n", sharpe, v, v.getName());
    }
  }
}
