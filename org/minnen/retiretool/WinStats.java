package org.minnen.retiretool;

public class WinStats
{
  public int nPredCorrect, nPredWrong;
  public int[] nCorrect;

  public int total()
  {
    return nPredCorrect + nPredWrong;
  }

  public double percentCorrect()
  {
    int n = total();
    if (n == 0) {
      return 0.0;
    } else {
      return 100.0 * nPredCorrect / n;
    }
  }
}