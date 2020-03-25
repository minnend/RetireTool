package org.minnen.retiretool.simba;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.util.Writer;

public class Portfolio implements Comparable<Portfolio>
{
  public final String               name;
  public final DiscreteDistribution allocation;
  public FeatureVec                 stats;

  private static Pattern            pattern = Pattern.compile("^(\\[.*\\])\\s*(\\[.*\\])$");

  public enum Strictness {
    Normal, Strict, Strictest
  };

  public Portfolio(DiscreteDistribution allocation, FeatureVec stats)
  {
    this(allocation.toStringWithNames(0), allocation, stats);
  }

  public Portfolio(String name, DiscreteDistribution allocation, FeatureVec stats)
  {
    this.name = name;
    this.allocation = allocation;
    this.stats = stats;
  }

  public FeatureVec getStats()
  {
    return stats;
  }

  @Override
  public String toString()
  {
    return String.format("%-70s %s", allocation.toStringWithNames(0), stats);
  }

  public static Portfolio fromString(String line)
  {
    Matcher m = pattern.matcher(line);
    if (!m.matches()) return null;
    DiscreteDistribution allocation = DiscreteDistribution.fromStringWithNames(m.group(1));
    FeatureVec stats = FeatureVec.fromString(m.group(2));
    stats.setName(m.group(1));
    return new Portfolio(allocation, stats);
  }

  public static void savePortfolios(File file, List<Portfolio> portfolios) throws IOException
  {
    try (Writer writer = new Writer(file)) {
      for (Portfolio p : portfolios) {
        writer.writef("%s\n", p);
      }
    }
  }

  public static List<Portfolio> loadPortfolios(File file) throws IOException
  {
    System.out.printf("Loading portfolio data file: [%s]\n", file.getPath());
    List<Portfolio> portfolios = new ArrayList<>();
    try (BufferedReader in = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = in.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) continue;
        portfolios.add(Portfolio.fromString(line));
      }
    }
    return portfolios;
  }

  /** @return 0 if portfolio stats are similar else 1 if this object dominates and -1 if `other` dominates. */
  public int dominates(Portfolio other, double[] domdir, double[] threshold, Strictness strictness)
  {
    int nLoss = 0;
    int nTiePlus = 0; // within threshold but slightly better
    int nTieMinus = 0; // within threshold but slightly worse
    int nWin = 0;
    int nCategories = 0;
    for (int i = 0; i < domdir.length; ++i) {
      if (Math.abs(domdir[i]) < 0.1) continue; // skip this feature
      ++nCategories;
      double v = stats.get(i) - other.stats.get(i);
      if (domdir[i] > 0) { // bigger is better
        if (v < -threshold[i]) ++nLoss;
        else if (v <= threshold[i]) { // within threshold range => tie
          if (v >= 0) ++nTiePlus;
          else++nTieMinus;
        } else {
          assert v > threshold[i];
          ++nWin;
        }
      } else { // smaller is better
        assert domdir[i] < 0;
        if (v > threshold[i]) ++nLoss;
        else if (v >= -threshold[i]) {
          if (v <= 0) ++nTiePlus;
          else++nTieMinus;
        } else {
          assert v < -threshold[i];
          ++nWin;
        }
      }
    }

    if (strictness == Strictness.Strictest) {
      // Strictest sense: must win all categories.
      if (nWin == nCategories) {
        assert nLoss == 0 && nTieMinus == 0 && nTiePlus == 0;
        return 1;
      }
      if (nLoss == nCategories) {
        assert nWin == 0 && nTieMinus == 0 && nTiePlus == 0;
        return -1;
      }
    } else if (strictness == Strictness.Strict) {
      // Strict sense: must win at least one plus can't lose any ties.
      if (nLoss == 0 && nWin > 0 && nTieMinus == 0) return 1;
      if (nLoss > 0 && nWin == 0 && nTiePlus == 0) return -1;
    } else {
      assert strictness == Strictness.Normal;
      // Most relaxed: must win at least one, for ties, must win more then lose.
      if (nLoss == 0 && nWin > 0 && nTiePlus > nTieMinus) return 1;
      if (nLoss > 0 && nWin == 0 && nTiePlus < nTieMinus) return -1;
    }
    return 0;
  }

  /** Remove duplicate portfolios. */
  public static List<Portfolio> removeDuplicates(List<Portfolio> portfolios)
  {
    int n = portfolios.size();
    for (int i = 0; i < n; ++i) {
      Portfolio p1 = portfolios.get(i);
      if (p1 == null) continue;
      for (int j = i + 1; j < n; ++j) {
        Portfolio p2 = portfolios.get(j);
        if (p2 == null) continue;
        if (p1.allocation.isSimilar(p2.allocation, 1e-4)) {
          portfolios.set(j, null);
        }
      }
    }
    // Remove all null entries.
    portfolios = portfolios.stream().filter(x -> x != null).collect(Collectors.toList());
    return portfolios;
  }

  /** Remove portfolios that are "dominated" by another portfolio. */
  public static List<Portfolio> removeNonDominators(List<Portfolio> portfolios, double[] domdir, double[] thresholds,
      Strictness strictness)
  {
    assert domdir.length == thresholds.length;
    int n = portfolios.size();
    for (int i = 0; i < n; ++i) {
      Portfolio p1 = portfolios.get(i);
      if (p1 == null) continue;
      for (int j = i + 1; j < n; ++j) {
        Portfolio p2 = portfolios.get(j);
        if (p2 == null) continue;
        int dom = p1.dominates(p2, domdir, thresholds, strictness);
        if (dom > 0) {
          portfolios.set(j, null);
        } else if (dom < 0) {
          portfolios.set(i, null);
          break;
        }
      }
    }
    // Remove all null entries.
    portfolios = portfolios.stream().filter(x -> x != null).collect(Collectors.toList());
    return portfolios;
  }

  @Override
  public int compareTo(Portfolio other)
  {
    if (other == this) return 0;
    if (other == null) return -1;

    int n = stats.getNumDims();
    for (int i = 0; i < n; ++i) {
      double a = stats.get(i);
      double b = other.stats.get(i);
      if (a < b) return 1;
      if (a > b) return -1;
    }
    return 0;
  }
}
