package org.minnen.retiretool;

public class WinStats
{
  public int   nPredCorrect, nPredWrong;
  public int[] nCorrect;
  public int[] nSelected;

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

  public double percentCorrect(int i)
  {
    int n = Library.sum(nCorrect);
    if (n == 0) {
      return 0.0;
    } else {
      return 100.0 * nCorrect[i] / n;
    }
  }

  public double percentSelected(int i)
  {
    int n = Library.sum(nSelected);
    if (n == 0) {
      return 0.0;
    } else {
      return 100.0 * nSelected[i] / n;
    }
  }
}