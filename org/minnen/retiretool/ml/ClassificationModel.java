package org.minnen.retiretool.ml;

import java.util.List;

import org.minnen.retiretool.data.FeatureVec;

import smile.classification.Classifier;
import smile.classification.ClassifierTrainer;
import smile.classification.RandomForest;
import smile.classification.SoftClassifier;

public class ClassificationModel implements SoftClassifier<FeatureVec>
{
  public final Classifier<double[]> model;

  public ClassificationModel(Classifier<double[]> model)
  {
    this.model = model;
  }

  public int predict(double x)
  {
    return model.predict(new double[] { x });
  }

  @Override
  public int predict(FeatureVec x)
  {
    return model.predict(x.get());
  }

  @Override
  public int predict(FeatureVec x, double[] probs)
  {
    SoftClassifier<double[]> softModel = (SoftClassifier<double[]>) model;
    return softModel.predict(x.get(), probs);
  }

  /**
   * Learn model parameters using the given trainer
   */
  public static ClassificationModel learn(List<Example> examples, ClassifierTrainer<double[]> trainer)
  {
    // Setup the data for learning.
    final int N = examples.size();
    final int D = examples.get(0).x.getNumDims();

    double[][] x = new double[N][D];
    int[] y = new int[N];
    for (int i = 0; i < N; ++i) {
      Example example = examples.get(i);
      assert example.supportsClassification();
      System.arraycopy(example.x.get(), 0, x[i], 0, D);
      y[i] = example.k;
    }

    // Learn the model parameters.
    Classifier<double[]> model = trainer.train(x, y);
    return new ClassificationModel(model);
  }

  public static ClassificationModel learnRF(List<Example> examples, int nTrees, int nRandFeatures, int nNodes)
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
}
