package org.minnen.retiretool.tests;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.minnen.retiretool.ml.rank.ColleyRanker;

public class TestRankers
{
  @Test
  public void testColleyRanker()
  {
    ColleyRanker ranker = new ColleyRanker();
    int[][] wins = new int[][] { { 0, -1, 1, 1, 1 }, { 1, 0, -1, 1, 1 }, { -1, 1, 0, -1, 1 }, { -1, -1, 1, 0, 1 },
        { -1, -1, -1, -1, 0 } };
    int[] rank = ranker.rank(wins);
    assertEquals(5, rank.length);
    assert (rank[0] == 0 || rank[0] == 1);
    assert (rank[1] == 0 || rank[1] == 1);
    assert (rank[2] == 2 || rank[2] == 3);
    assert (rank[3] == 2 || rank[3] == 3);
    assert (rank[4] == 4);

    double[] scores = ranker.getScores();
    final double[] expectedScores = new double[] { 0.6429, 0.6429, 0.5, 0.5, 0.2143 };
    assertArrayEquals(expectedScores, scores, 1e-4);
  }
}
