package org.minnen.retiretool.stats;

public class BinaryPredictionStats
{
  private final int[][] counts = new int[2][2];

  public void add(boolean predicted, boolean actual)
  {
    int iPred = predicted ? 1 : 0;
    int iTrue = actual ? 1 : 0;
    ++counts[iPred][iTrue];
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

  @Override
  public String toString()
  {
    return String.format("[Accuracy=%.2f @ %d]", accuracy(), size());
  }
}
