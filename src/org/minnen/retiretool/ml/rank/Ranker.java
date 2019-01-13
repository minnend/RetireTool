package org.minnen.retiretool.ml.rank;

public abstract class Ranker
{
  /**
   * Return a ranked array of indices based on the win record.
   * 
   * @param wins wins[i][j]==1 iff team[i] beat team[j], -1 iff team[i] lost to team[j], and 0 if they tied.
   * @return array of indices where the best (higher rank) values comes first.
   */
  public abstract int[] rank(int[][] wins);

  public double[] getScores()
  {
    throw new RuntimeException("Rankers that generate scores should override getScores().");
  }
}
