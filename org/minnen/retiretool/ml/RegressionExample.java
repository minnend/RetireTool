package org.minnen.retiretool.ml;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.data.FeatureVec;

/**
 * A single example for a regression problem: y = f(x).
 */
public class RegressionExample
{
  public final FeatureVec x;
  public final double     y;

  public RegressionExample(double x, double y)
  {
    this.x = new FeatureVec(1, x);
    this.y = y;
  }

  public RegressionExample(FeatureVec x, double y)
  {
    this.x = x;
    this.y = y;
  }

  @Override
  public String toString()
  {
    return String.format("%s -> %.3f", x, y);
  }

  public static FeatureVec mean(List<RegressionExample> examples)
  {
    final int N = examples.size();
    final int D = examples.get(0).x.getNumDims();
    FeatureVec sum = new FeatureVec(D + 1);
    double[] v = sum.get();
    for (RegressionExample example : examples) {
      for (int i = 0; i < D; ++i) {
        v[i] += example.x.get(i);
      }
      v[D] += example.y;
    }
    return sum._div(N);
  }

  public static void save(List<RegressionExample> examples, File file) throws IOException
  {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      for (RegressionExample example : examples) {
        writer.write(String.format("%f", example.y));
        for (int i = 0; i < example.x.getNumDims(); ++i) {
          writer.write(String.format(" %f", example.x.get(i)));
        }
        writer.write("\n");
      }
    }
  }

  public static void getFold(int iFold, int kFolds, List<RegressionExample> train, List<RegressionExample> test,
      List<RegressionExample> examples)
  {
    int n = examples.size();
    int iFirst = n * iFold / kFolds;
    int iLast = n * (iFold + 1) / kFolds - 1;
    System.out.printf("CV[%d/%d] = %d -> %d\n", iFold, kFolds, iFirst, iLast);
    for (int i = 0; i < n; ++i) {
      if (i >= iFirst && i <= iLast) {
        test.add(examples.get(i));
      } else {
        train.add(examples.get(i));
      }
    }
  }
}
