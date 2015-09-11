package org.minnen.retiretool;

import java.util.Map;
import java.util.TreeMap;

public class Strategy
{
  public enum Disposition {
    Safe, Cautious, Moderate, Risky
  }

  /**
   * Invest 100% in asset with highest CAGR over last N months.
   * 
   * @param nMonths calculate CAGR over last N months.
   * @param iStart index of month to start investing.
   * @param seqs cumulative returns for each asset.
   * @return sequence of returns using the momentum strategy
   */
  public static Sequence calcMomentumReturns(int nMonths, int iStart, Sequence... seqs)
  {
    assert seqs.length > 1;
    int N = seqs[0].length();
    for (int i = 1; i < seqs.length; ++i) {
      assert seqs[i].length() == N;
    }

    Sequence momentum = new Sequence("Momentum-" + nMonths);
    double balance = 1.0;
    int nCorrect = 0, nWrong = 0;
    for (int i = iStart; i < N; ++i) {
      // Select asset with best return over previous 12 months.
      int a = Math.max(0, i - nMonths - 1);
      int b = Math.max(0, i - 1);
      Sequence bestSeq = null;
      double bestReturn = 0.0, correctReturn = 1.0;
      for (Sequence seq : seqs) {
        double r = FinLib.getReturn(seq, a, b);
        if (r > bestReturn) {
          bestSeq = seq;
          bestReturn = r;
        }

        r = FinLib.getReturn(seq, b, i);
        if (r > correctReturn) {
          correctReturn = r;
        }
      }

      // Invest everything in best asset for this month.
      // No bestSeq => hold everything in cash for no gain and no loss.
      double realizedReturn = bestSeq != null ? FinLib.getReturn(bestSeq, b, i) : 1.0;
      balance *= realizedReturn;
      momentum.addData(balance, seqs[0].getTimeMS(i));

      if (realizedReturn > correctReturn - 1e-6) {
        ++nCorrect;
      } else {
        ++nWrong;
      }
    }
    System.out.printf("Momentum-%d: %.2f%% Correct (%d vs. %d / %d)\n", nMonths, nCorrect * 100.0
        / (nCorrect + nWrong), nCorrect, nWrong, nCorrect + nWrong);
    return momentum;
  }

  /**
   * Calculate the SMA in [iStart, iEnd) for the given sequence.
   * 
   * @param iStart first index (inclusive)
   * @param iEnd last index (exclusive)
   * @param prices sequence that holds price (assumed to be in dim=0)
   * @return average of prices in [iStart, iEnd)
   */
  private static double calcSma(int iStart, int iEnd, Sequence prices)
  {
    if (iEnd <= iStart) {
      return 0.0;
    }
    double sma = 0.0;
    for (int j = iStart; j < iEnd; ++j) {
      sma += prices.get(j, 0);
    }
    sma /= (iEnd - iStart);
    return sma;
  }

  /**
   * Invest in risky asset when above SMA, otherwise safe asset.
   * 
   * @param numMonths calculate SMA over past N months
   * @param iStart index of month to start investing.
   * @param prices monthly price used for SMA and signal
   * @param risky cumulative returns for risky asset
   * @param safe cumulative returns for safe asset
   * @return sequence of returns using the above/below-SMA strategy
   */
  public static Sequence calcSMAReturns(int numMonths, int iStart, Sequence prices, Sequence risky, Sequence safe)
  {
    assert risky.length() == safe.length();

    Sequence sma = new Sequence("SMA-" + numMonths);
    double balance = 1.0;
    sma.addData(balance, risky.getTimeMS(iStart));
    for (int i = iStart + 1; i < risky.length(); ++i) {
      // Calculate trailing moving average.
      int a = Math.max(0, i - numMonths - 1);
      double ma = calcSma(a, i, prices);

      // Test above / below moving average.
      double lastMonthReturn;
      double price = prices.get(i - 1, 0);
      if (price > ma) {
        lastMonthReturn = risky.get(i, 0) / risky.get(i - 1, 0);
      } else {
        lastMonthReturn = safe.get(i, 0) / safe.get(i - 1, 0);
      }
      balance *= lastMonthReturn;
      sma.addData(balance, risky.getTimeMS(i));
    }
    return sma;
  }

  /**
   * Every month, invest 100% in the best asset.
   * 
   * @param iStart index of month to start investing.
   * @param seqs cumulative returns for each asset
   * @return sequence of returns using the perfect strategy
   */
  public static Sequence calcPerfectReturns(int iStart, Sequence... seqs)
  {
    assert seqs.length > 0;
    int N = seqs[0].length();
    for (int i = 1; i < seqs.length; ++i) {
      assert seqs[i].length() == N;
    }

    Sequence perfect = new Sequence("Perfect");
    double balance = 1.0;
    perfect.addData(balance, seqs[0].getTimeMS(iStart));
    for (int i = iStart + 1; i < seqs[0].length(); ++i) {
      double bestReturn = 0.0;
      for (Sequence seq : seqs) {
        double r = seq.get(i, 0) / seq.get(i - 1, 0);
        if (r > bestReturn) {
          bestReturn = r;
        }
      }
      balance *= bestReturn;
      perfect.addData(balance, seqs[0].getTimeMS(i));
    }
    return perfect;
  }

  /**
   * Calculate returns as a mixed of other returns.
   * 
   * @param assets cumulative returns for each asset.
   * @param targetPercents target percentage for each asset (will be normalized).
   * @param rebalanceMonths rebalance every N months (zero for never).
   * @param rebalanceBand rebalance when allocation is too far from target percent (zero for never, 5.0 = 5% band).
   * @return sequence of returns using the mixed strategy.
   */
  public static Sequence calcMixedReturns(Sequence[] assets, double[] targetPercents, int rebalanceMonths,
      double rebalanceBand)
  {
    final int numAssets = assets.length;
    assert numAssets == targetPercents.length;
    for (int i = 1; i < numAssets; ++i) {
      assert assets[i].length() == assets[0].length();
    }

    // Normalize target percentages to sum to 1.0.
    double targetSum = 0.0;
    for (int i = 0; i < numAssets; ++i) {
      targetSum += targetPercents[i];
    }
    assert targetSum > 0.0;
    for (int i = 0; i < numAssets; ++i) {
      targetPercents[i] /= targetSum;
    }

    // Initialize asset values to correct percentage.
    double[] value = new double[numAssets];
    for (int i = 0; i < numAssets; ++i) {
      value[i] = targetPercents[i];
    }

    // Convert percentage to decimal.
    rebalanceBand /= 100.0;

    // Compute returns for each class and rebalance as requested.
    final int N = assets[0].length();
    Sequence returns = new Sequence("Mixed");
    returns.addData(1.0, assets[0].getStartMS());
    int numRebalances = 0;
    for (int index = 1; index < N; ++index) {
      double balance = 0.0;
      for (int i = 0; i < numAssets; ++i) {
        value[i] *= FinLib.getReturn(assets[i], index - 1, index);
        balance += value[i];
      }
      returns.addData(balance, assets[0].getTimeMS(index));

      // Figure out if we need to rebalance.
      boolean rebalanceNow = (rebalanceMonths > 0 && index % rebalanceMonths == 0);
      if (!rebalanceNow && rebalanceBand > 0.0) {
        // Check each asset to see if it has exceeded the allocation band.
        for (int i = 0; i < numAssets; ++i) {
          double diff = (value[i] / balance - targetPercents[i]);
          if (Math.abs(diff) > rebalanceBand) {
            rebalanceNow = true;
            break;
          }
        }
      }

      // Rebalance if needed.
      if (rebalanceNow) {
        ++numRebalances;
        for (int j = 0; j < numAssets; ++j) {
          value[j] = balance * targetPercents[j];
        }
      }
    }

    // System.out.printf("Rebalances: %d\n", numRebalances);
    return returns;
  }

  public static Sequence calcMultiMomentumReturns(int iStart, Sequence risky, Sequence safe, Disposition disposition)
  {
    int N = risky.length();
    assert safe.length() == N;
    Sequence[] seqs = new Sequence[] { risky, safe };

    int[] numMonths = new int[] { 1, 3, 12 };

    Sequence momentum = new Sequence("MultiMom-" + disposition);
    double balance = 1.0;
    for (int i = iStart; i < N; ++i) {
      int code = calcMomentumCode(i, numMonths, seqs);

      // Use votes to select asset.
      Sequence bestSeq = selectAsset(code, disposition, risky, safe);

      // Invest everything in best asset for this month.
      double lastMonthReturn = bestSeq.get(i, 0) / bestSeq.get(Math.max(0, i - 1), 0);
      balance *= lastMonthReturn;
      momentum.addData(balance, seqs[0].getTimeMS(i));
    }

    return momentum;
  }

  static class SeqCount
  {
    public int n1 = 0, n2 = 0;
    public double r1 = 1.0, r2 = 1.0;
  };

  public static void calcMultiMomentumStats(Sequence s1, Sequence s2)
  {
    int N = s1.length();
    assert s2.length() == N;
    Sequence[] seqs = new Sequence[] { s1, s2 };

    int[] numMonths = new int[] { 1, 3, 12 };
    Map<Integer, SeqCount> map = new TreeMap<Integer, SeqCount>();

    for (int i = numMonths[numMonths.length - 1] + 1; i < N; ++i) {
      int code = calcMomentumCode(i, numMonths, seqs);

      // Record result.
      SeqCount sc;
      if (map.containsKey(code)) {
        sc = map.get(code);
      } else {
        sc = new SeqCount();
        map.put(code, sc);
      }
      double r1 = s1.get(i, 0) / s1.get(i - 1, 0);
      double r2 = s2.get(i, 0) / s2.get(i - 1, 0);
      if (r1 >= r2) {
        ++sc.n1;
      } else {
        ++sc.n2;
      }
      sc.r1 *= r1;
      sc.r2 *= r2;
    }

    printStats("Momentum Statistics:", map);
  }

  public static int calcMomentumCode(int index, int[] numMonths, Sequence... seqs)
  {
    final int b = Math.max(0, index - 1);
    int code = 0;
    for (int j = 0; j < numMonths.length; ++j) {
      code <<= 1;
      final int a = Math.max(0, index - numMonths[j] - 1);
      Sequence bestSeq = null;
      double bestReturn = 0.0;
      for (Sequence seq : seqs) {
        double r = seq.get(b, 0) / seq.get(a, 0);
        if (bestSeq == null || r > bestReturn) {
          bestSeq = seq;
          bestReturn = r;
        }
      }
      if (bestSeq == seqs[0]) {
        ++code;
      }
    }
    return code;
  }

  public static int calcSmaCode(int index, int[] numMonths, Sequence prices, Sequence risky, Sequence safe)
  {
    int code = 0;
    for (int j = 0; j < numMonths.length; ++j) {
      code <<= 1;
      final int a = Math.max(0, index - numMonths[j] - 1);
      double sma = calcSma(a, index, prices);
      double price = prices.get(Math.max(index - 1, 0), 0);

      if (price > sma) {
        ++code;
      }
    }
    return code;
  }

  public static void calcSmaStats(Sequence prices, Sequence risky, Sequence safe)
  {
    int N = risky.length();
    assert safe.length() == N;

    int[] numMonths = new int[] { 1, 5, 10 };
    Map<Integer, SeqCount> map = new TreeMap<Integer, SeqCount>();

    for (int i = numMonths[numMonths.length - 1] + 1; i < N; ++i) {
      int code = calcSmaCode(i, numMonths, prices, risky, safe);

      // Record result.
      SeqCount sc;
      if (map.containsKey(code)) {
        sc = map.get(code);
      } else {
        sc = new SeqCount();
        map.put(code, sc);
      }
      double r1 = risky.get(i, 0) / risky.get(i - 1, 0);
      double r2 = safe.get(i, 0) / safe.get(i - 1, 0);
      if (r1 >= r2) {
        ++sc.n1;
      } else {
        ++sc.n2;
      }
      sc.r1 *= r1;
      sc.r2 *= r2;
    }

    printStats("SMA Statistics:", map);
  }

  public static Sequence calcMultiSmaReturns(int iStart, Sequence prices, Sequence risky, Sequence safe,
      Disposition disposition)
  {
    int N = risky.length();
    assert safe.length() == N;

    int[] numMonths = new int[] { 1, 5, 10 };

    Sequence sma = new Sequence("MultiSMA-" + disposition);
    double balance = 1.0;
    for (int i = iStart; i < N; ++i) {
      int code = calcSmaCode(i, numMonths, prices, risky, safe);

      // Use votes to select asset.
      Sequence bestSeq = selectAsset(code, disposition, risky, safe);

      // Invest everything in best asset for this month.
      double lastMonthReturn = bestSeq.get(i, 0) / bestSeq.get(Math.max(0, i - 1), 0);
      balance *= lastMonthReturn;
      sma.addData(balance, risky.getTimeMS(i));
    }

    return sma;
  }

  public static Sequence selectAsset(int code, Disposition disposition, Sequence risky, Sequence safe)
  {
    assert code >= 0 && code <= 7;

    // Complete support.
    if (code == 7) {
      return risky;
    }

    // Shortest + support => always risky.
    if (code >= 5) {
      return disposition == Disposition.Safe ? safe : risky;
    }

    // Not shortest and zero or one other => always safe.
    if (code <= 2) {
      return safe;
    }

    // Not shortest but both others.
    if (code == 3) {
      return disposition == Disposition.Safe || disposition == Disposition.Cautious ? safe : risky;
    }

    // Only short-term support.
    assert code == 4;
    return disposition == Disposition.Risky ? risky : safe;
  }

  public static void printStats(String title, Map<Integer, SeqCount> map)
  {
    System.out.println(title);
    for (Map.Entry<Integer, SeqCount> entry : map.entrySet()) {
      SeqCount sc = entry.getValue();
      int n = sc.n1 + sc.n2;
      double p = (n == 0 ? 0.0 : 100.0 * (sc.n1) / n);
      System.out.printf("%d: %.1f%%  %.3fx  %3d  [%d, %d]\n", entry.getKey(), p, sc.r1 / sc.r2, n, sc.n1, sc.n2);
    }
  }
}
