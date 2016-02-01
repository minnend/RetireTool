package org.minnen.retiretool.stats;

import java.util.ArrayList;
import java.util.List;

public class BinaryPredictionStats
{
  private static class Pair
  {
    public double predicted, actual;

    public Pair(double predicted, double actual)
    {
      this.predicted = predicted;
      this.actual = actual;
    }
  }

  private final int[][]    counts   = new int[2][2];
  private final List<Pair> examples = new ArrayList<>();

  public void add(double predicted, double actual)
  {
    boolean bPredicted = predicted > 0.0;
    boolean bActual = actual > 0.0;
    int iPred = bPredicted ? 1 : 0;
    int iTrue = bActual ? 1 : 0;
    ++counts[iPred][iTrue];
    examples.add(new Pair(predicted, actual));
  }

  public int numPred(boolean pred)
  {
    int i = pred ? 1 : 0;
    return counts[i][0] + counts[i][1];
  }

  public int numActual(boolean actual)
  {
    int i = actual ? 1 : 0;
    return counts[0][i] + counts[1][i];
  }

  public int numCorrect()
  {
    return counts[0][0] + counts[1][1];
  }

  public int numWrong()
  {
    return counts[0][1] + counts[1][0];
  }

  public int size()
  {
    return numCorrect() + numWrong();
  }

  public double accuracy()
  {
    int n = size();
    if (n == 0) return 0.0;
    return 100.0 * numCorrect() / n;
  }

  public double accuracy(boolean pred)
  {
    int nPred = numPred(pred);
    if (nPred == 0) return 0.0;

    int i = pred ? 1 : 0;
    return 100.0 * counts[i][i] / nPred;
  }

  public double weightedPairedAccuracy()
  {
    final int N = examples.size();
    if (N < 2) return 0.0;

    int step = N > 5000 ? 2 : 1;
    double nCorrect = 0;
    double nPairs = 0;
    for (int i = 0; i < N; i += step) {
      Pair pi = examples.get(i);
      for (int j = i + 1; j < N; j += step) {
        Pair pj = examples.get(j);
        double diff = Math.abs(pi.predicted - pj.predicted);
        if (pi.predicted > pj.predicted) {
          if (pi.actual > pj.actual) {
            nCorrect += diff;
          }
        } else {
          if (pi.actual < pj.actual) {
            nCorrect += diff;
          }
        }
        nPairs += diff;
      }
    }

    // System.out.printf("Paired Accuracy: %.2f / %.2f\n", nCorrect, nPairs);
    return 100.0 * nCorrect / nPairs;
  }

  @Override
  public String toString()
  {
    return String.format("[Accuracy=%.2f @ %d]", accuracy(), size());
  }
}
