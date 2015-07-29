package org.minnen.retiretool;

import java.util.Map;
import java.util.TreeMap;

public class Strategy
{
  /**
   * Invest 100% in asset with highest CAGR over last N months.
   * 
   * @param numMonths calculate CAGR over last N months.
   * @param seqs cumulative returns for each asset.
   * @return sequence of returns using the momentum strategy
   */
  public static Sequence calcMomentumReturnSeq(int numMonths, Sequence... seqs)
  {
    assert seqs.length > 1;
    int N = seqs[0].length();
    for (int i = 1; i < seqs.length; ++i) {
      assert seqs[i].length() == N;
    }

    Sequence momentum = new Sequence("Momentum-" + numMonths);
    double balance = 1.0;
    for (int i = 0; i < N; ++i) {
      // Select asset with best return over previous 12 months.
      int a = Math.max(0, i - numMonths - 1);
      int b = Math.max(0, i - 1);
      Sequence bestSeq = null;
      double bestReturn = 0.0;
      for (Sequence seq : seqs) {
        double r = seq.get(b, 0) / seq.get(a, 0);
        if (bestSeq == null || r > bestReturn) {
          bestSeq = seq;
          bestReturn = r;
        }
      }

      // Invest everything in best asset for this month.
      double lastMonthReturn = bestSeq.get(i, 0) / bestSeq.get(b, 0);
      balance *= lastMonthReturn;
      momentum.addData(balance, seqs[0].getTimeMS(i));
    }

    return momentum;
  }

  /**
   * Invest in risky asset when above SMA, otherwise safe asset.
   * 
   * @param numMonths calculate SMA over past N months
   * @param prices monthly price used for SMA and signal
   * @param risky cumulative returns for risky asset
   * @param safe cumulative returns for safe asset
   * @return sequence of returns using the above/below-SMA strategy
   */
  public static Sequence calcSMAReturnSeq(int numMonths, Sequence prices, Sequence risky, Sequence safe)
  {
    assert risky.length() == safe.length();

    Sequence sma = new Sequence("SMA");
    double balance = 1.0;
    sma.addData(balance, risky.getStartMS());
    for (int i = 1; i < risky.length(); ++i) {
      // Calculate trailing moving average.
      int a = Math.max(0, i - numMonths - 1);
      double ma = 0.0;
      for (int j = a; j < i; ++j) {
        ma += prices.get(j, 0);
      }
      ma /= (i - a);

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
   * @param seqs cumulative returns for each asset
   * @return sequence of returns using the perfect strategy
   */
  public static Sequence calcPerfectReturnSeq(Sequence... seqs)
  {
    assert seqs.length > 0;
    int N = seqs[0].length();
    for (int i = 1; i < seqs.length; ++i) {
      assert seqs[i].length() == N;
    }

    Sequence perfect = new Sequence("Perfect");
    double balance = 1.0;
    perfect.addData(balance, seqs[0].getStartMS());
    for (int i = 1; i < seqs[0].length(); ++i) {
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
   * @return sequence of returns using the mixed strategy.
   */
  public static Sequence calcMixedReturnSeq(Sequence[] assets, double[] targetPercents, int rebalanceMonths)
  {
    final int numAssets = assets.length;
    assert numAssets == targetPercents.length;

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

    // Compute returns for each class and rebalance as requested.
    final int N = assets[0].length();
    Sequence returns = new Sequence("Mixed");
    returns.addData(1.0, assets[0].getStartMS());
    for (int i = 1; i < N; ++i) {
      double balance = 0.0;
      for (int j = 0; j < numAssets; ++j) {
        value[j] *= RetireTool.getReturn(assets[j], i - 1, i);
        balance += value[j];
      }
      returns.addData(balance, assets[0].getTimeMS(i));

      if (rebalanceMonths > 0 && i % rebalanceMonths == 0) {
        for (int j = 0; j < numAssets; ++j) {
          value[j] = balance * targetPercents[j];
        }
      }
    }
    return returns;
  }

  public static Sequence calcMultiMomentumReturnSeq(Sequence s1, Sequence s2, boolean bConservative)
  {
    int N = s1.length();
    assert s2.length() == N;
    Sequence[] seqs = new Sequence[] { s1, s2 };

    int[] numMonths = new int[] { 1, 3, 12 };

    Sequence momentum = new Sequence("MultiMomentum-" + (bConservative ? "Safe" : "Risky"));
    double balance = 1.0;
    for (int i = 0; i < N; ++i) {
      int code = calcMomentumCode(i, numMonths, seqs);

      // Use votes to select asset.
      Sequence bestSeq = null;
      switch (code) {
      case 5:
      case 6:
      case 7:
        bestSeq = s1;
        break;
      case 3:
      case 4:
        bestSeq = bConservative ? s2 : s1;
        break;
      default:
        bestSeq = s2;
      }

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
  };

  public static void calcMomentumStats(Sequence s1, Sequence s2)
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

      // System.out.printf("Code=%d: %.3f, %.3f\n", code, r1, r2);
    }

    for (Map.Entry<Integer, SeqCount> entry : map.entrySet()) {
      System.out.printf("%d: %d, %d\n", entry.getKey(), entry.getValue().n1, entry.getValue().n2);
    }
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
}
