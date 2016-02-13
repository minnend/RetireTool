package org.minnen.retiretool.ml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.Random;

import smile.classification.Classifier;
import smile.classification.ClassifierTrainer;
import smile.classification.SoftClassifier;

/** Combination of stumps that predicts True only if all stumps predict True. */
public class PositiveQuadrant implements SoftClassifier<double[]>
{
  private final List<PositiveStump> stumps = new ArrayList<>();

  @Override
  public int predict(double[] x)
  {
    for (PositiveStump stump : stumps) {
      if (stump.predict(x) == 0) return 0;
    }
    return 1;
  }

  @Override
  public int predict(double[] x, double[] posteriori)
  {
    assert posteriori.length == 2;
    double minPos = 1.0;
    for (PositiveStump stump : stumps) {
      stump.predict(x, posteriori);
      if (posteriori[1] < minPos) {
        minPos = posteriori[1];
      }
    }
    posteriori[0] = 1.0 - minPos;
    posteriori[1] = minPos;
    return minPos > 0.5 ? 1 : 0;
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("[PosQuad:");
    for (PositiveStump stump : stumps) {
      sb.append(String.format(" [%d,%.2f]", stump.iDim, stump.threshold));
    }
    sb.append("]");
    return sb.toString();
  }

  public static class Trainer extends ClassifierTrainer<double[]>
  {
    private int nStumps      = -1;
    private int nRandomTries = 10;

    public Trainer(int nStumps, int nRandomTries)
    {
      this.nStumps = nStumps;
      this.nRandomTries = nRandomTries;
    }

    @Override
    public Classifier<double[]> train(double[][] x, int[] y)
    {
      final int D = x[0].length;
      int nStumps = this.nStumps;
      if (nStumps < 0) {
        nStumps = D;
      }
      assert nStumps > 0 && nStumps <= D;

      if (nRandomTries < 0) { // Greedy selection
        boolean[] used = new boolean[D];
        double threshold = 0.0;
        PositiveQuadrant posQuad = new PositiveQuadrant();
        while (posQuad.stumps.size() < nStumps) {
          PositiveStump bestStump = null;
          double bestAccuracy = -1.0;

          for (int d = 0; d < D; ++d) {
            if (used[d]) continue;
            PositiveStump stump = new PositiveStump(d, threshold, 1.0);
            posQuad.stumps.add(stump);
            ClassificationModel model = new ClassificationModel(posQuad);
            double accuracy = model.accuracy(x, y);
            // System.out.printf("%s: %.2f\n", posQuad, accuracy);
            if (bestStump == null || accuracy > bestAccuracy) {
              bestStump = stump;
              bestAccuracy = accuracy;
            }
            posQuad.stumps.remove(posQuad.stumps.size() - 1);
          }

          used[bestStump.iDim] = true;
          posQuad.stumps.add(bestStump);
        }
        // System.out.println(posQuad);
        return posQuad;
      } else {
        // Random rng = new Random();
        ClassificationModel best = null;
        double bestAccuracy = -1.0;
        int[] ii = new int[nStumps];// new int[] { 3, 7, 10 };// Library.genIdentityArray(nStumps);
        for (int iTry = 0; iTry < nRandomTries; ++iTry) {
          if (nStumps < D) {
            int[] a = Library.shuffle(D);
            System.arraycopy(a, 0, ii, 0, nStumps);
            Arrays.sort(ii);
          }
          assert ii.length == nStumps;

          PositiveQuadrant posQuad = new PositiveQuadrant();
          for (int i = 0; i < nStumps; ++i) {
            // System.out.printf("Try=%d  Stump %d: %d (%d)\n", iTry, i, ii[i], D);
            double threshold = 0.1;// rng.nextDouble() * 0.1;
            PositiveStump stump = new PositiveStump(ii[i], threshold, 1.0);
            posQuad.stumps.add(stump);
          }
          assert posQuad.stumps.size() == nStumps;
          ClassificationModel model = new ClassificationModel(posQuad);
          double accuracy = model.accuracy(x, y);
          if (best == null || accuracy > bestAccuracy) {
            best = model;
            bestAccuracy = accuracy;
          }
        }
        System.out.println(best.model);
        return (PositiveQuadrant) best.model;
      }
    }
  }
}
