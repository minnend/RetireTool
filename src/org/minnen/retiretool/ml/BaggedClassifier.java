package org.minnen.retiretool.ml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.minnen.retiretool.data.FeatureVec;

import smile.classification.Classifier;
import smile.classification.ClassifierTrainer;
import smile.classification.SoftClassifier;
import smile.math.Math;

public class BaggedClassifier implements SoftClassifier<FeatureVec>
{
  private final List<SoftClassifier<FeatureVec>> weakClassifiers = new ArrayList<>();

  @Override
  public int predict(FeatureVec x)
  {
    return predict(x, new double[2]);
  }

  @Override
  public int predict(FeatureVec x, double[] posteriori)
  {
    assert weakClassifiers.size() > 0;
    assert posteriori.length == 2;
    Arrays.fill(posteriori, 0.0);
    double[] pos = new double[2];
    for (SoftClassifier<FeatureVec> weak : weakClassifiers) {
      weak.predict(x, pos);
      for (int i = 0; i < posteriori.length; ++i) {
        posteriori[i] += pos[i];
      }
    }
    for (int i = 0; i < posteriori.length; ++i) {
      posteriori[i] /= weakClassifiers.size();
    }
    return Math.whichMax(posteriori);
  }

  public static class Trainer extends ClassifierTrainer<FeatureVec>
  {
    private int                           nWeak = 10;
    private ClassifierTrainer<FeatureVec> trainer;

    public Trainer(int nWeak, ClassifierTrainer<FeatureVec> trainer)
    {
      this.nWeak = nWeak;
      this.trainer = trainer;
    }

    @Override
    public Classifier<FeatureVec> train(FeatureVec[] x, int[] y)
    {
      BaggedClassifier bag = new BaggedClassifier();
      for (int iWeak = 0; iWeak < nWeak; ++iWeak) {
        // TODO need to take a bootstrap sample.
        bag.weakClassifiers.add((SoftClassifier<FeatureVec>) trainer.train(x, y));
      }
      return bag;
    }
  }
}
