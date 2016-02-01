package org.minnen.retiretool.ml;

import java.util.List;

import org.minnen.retiretool.data.FeatureVec;

import smile.regression.Regression;
import smile.regression.RegressionTrainer;
import smile.regression.RidgeRegression;

public class LinearRegression extends RegressionFV
{
  public final double     intercept;
  public final FeatureVec weights;

  public LinearRegression(FeatureVec weights, double intercept)
  {
    this.weights = weights;
    this.intercept = intercept;
  }

  @Override
  public double predict(double x)
  {
    assert weights.getNumDims() == 1;
    return intercept + weights.get(0) * x;
  }

  @Override
  public double predict(FeatureVec x)
  {
    return intercept + weights.dot(x);
  }

  /**
   * Learn model parameters using ridge regression.
   */
  public static LinearRegression learn(List<RegressionExample> examples, RegressionTrainer<double[]> trainer,
      double lambda)
  {
    // Setup the data for learning.
    final int N = examples.size();
    final int D = examples.get(0).x.getNumDims();

    double[][] x = new double[N][D];
    double[] y = new double[N];
    for (int i = 0; i < N; ++i) {
      RegressionExample example = examples.get(i);
      System.arraycopy(example.x.get(), 0, x[i], 0, D);
      y[i] = example.y;
    }

    // Learn the model parameters.
    RidgeRegression model = new RidgeRegression(x, y, lambda);
    // Regression<double[]> model = trainer.train(x, y);

    // Transfer learned parameters to internal representation.
    FeatureVec weights = new FeatureVec(model.coefficients());
    return new LinearRegression(weights, model.intercept());
  }

  /**
   * Learn model parameters using ridge regression.
   */
  public static LinearRegression learnRidge(List<RegressionExample> examples, double lambda)
  {
    // Setup the data for learning.
    final int N = examples.size();
    final int D = examples.get(0).x.getNumDims();

    double[][] x = new double[N][D];
    double[] y = new double[N];
    for (int i = 0; i < N; ++i) {
      RegressionExample example = examples.get(i);
      System.arraycopy(example.x.get(), 0, x[i], 0, D);
      y[i] = example.y;
    }

    // Learn the model parameters.
    RidgeRegression model = new RidgeRegression(x, y, lambda);

    // Transfer learned parameters to internal representation.
    FeatureVec weights = new FeatureVec(model.coefficients());
    return new LinearRegression(weights, model.intercept());
  }
}
