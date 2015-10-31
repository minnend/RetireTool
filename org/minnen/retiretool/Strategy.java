package org.minnen.retiretool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.predictor.AssetPredictor;
import org.minnen.retiretool.predictor.SMAPredictor;
import org.minnen.retiretool.predictor.Multi3Predictor.Disposition;
import org.minnen.retiretool.stats.WinStats;

public class Strategy
{
  public static Sequence calcReturns(AssetPredictor predictor, int iStart, Slippage slippage, WinStats winStats,
      Sequence... seqs)
  {
    assert seqs.length > 1;
    int N = seqs[0].length();
    for (int i = 0; i < seqs.length; ++i) {
      assert seqs[i].length() == N;
      assert seqs[i].getStartMS() == seqs[0].getStartMS();
      assert predictor.store.has(seqs[0].getName());
    }

    predictor.reset();
    final double principal = 1.0; // TODO principal might matter due to slippage or min purchase reqs
    double balance = principal;
    Sequence currentAsset = null;
    Sequence returns = new Sequence(predictor.name);
    returns.addData(balance, seqs[0].getTimeMS(iStart));
    if (winStats == null) {
      winStats = new WinStats();
    }
    winStats.nCorrect = new int[seqs.length];
    winStats.nSelected = new int[seqs.length];
    for (int i = iStart + 1; i < N; ++i) {
      assert seqs[0].getTimeMS(i) == seqs[1].getTimeMS(i);
      predictor.store.lock(seqs[0].getStartMS(), seqs[0].getTimeMS(i) - 1);
      int iSelected = predictor.selectAsset(seqs);
      predictor.store.unlock();

      if (slippage != null) {
        Sequence nextAsset = (iSelected >= 0 ? seqs[iSelected] : null);
        balance = slippage.adjustForAssetChange(balance, i - 1, currentAsset, nextAsset);
        currentAsset = nextAsset;
      }

      // Find correct answer (sequence with highest return for current month)
      double correctReturn = 1.0;
      int iCorrect = -1;
      for (int iSeq = 0; iSeq < seqs.length; ++iSeq) {
        Sequence seq = seqs[iSeq];
        double r = FinLib.getReturn(seq, i - 1, i);
        if (r > correctReturn) {
          correctReturn = r;
          iCorrect = iSeq;
        }
      }
      predictor.feedback(seqs[0].getTimeMS(i), iCorrect, correctReturn);

      // Invest everything in best asset for this month.
      // No bestSeq => hold everything in cash for no gain and no loss.
      double realizedReturn = 1.0;
      if (iSelected >= 0) {
        realizedReturn = FinLib.getReturn(seqs[iSelected], i - 1, i);
        balance *= realizedReturn;
      }
      returns.addData(balance, seqs[0].getTimeMS(i));

      // Update win stats
      if (realizedReturn > correctReturn - 1e-6) {
        ++winStats.nPredCorrect;
      } else {
        ++winStats.nPredWrong;
      }
      if (iCorrect >= 0) {
        ++winStats.nCorrect[iCorrect];
      }
      if (iSelected >= 0) {
        ++winStats.nSelected[iSelected];
      }
    }

    // Return cumulative returns normalized so that starting value is 1.0.
    returns._div(principal);
    assert Math.abs(returns.getFirst(0) - 1.0) < 1e-6;
    return returns;
  }

  public static Sequence calcReturnsUsingDistributions(AssetPredictor predictor, int iStart, Slippage slippage,
      WinStats winStats, Sequence... seqs)
  {
    assert seqs.length > 1;
    int N = seqs[0].length();
    for (int i = 0; i < seqs.length; ++i) {
      assert seqs[i].length() == N;
      assert seqs[i].getStartMS() == seqs[0].getStartMS();
      assert predictor.store.has(seqs[0].getName());
    }

    predictor.reset();
    final double principal = 1.0; // TODO principal might matter due to slippage or min purchase reqs
    double balance = principal;
    Sequence currentAsset = null;
    Sequence returns = new Sequence(predictor.name);
    returns.addData(balance, seqs[0].getTimeMS(iStart));
    if (winStats == null) {
      winStats = new WinStats();
    }
    winStats.nCorrect = new int[seqs.length];
    winStats.nSelected = new int[seqs.length];
    for (int i = iStart + 1; i < N; ++i) {
      assert seqs[0].getTimeMS(i) == seqs[1].getTimeMS(i);
      predictor.store.lock(seqs[0].getStartMS(), seqs[0].getTimeMS(i) - 1);
      int iSelected = predictor.selectAsset(seqs);
      predictor.store.unlock();

      if (slippage != null) {
        Sequence nextAsset = (iSelected >= 0 ? seqs[iSelected] : null);
        balance = slippage.adjustForAssetChange(balance, i - 1, currentAsset, nextAsset);
        currentAsset = nextAsset;
      }

      // Find correct answer (sequence with highest return for current month)
      double correctReturn = 1.0;
      int iCorrect = -1;
      for (int iSeq = 0; iSeq < seqs.length; ++iSeq) {
        Sequence seq = seqs[iSeq];
        double r = FinLib.getReturn(seq, i - 1, i);
        if (r > correctReturn) {
          correctReturn = r;
          iCorrect = iSeq;
        }
      }
      predictor.feedback(seqs[0].getTimeMS(i), iCorrect, correctReturn);

      // Invest everything in best asset for this month.
      // No bestSeq => hold everything in cash for no gain and no loss.
      double realizedReturn = 1.0;
      if (iSelected >= 0) {
        realizedReturn = FinLib.getReturn(seqs[iSelected], i - 1, i);
        balance *= realizedReturn;
      }
      returns.addData(balance, seqs[0].getTimeMS(i));

      // Update win stats
      if (realizedReturn > correctReturn - 1e-6) {
        ++winStats.nPredCorrect;
      } else {
        ++winStats.nPredWrong;
      }
      if (iCorrect >= 0) {
        ++winStats.nCorrect[iCorrect];
      }
      if (iSelected >= 0) {
        ++winStats.nSelected[iSelected];
      }
    }

    // Return cumulative returns normalized so that starting value is 1.0.
    returns._div(principal);
    assert Math.abs(returns.getFirst(0) - 1.0) < 1e-6;
    return returns;
  }

  /**
   * Invest 100% in asset with highest CAGR over last N months.
   * 
   * @param nMonths calculate CAGR over last N months.
   * @param iStart index of month to start investing.
   * @param slippage slippage model to use for each trade.
   * @param prediction stats will be saved here (optional; can be null)
   * @param seqs cumulative returns for each asset.
   * @return sequence of returns using the momentum strategy
   */
  public static Sequence calcMomentumReturns(int nMonths, int iStart, Slippage slippage, WinStats winStats,
      Sequence... seqs)
  {
    assert seqs.length > 1;
    int N = seqs[0].length();
    for (int i = 1; i < seqs.length; ++i) {
      assert seqs[i].length() == N;
      assert seqs[i].getStartMS() == seqs[0].getStartMS();
    }

    Sequence currentAsset = null;
    double balance = 1.0;
    Sequence momentum = new Sequence("Momentum-" + nMonths);
    momentum.addData(balance, seqs[0].getTimeMS(iStart));
    if (winStats == null) {
      winStats = new WinStats();
    }
    winStats.nCorrect = new int[seqs.length];
    winStats.nSelected = new int[seqs.length];
    for (int i = iStart + 1; i < N; ++i) {
      // Select asset with best return over previous 12 months.
      double bestReturn = 0.0, correctReturn = 1.0;
      int iSelected = -1, iCorrect = -1;
      for (int iSeq = 0; iSeq < seqs.length; ++iSeq) {
        Sequence seq = seqs[iSeq];
        seq.lock(0, i - 1); // lock sequence so only historical data is accessible
        int iLast = seq.length() - 1;
        double r = FinLib.getReturn(seq, iLast - nMonths, iLast);
        seq.unlock();
        if (r > bestReturn) {
          iSelected = iSeq;
          bestReturn = r;
        }

        // The correct return is the one actually realized for the current month.
        r = FinLib.getReturn(seq, i - 1, i);
        if (r > correctReturn) {
          correctReturn = r;
          iCorrect = iSeq;
        }
      }

      if (slippage != null) {
        Sequence nextAsset = (iSelected >= 0 ? seqs[iSelected] : null);
        balance = slippage.adjustForAssetChange(balance, i - 1, currentAsset, nextAsset);
        currentAsset = nextAsset;
      }

      // Invest everything in best asset for this month.
      // No bestSeq => hold everything in cash for no gain and no loss.
      double realizedReturn = iSelected < 0 ? 1.0 : FinLib.getReturn(seqs[iSelected], i - 1, i);
      balance *= realizedReturn;
      momentum.addData(balance, seqs[0].getTimeMS(i));

      if (realizedReturn > correctReturn - 1e-6) {
        ++winStats.nPredCorrect;
      } else {
        ++winStats.nPredWrong;
      }
      if (iCorrect >= 0) {
        ++winStats.nCorrect[iCorrect];
      }
      if (iSelected >= 0) {
        ++winStats.nSelected[iSelected];
      }
    }

    return momentum;
  }

  /**
   * Invest in risky asset when above SMA, otherwise safe asset.
   * 
   * @param numMonths calculate SMA over past N months
   * @param iStart index of month to start investing.
   * @param slippage slippage model to use for each trade.
   * @param prices monthly price used for SMA and signal
   * @param risky cumulative returns for risky asset
   * @param safe cumulative returns for safe asset
   * @return sequence of returns using the above/below-SMA strategy
   */
  public static Sequence calcSMAReturns(int numMonths, int iStart, Slippage slippage, Sequence prices, Sequence risky,
      Sequence safe)
  {
    // TODO update to use sequence locking for safety.
    assert risky.length() == safe.length();

    Sequence sma = new Sequence("SMA-" + numMonths);
    double balance = 1.0;
    Sequence currentAsset = null;
    sma.addData(balance, risky.getTimeMS(iStart));
    for (int i = iStart + 1; i < risky.length(); ++i) {
      // Calculate trailing moving average.
      int a = Math.max(0, i - numMonths - 1);
      double ma = prices.average(a, i - 1).get(0);

      // Test above / below moving average.
      double price = prices.get(i - 1, 0);

      // Select asset based on price relative to moving average.
      Sequence nextAsset = (price > ma ? risky : safe);
      if (slippage != null) {
        balance = slippage.adjustForAssetChange(balance, i - 1, currentAsset, nextAsset);
      }
      currentAsset = nextAsset;

      double lastMonthReturn = FinLib.getReturn(nextAsset, i - 1, i);
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

  public static Sequence calcMultiMomentumReturns(int iStart, Slippage slippage, Sequence risky, Sequence safe,
      Disposition disposition)
  {
    return calcMultiMomentumReturns(iStart, slippage, risky, safe, disposition, -1);
  }

  public static Sequence calcMultiMomentumReturns(int iStart, Slippage slippage, Sequence risky, Sequence safe,
      int assetMap)
  {
    assert assetMap >= 0;
    return calcMultiMomentumReturns(iStart, slippage, risky, safe, Disposition.Defensive, assetMap);
  }

  private static Sequence calcMultiMomentumReturns(int iStart, Slippage slippage, Sequence risky, Sequence safe,
      Disposition disposition, int assetMap)
  {
    int N = risky.length();
    assert safe.length() == N;

    int[] numMonths = new int[] { 1, 3, 12 };

    String name;
    if (assetMap >= 0) {
      name = "Mom." + assetMap;
    } else {
      name = "Mom." + disposition;
    }
    Sequence mom = new Sequence(name);
    Sequence currentAsset = null;
    double balance = 1.0;
    mom.addData(balance, risky.getTimeMS(iStart));
    for (int i = iStart + 1; i < N; ++i) {
      int code = calcMomentumCode(i - 1, numMonths, risky, safe);

      // Use votes to select asset.
      Sequence nextAsset = selectAsset(code, disposition, assetMap, risky, safe);
      if (slippage != null) {
        balance = slippage.adjustForAssetChange(balance, i - 1, currentAsset, nextAsset);
      }
      currentAsset = nextAsset;

      // Invest everything in best asset for this month.
      balance *= FinLib.getReturn(nextAsset, i - 1, i);
      mom.addData(balance, risky.getTimeMS(i));
    }

    return mom;
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
   * @return multi-sma code.
   */
  public static int calcSmaCode(int index, int[] numMonths, Sequence prices)
  {
    int code = 0;
    for (int i = 0; i < numMonths.length; ++i) {
      code <<= 1;
      final int a = Math.max(0, index - numMonths[i]);
      double sma = prices.average(a, index).get(0);
      double price = prices.get(index, 0);
      if (price > sma) {
        ++code;
      }
    }
    return code;
  }

  public static void calcMultiSmaStats(int iStart, Sequence prices, Sequence risky, Sequence safe)
  {
    int N = risky.length();
    assert safe.length() == N;

    int[] numMonths = new int[] { 1, 5, 10 };
    Map<Integer, SeqCount> map = new TreeMap<Integer, SeqCount>();

    for (int i = numMonths[numMonths.length - 1] + 1; i < N; ++i) {
      int code = calcSmaCode(i, numMonths, prices);

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
      smaPredictors[i] = new SMAPredictor(i + 1, prices.getName(), store);
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

  public static Sequence calcMultiSmaReturns(int iStart, Slippage slippage, Sequence prices, Sequence risky,
      Sequence safe, Disposition disposition)
  {
    return calcMultiSmaReturns(iStart, slippage, prices, risky, safe, disposition, -1);
  }

  public static Sequence calcMultiSmaReturns(int iStart, Slippage slippage, Sequence prices, Sequence risky,
      Sequence safe, int assetMap)
  {
    assert assetMap >= 0;
    return calcMultiSmaReturns(iStart, slippage, prices, risky, safe, Disposition.Defensive, assetMap);
  }

  public static Sequence calcMultiSmaReturns(int iStart, Slippage slippage, Sequence prices, Sequence risky,
      Sequence safe, Disposition disposition, int assetMap)
  {
    int N = risky.length();
    assert safe.length() == N;

    int[] numMonths = new int[] { 1, 5, 10 };

    String name;
    if (assetMap >= 0) {
      name = "SMA." + assetMap;
    } else {
      name = "SMA." + disposition;
    }
    Sequence sma = new Sequence(name);
    double balance = 1.0;
    sma.addData(balance, risky.getTimeMS(iStart));
    Sequence currentAsset = null;
    for (int i = iStart + 1; i < N; ++i) {
      int code = calcSmaCode(i - 1, numMonths, prices);

      // Use votes to select asset.
      Sequence nextAsset = selectAsset(code, disposition, assetMap, risky, safe);
      if (slippage != null) {
        balance = slippage.adjustForAssetChange(balance, i - 1, currentAsset, nextAsset);
      }
      currentAsset = nextAsset;

      // Invest everything in best asset for this month.
      balance *= FinLib.getReturn(nextAsset, i - 1, i);
      sma.addData(balance, risky.getTimeMS(i));
    }

    return sma;
  }

  private static Sequence selectAsset(int code, Disposition disposition, int assetMap, Sequence risky, Sequence safe)
  {
    assert code >= 0 && code <= 7;

    if (assetMap >= 0) {
      int x = (assetMap >> code) & 1;
      return (x == 1 ? risky : safe);
    } else {
      // Complete support or shortest + mid => always risky.
      if (code >= 6) {
        return risky;
      }

      // Shortest + support => only Defensive is safe.
      if (code == 5) {
        return disposition == Disposition.Defensive ? safe : risky;
      }

      // Not shortest and zero or one other => always safe.
      if (code <= 2) {
        return safe;
      }

      // Not shortest but both others.
      if (code == 3) {
        return disposition == Disposition.Defensive || disposition == Disposition.Cautious ? safe : risky;
      }

      // Only short-term support.
      assert code == 4;
      return disposition == Disposition.Aggressive ? risky : safe;
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
