package org.minnen.retiretool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.data.SequenceStore;
import org.minnen.retiretool.predictor.monthly.AssetPredictor;
import org.minnen.retiretool.predictor.monthly.SMAPredictor;
import org.minnen.retiretool.predictor.monthly.Multi3Predictor.Disposition;
import org.minnen.retiretool.stats.WinStats;

public class Strategy
{
  public static Sequence calcReturns(AssetPredictor predictor, int iStart, int nMinTradeGap, Sequence... seqs)
  {
    assert seqs.length > 1;
    int N = seqs[0].length();
    for (int i = 0; i < seqs.length; ++i) {
      assert seqs[i].length() == N;
      assert seqs[i].getStartMS() == seqs[0].getStartMS();
      assert predictor.store.has(seqs[0].getName());
    }
    N = 24; // TODO for debug
    System.out.printf("Predictor: %s\n", predictor.name);

    predictor.reset();
    final double principal = 1000.0; // TODO principal might matter due to slippage or min purchase reqs
    double balance = principal;
    Sequence returns = new Sequence(predictor.name);
    returns.addData(balance, seqs[0].getTimeMS(iStart));
    int iLastTrade = -1;
    double[] prevDistribution = new double[seqs.length];
    for (int t = iStart + 1; t < N; ++t) {
      System.out.printf("Process: %d = [%s]\n", t, TimeLib.formatMonth(seqs[0].getTimeMS(t)));
      assert seqs[0].getTimeMS(t) == seqs[1].getTimeMS(t);
      predictor.store.lock(TimeLib.TIME_BEGIN, seqs[0].getTimeMS(t) - 1);
      double[] distribution = predictor.selectDistribution(seqs);
      assert distribution.length == seqs.length;
      predictor.store.unlock();

      // Is it too soon to trade again?
      if (iLastTrade >= 0 && t - iLastTrade <= nMinTradeGap) {
        assert prevDistribution.length == distribution.length;
        System.arraycopy(prevDistribution, 0, distribution, 0, distribution.length);
      } else {
        if (!Library.almostEqual(prevDistribution, distribution, 1e-5)) {
          iLastTrade = t;
          System.arraycopy(distribution, 0, prevDistribution, 0, distribution.length);
        }
      }

      // Find correct answer (sequence with highest return for current month)
      double realizedReturn = 0.0;
      double correctReturn = 1.0;
      int iCorrect = -1;
      for (int iSeq = 0; iSeq < seqs.length; ++iSeq) {
        Sequence seq = seqs[iSeq];
        double r = FinLib.getReturn(seq, t - 1, t);
        realizedReturn += distribution[iSeq] * r;
        if (r > correctReturn) {
          correctReturn = r;
          iCorrect = iSeq;
        }
      }
      predictor.feedback(seqs[0].getTimeMS(t), iCorrect, correctReturn);
      balance *= realizedReturn;

      System.out.printf(" %s  $%.2f  (%f)\n", distribution[0] > 0.0 ? "Risky" : "Safe", balance, realizedReturn);

      returns.addData(balance, seqs[0].getTimeMS(t));
    }

    // Return cumulative returns normalized so that starting value is 1.0.
    returns._div(principal);
    assert Math.abs(returns.getFirst(0) - 1.0) < 1e-6;
    return returns;
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
      double bestReturn = 1.0; // can always hold cash
      for (Sequence seq : seqs) {
        double r = FinLib.getReturn(seq, i - 1, i);
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
   * Calculate returns from multiple asset with rebalancing.
   * 
   * @param assets cumulative returns for each asset.
   * @param targetPercents target percentage for each asset (will be normalized).
   * @param nLookback rebalance every N months (zero for never).
   * @param band rebalance when allocation is too far from target percent (zero for never, 5.0 = 5% band).
   * @return sequence of returns using the mixed strategy.
   */
  public static Sequence calcReturns(Sequence[] assets, RebalanceInfo rebalance)
  {
    final int numAssets = assets.length;
    assert numAssets == rebalance.targetPercents.length;
    for (int i = 1; i < numAssets; ++i) {
      assert assets[i].length() == assets[0].length();
    }

    // Initialize asset values to correct percentage.
    double[] value = new double[numAssets];
    System.arraycopy(rebalance.targetPercents, 0, value, 0, value.length);

    // Convert percentage to decimal.
    double rebalanceBand = rebalance.band / 100.0;

    // Compute returns for each class and rebalance as requested.
    final int N = assets[0].length();
    Sequence returns = new Sequence("Mixed");
    returns.addData(1.0, assets[0].getStartMS());
    rebalance.nRebalances = 0;
    for (int index = 1; index < N; ++index) {
      double balance = 0.0;
      for (int i = 0; i < numAssets; ++i) {
        value[i] *= FinLib.getReturn(assets[i], index - 1, index);
        balance += value[i];
      }
      returns.addData(balance, assets[0].getTimeMS(index));

      // Figure out if we need to rebalance.
      boolean rebalanceNow = (rebalance.nMonths > 0 && index % rebalance.nMonths == 0);
      if (!rebalanceNow && rebalanceBand > 0.0) {
        // Check each asset to see if it has exceeded the allocation band.
        for (int i = 0; i < numAssets; ++i) {
          double diff = (value[i] / balance - rebalance.targetPercents[i]);
          if (Math.abs(diff) > rebalanceBand) {
            rebalanceNow = true;
            break;
          }
        }
      }

      // Rebalance if needed.
      if (rebalanceNow) {
        ++rebalance.nRebalances;
        for (int j = 0; j < numAssets; ++j) {
          value[j] = balance * rebalance.targetPercents[j];
        }
      }
    }

    // System.out.printf("Rebalances: %d\n", numRebalances);
    return returns;
  }

  static class SeqCount implements Comparable<SeqCount>
  {
    public String name;
    public int    n1 = 0, n2 = 0;
    public double r1 = 1.0, r2 = 1.0;

    public SeqCount(String name)
    {
      this.name = name;
    }

    public void update(double r1, double r2)
    {
      if (r1 > 1.0) { // TODO: compare to 1.0 or r2?
        ++n1;
      } else {
        ++n2;
      }
      this.r1 *= r1;
      this.r2 *= r2;
    }

    public double expectedReturn()
    {
      return FinLib.mul2ret(Math.pow(r1, 12.0 / n1));
    }

    public double winRate()
    {
      return (n1 == 0 ? 0.0 : 100.0 * n1 / (n1 + n2));
    }

    @Override
    public String toString()
    {
      return String.format("%s: %.1f%%  %.3f%%  %3d  [%d, %d]", name, winRate(), expectedReturn(), n1 + n2, n1, n2);
    }

    @Override
    public int compareTo(SeqCount other)
    {
      double ea = this.expectedReturn();
      double eb = other.expectedReturn();
      // double ea = this.winRate();
      // double eb = other.winRate();
      if (ea > eb)
        return 1;
      if (ea < eb)
        return -1;
      return 0;
    }
  };

  public static void calcMultiMomentumStats(Sequence risky, Sequence safe)
  {
    int N = risky.length();
    assert safe.length() == N;
    Sequence[] seqs = new Sequence[] { risky, safe };

    int[] numMonths = new int[] { 1, 3, 12 };
    Map<Integer, SeqCount> map = new TreeMap<Integer, SeqCount>();

    for (int i = numMonths[numMonths.length - 1] + 1; i < N; ++i) {
      int code = calcMomentumCode(i - 1, numMonths, seqs);

      // Record result.
      SeqCount sc;
      if (map.containsKey(code)) {
        sc = map.get(code);
      } else {
        sc = new SeqCount(String.format("Code=%d", code));
        map.put(code, sc);
      }
      double r1 = FinLib.getReturn(risky, i - 1, i);
      double r2 = FinLib.getReturn(safe, i - 1, i);
      sc.update(r1, r2);
    }

    printStats("Momentum Statistics:", map);
  }

  /**
   * Calculate multi-momentum code.
   * 
   * @param index last accessible index
   * @param numMonths number of months of look-back for momentum
   * @param seqs list of sequences to select bewteen
   * @return multi-momentum code
   */
  public static int calcMomentumCode(int index, int[] numMonths, Sequence... seqs)
  {
    int code = 0;
    for (int j = 0; j < numMonths.length; ++j) {
      code <<= 1;
      final int a = Math.max(0, index - numMonths[j]);
      Sequence bestSeq = null;
      double bestReturn = 0.0;
      for (Sequence seq : seqs) {
        double r = FinLib.getReturn(seq, a, index);
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

  /**
   * Calculate multi-sma code.
   * 
   * @param index last accessible index
   * @param numMonths number of months to average
   * @param prices sequences of prices
   * @param iPrice dimension index that holds price data
   * @return multi-sma code.
   */
  public static int calcSmaCode(int index, int[] numMonths, Sequence prices, int iPrice)
  {
    int code = 0;
    for (int i = 0; i < numMonths.length; ++i) {
      code <<= 1;
      final int a = Math.max(0, index - numMonths[i]);
      double sma = prices.average(a, index).get(iPrice);
      double price = prices.get(index, iPrice);
      if (price > sma) {
        ++code;
      }
    }
    return code;
  }

  public static void calcMultiSmaStats(int iStart, Sequence prices, int iPrice, Sequence risky, Sequence safe)
  {
    int N = risky.length();
    assert safe.length() == N;

    int[] numMonths = new int[] { 1, 5, 10 };
    Map<Integer, SeqCount> map = new TreeMap<Integer, SeqCount>();

    for (int i = numMonths[numMonths.length - 1] + 1; i < N; ++i) {
      int code = calcSmaCode(i, numMonths, prices, iPrice);

      // Record result.
      SeqCount sc;
      if (map.containsKey(code)) {
        sc = map.get(code);
      } else {
        sc = new SeqCount(String.format("Code=%d", code));
        map.put(code, sc);
      }
      double r1 = FinLib.getReturn(risky, i - 1, i);
      double r2 = FinLib.getReturn(safe, i - 1, i);
      sc.update(r1, r2);
    }

    printStats("SMA Statistics:", map);
  }

  public static void calcSmaStats(Sequence prices, Sequence risky, Sequence safe, SequenceStore store)
  {
    int N = risky.length();
    assert safe.matches(risky);
    assert prices.matches(risky);

    final int M = 12;
    AssetPredictor[] smaPredictors = new SMAPredictor[M];
    SeqCount[][][][] r = new SeqCount[M][M][M][M];
    SeqCount rAll = new SeqCount("All-12");
    List<SeqCount> all = new ArrayList<>();
    all.add(rAll);
    for (int i = 0; i < M; ++i) {
      smaPredictors[i] = new SMAPredictor(i + 1, prices.getName(), 0, store);
      r[i][0][0][0] = new SeqCount(String.format("SMA[%d]", i + 1));
      all.add(r[i][0][0][0]);
      for (int j = i + 1; j < M; ++j) {
        r[i][j][0][0] = new SeqCount(String.format("SMA[%d,%d]", i + 1, j + 1));
        all.add(r[i][j][0][0]);
        for (int k = j + 1; k < M; ++k) {
          r[i][j][k][0] = new SeqCount(String.format("SMA[%d,%d,%d]", i + 1, j + 1, k + 1));
          all.add(r[i][j][k][0]);
          for (int a = k + 1; a < M; ++a) {
            r[i][j][k][a] = new SeqCount(String.format("SMA[%d,%d,%d,%d]", i + 1, j + 1, k + 1, a + 1));
            // all.add(r[i][j][k][a]);
          }
        }
      }
    }

    for (int t = M + 1; t < N; ++t) {
      double r1 = risky.get(t, 0) / risky.get(t - 1, 0);
      double r2 = safe.get(t, 0) / safe.get(t - 1, 0);

      store.lock(prices.getStartMS(), prices.getTimeMS(t) - 1);

      boolean bAll = true;
      for (int i = 0; i < M; ++i) {
        if (smaPredictors[i].selectAsset(risky, safe) != 0) {
          bAll = false;
          break;
        }
      }
      if (bAll) {
        rAll.update(r1, r2);
      }

      for (int i = 0; i < M; ++i) {
        int pred = smaPredictors[i].selectAsset(risky, safe);
        if (pred == 0) {
          r[i][0][0][0].update(r1, r2);
          for (int j = i + 1; j < M; ++j) {
            pred = smaPredictors[j].selectAsset(risky, safe);
            if (pred == 0) {
              r[i][j][0][0].update(r1, r2);
              for (int k = j + 1; k < M; ++k) {
                pred = smaPredictors[k].selectAsset(risky, safe);
                if (pred == 0) {
                  r[i][j][k][0].update(r1, r2);
                  for (int a = k + 1; a < M; ++a) {
                    pred = smaPredictors[a].selectAsset(risky, safe);
                    if (pred == 0) {
                      r[i][j][k][a].update(r1, r2);
                    }
                  }
                }
              }
            }
          }
        }
      }
      store.unlock();
    }

    Collections.sort(all);
    for (int i = 0; i < all.size(); ++i) {
      System.out.println(all.get(i));
    }
  }

  public static void printStats(String title, Map<Integer, SeqCount> map)
  {
    // Calculate total number of occurrences.
    int N = 0;
    for (Map.Entry<Integer, SeqCount> entry : map.entrySet()) {
      SeqCount sc = entry.getValue();
      N += sc.n1 + sc.n2;
    }

    System.out.println(title);
    for (Map.Entry<Integer, SeqCount> entry : map.entrySet()) {
      SeqCount sc = entry.getValue();
      int n = sc.n1 + sc.n2;
      double p = (n == 0 ? 0.0 : 100.0 * (sc.n1) / n);
      // System.out.printf("%d: %.1f%%  %.3fx  %3d  [%d, %d]\n", entry.getKey(), p, sc.r1 / sc.r2, n, sc.n1, sc.n2);
      System.out.printf(
          "<tr><td>%d</td><td>%.1f%%</td><td>%.3fx</td><td>%.2f%%</td><td>%d</td><td>%d</td><td>%d</td></tr>\n",
          entry.getKey(), p, sc.r1 / sc.r2, 100.0 * n / N, n, sc.n1, sc.n2);
    }
  }
}
