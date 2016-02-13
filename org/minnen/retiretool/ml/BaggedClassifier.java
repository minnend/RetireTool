package org.minnen.retiretool.ml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.Random;

import smile.classification.Classifier;
import smile.classification.ClassifierTrainer;
import smile.classification.SoftClassifier;
import smile.math.Math;

public class BaggedClassifier implements SoftClassifier<double[]>
{
  private final List<SoftClassifier<double[]>> weakClassifiers = new ArrayList<>();

  @Override
  public int predict(double[] x)
  {
    return predict(x, new double[2]);
  }

  @Override
  public int predict(double[] x, double[] posteriori)
  {
    assert weakClassifiers.size() > 0;
    assert posteriori.length == 2;
    Arrays.fill(posteriori, 0.0);
    double[] pos = new double[2];
    for (SoftClassifier<double[]> weak : weakClassifiers) {
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

  public static class Trainer extends ClassifierTrainer<double[]>
  {
    private int                         nWeak = 10;
    private ClassifierTrainer<double[]> trainer;

    public Trainer(int nWeak, ClassifierTrainer<double[]> trainer)
    {
      this.nWeak = nWeak;
      this.trainer = trainer;
    }

    @Override
    public Classifier<double[]> train(double[][] x, int[] y)
    {
      BaggedClassifier bag = new BaggedClassifier();
      for (int iWeak = 0; iWeak < nWeak; ++iWeak) {
        // TODO need to take a bootstrap sample.
        bag.weakClassifiers.add((SoftClassifier<double[]>) trainer.train(x, y));
      }
      return bag;
    }
  }
}
