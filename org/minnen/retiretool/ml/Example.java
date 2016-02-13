package org.minnen.retiretool.ml;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.minnen.retiretool.data.FeatureVec;

/**
 * A single example for a regression problem: y = f(x).
 */
public class Example
{
  /** Features used to make a prediction. */
  public final FeatureVec x;

  /** Real-valued target value for regression. */
  public final double     y;

  /** Integral target for classification */
  public final int        k;

  private Example(FeatureVec x, double y)
  {
    this(x, y, Integer.MIN_VALUE);
  }

  private Example(FeatureVec x, int k)
  {
    this(x, Double.NaN, k);
  }

  public Example(FeatureVec x, double y, int k)
  {
    this.x = x;
    this.y = y;
    this.k = k;
  }

  public long getTime()
  {
    return x.getTime();
  }

  public Example setTime(long time)
  {
    x.setTime(time);
    return this;
  }

  public double getWeight()
  {
    return x.getWeight();
  }

  public static Example forRegression(FeatureVec x, double y)
  {
    return new Example(x, y);
  }

  public static Example forClassification(FeatureVec x, int k)
  {
    return new Example(x, k);
  }

  public static Example forBoth(FeatureVec x, double y, int k)
  {
    return new Example(x, y, k);
  }

  public boolean supportsClassification()
  {
    return k > Integer.MIN_VALUE;
  }

  public boolean supportsRegression()
  {
    return !Double.isNaN(y);
  }

  @Override
  public String toString()
  {
    if (supportsClassification() && supportsRegression()) {
      return String.format("%s -> [%d, %f]", x, k, y);
    } else if (supportsClassification()) {
      return String.format("%s -> %d", x, k);
    } else {
      assert supportsRegression();
      return String.format("%s -> %f", x, y);
    }
  }

  public static FeatureVec mean(List<Example> examples)
  {
    final int N = examples.size();
    final int D = examples.get(0).x.getNumDims();
    FeatureVec sum = new FeatureVec(D + 1);
    double[] v = sum.get();
    for (Example example : examples) {
      for (int i = 0; i < D; ++i) {
        v[i] += example.x.get(i);
      }
      v[D] += example.y;
    }
    return sum._div(N);
  }

  /** @return array of feature vectors from the examples. */
  public static FeatureVec[] getFeatureArray(List<Example> examples)
  {
    final int N = examples.size();
    FeatureVec[] features = new FeatureVec[N];
    for (int i = 0; i < N; ++i) {
      features[i] = examples.get(i).x;
    }
    return features;
  }

  /** @return array of real-valued regressor values from the examples. */
  public static int[] getClassArray(List<Example> examples)
  {
    final int N = examples.size();
    int[] y = new int[N];
    for (int i = 0; i < N; ++i) {
      y[i] = examples.get(i).k;
    }
    return y;
  }

  /** @return array of real-valued regressor values from the examples. */
  public static double[] getRegressorArray(List<Example> examples)
  {
    final int N = examples.size();
    double[] y = new double[N];
    for (int i = 0; i < N; ++i) {
      y[i] = examples.get(i).y;
    }
    return y;
  }

  public static void saveRegression(List<Example> examples, File file) throws IOException
  {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      for (Example example : examples) {
        writer.write(String.format("%f", example.y));
        for (int i = 0; i < example.x.getNumDims(); ++i) {
          writer.write(String.format(" %f", example.x.get(i)));
        }
        writer.write("\n");
      }
    }
  }

  public static void saveClassification(List<Example> examples, File file) throws IOException
  {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      for (Example example : examples) {
        writer.write(String.format("%d", example.k));
        for (int i = 0; i < example.x.getNumDims(); ++i) {
          writer.write(String.format(" %f", example.x.get(i)));
        }
        writer.write("\n");
      }
    }
  }

  public static void getFold(int iFold, int kFolds, List<Example> train, List<Example> test, List<Example> examples)
  {
    int n = examples.size();
    int iFirst = n * iFold / kFolds;
    int iLast = n * (iFold + 1) / kFolds - 1;
    System.out.printf("CV[%d/%d] = %d -> %d\n", iFold + 1, kFolds, iFirst, iLast);
    for (int i = 0; i < n; ++i) {
      if (i >= iFirst && i <= iLast) {
        test.add(examples.get(i));
      } else {
        train.add(examples.get(i));
      }
    }
  }
}
