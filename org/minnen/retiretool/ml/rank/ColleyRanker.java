package org.minnen.retiretool.ml.rank;

import org.minnen.retiretool.util.Library;
import org.ojalgo.matrix.BasicMatrix;
import org.ojalgo.matrix.PrimitiveMatrix;

public class ColleyRanker extends Ranker
{
  private double[] scores;

  @Override
  public int[] rank(int[][] wins)
  {
    final int N = wins.length;
    if (scores == null || scores.length != N) {
      scores = new double[N];
    }

    // Construct the Colley matrix.
    BasicMatrix.Builder<PrimitiveMatrix> builderA = PrimitiveMatrix.getBuilder(N, N);
    BasicMatrix.Builder<PrimitiveMatrix> builderB = PrimitiveMatrix.getBuilder(N);
    for (int i = 0; i < N; ++i) {
      int wi = 0;
      int li = 0;
      for (int j = 0; j < N; ++j) {
        if (wins[i][j] == 0) continue;
        builderA.set(i, j, -1);
        if (wins[i][j] > 0) {
          ++wi;
        } else {
          assert wins[i][j] < 0;
          ++li;
        }
      }
      builderA.set(i, i, wi + li + 2);
      builderB.set(i, 1.0 + 0.5 * (wi - li));
    }
    PrimitiveMatrix A = builderA.build();
    PrimitiveMatrix B = builderB.build();
    PrimitiveMatrix r = A.solve(B);

    // System.out.println(A);
    // System.out.println(B);
    // System.out.println(r);

    // Copy scores into score array.
    for (int i = 0; i < N; ++i) {
      scores[i] = r.get(i);
    }

    return Library.sort(scores.clone(), false);
  }

  @Override
  public double[] getScores()
  {
    return scores;
  }
}
