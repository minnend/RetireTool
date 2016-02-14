package org.minnen.retiretool.ml;

import java.util.List;

import org.minnen.retiretool.data.FeatureVec;

import smile.classification.Classifier;
import smile.classification.ClassifierTrainer;
import smile.classification.RandomForest;
import smile.classification.SoftClassifier;

public class ClassificationModel implements SoftClassifier<FeatureVec>
{
  public final Classifier<double[]>   model;
  public final Classifier<FeatureVec> modelFV;

  public ClassificationModel(Classifier<double[]> model, Classifier<FeatureVec> modelFV)
  {
    this.model = model;
    this.modelFV = modelFV;
  }

  public int predict(double x)
  {
    if (model != null) {
      return model.predict(new double[] { x });
    } else {
      return modelFV.predict(new FeatureVec(1, x));
    }
  }

  @Override
  public int predict(FeatureVec x)
  {
    if (model != null) {
      return model.predict(x.get());
    } else {
      return modelFV.predict(x);
    }

  }

  @Override
  public int predict(FeatureVec x, double[] probs)
  {
    if (model != null) {
      SoftClassifier<double[]> softModel = (SoftClassifier<double[]>) model;
      return softModel.predict(x.get(), probs);
    } else {
      SoftClassifier<FeatureVec> softModel = (SoftClassifier<FeatureVec>) modelFV;
      return softModel.predict(x, probs);
    }
  }

  public double accuracy(List<Example> examples, boolean useWeights)
  {
    FeatureVec[] x = Example.getFeatureArray(examples);
    int[] y = Example.getClassArray(examples);
    return accuracy(x, y, useWeights);
  }

  public double accuracy(FeatureVec[] x, int[] y, boolean useWeights)
  {
    assert x.length == y.length;
    int nc = 0;
    double wc = 0.0;
    double ww = 0.0;
    for (int i = 0; i < x.length; ++i) {
      int k = predict(x[i]);
      if (k == y[i]) {
        wc += x[i].getWeight();
        ++nc;
      } else {
        ww += x[i].getWeight();
      }
    }
    if (useWeights) {
      return 100.0 * wc / (wc + ww);
    } else {
      return 100.0 * nc / x.length;
    }
  }

  public double accuracy(double[][] x, int[] y)
  {
    assert x.length == y.length;
    int nc = 0;
    for (int i = 0; i < x.length; ++i) {
      int k = model.predict(x[i]);
      if (k == y[i]) {
        ++nc;
      }
    }
    return 100.0 * nc / x.length;
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
    return new ClassificationModel(model, null);
  }

  /**
   * Learn model parameters using the given trainer
   */
  public static ClassificationModel learnFV(List<Example> examples, ClassifierTrainer<FeatureVec> trainer)
  {
    // Setup the data for learning.
    FeatureVec[] featuresArray = Example.getFeatureArray(examples);
    int[] y = Example.getClassArray(examples);

    // Learn the model parameters.
    Classifier<FeatureVec> model = trainer.train(featuresArray, y);
    return new ClassificationModel(null, model);
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

  public static ClassificationModel learnStump(List<Example> examples, int nTryDims, boolean useWeights)
  {
    Stump.Trainer trainer = new Stump.Trainer(nTryDims, useWeights);
    return learnFV(examples, trainer);
  }

  public static ClassificationModel learnQuadrant(List<Example> examples, int nStumps, int nRandomTries,
      boolean useWeights)
  {
    PositiveQuadrant.Trainer trainer = new PositiveQuadrant.Trainer(nStumps, nRandomTries, useWeights);
    return learnFV(examples, trainer);
  }

  public static ClassificationModel learnBaggedQuadrant(List<Example> examples, int nWeak, int nStumps,
      int nRandomTries, boolean useWeights)
  {
    PositiveQuadrant.Trainer weakTrainer = new PositiveQuadrant.Trainer(nStumps, nRandomTries, useWeights);
    BaggedClassifier.Trainer bagTrainer = new BaggedClassifier.Trainer(nWeak, weakTrainer);
    return learnFV(examples, bagTrainer);
  }
}
