package org.minnen.retiretool.ml;

import java.util.List;

import org.minnen.retiretool.data.FeatureVec;

import smile.regression.LASSO;
import smile.regression.RandomForest;
import smile.regression.Regression;
import smile.regression.RegressionTrainer;
import smile.regression.RegressionTree;
import smile.regression.RidgeRegression;

public class RegressionModel implements Regression<FeatureVec>
{
  public final Regression<double[]> model;

  public RegressionModel(Regression<double[]> model)
  {
    this.model = model;
  }

  public double predict(double x)
  {
    return model.predict(new double[] { x });
  }

  @Override
  public double predict(FeatureVec x)
  {
    return model.predict(x.get());
  }

  /**
   * Learn model parameters using the given trainer
   */
  public static RegressionModel learn(List<Example> examples, RegressionTrainer<double[]> trainer)
  {
    // Setup the data for learning.
    final int N = examples.size();
    final int D = examples.get(0).x.getNumDims();

    double[][] x = new double[N][D];
    double[] y = new double[N];
    for (int i = 0; i < N; ++i) {
      Example example = examples.get(i);
      assert example.supportsRegression();
      System.arraycopy(example.x.get(), 0, x[i], 0, D);
      y[i] = example.y;
    }

    // Learn the model parameters.
    Regression<double[]> model = trainer.train(x, y);
    return new RegressionModel(model);
  }

  public static RegressionModel learnRidge(List<Example> examples, double lambda)
  {
    RidgeRegression.Trainer trainer = new RidgeRegression.Trainer(lambda);
    return learn(examples, trainer);
  }

  public static RegressionModel learnLasso(List<Example> examples, double lambda)
  {
    LASSO.Trainer trainer = new LASSO.Trainer(lambda);
    return learn(examples, trainer);
  }

  public static RegressionModel learnRF(List<Example> examples, int nTrees, int nRandFeatures, int nNodes)
  {
    if (nRandFeatures < 0) {
      int nFeatures = examples.get(0).x.getNumDims();
      nRandFeatures = (int) Math.sqrt(nFeatures);
      // System.out.printf("#features: %d -> %d\n", nFeatures, nRandFeatures);
      assert nRandFeatures > 0;
    }
    RandomForest.Trainer trainer = new RandomForest.Trainer(nTrees);
    trainer.setNumRandomFeatures(nRandFeatures);
    trainer.setNodeSize(8);
    trainer.setMaxNodes(nNodes);
    return learn(examples, trainer);
  }

  public static RegressionModel learnTree(List<Example> examples)
  {
    RegressionTree.Trainer trainer = new RegressionTree.Trainer(20).setNodeSize(5);
    return learn(examples, trainer);
  }
}
