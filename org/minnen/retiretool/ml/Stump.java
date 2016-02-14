package org.minnen.retiretool.ml;

import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.util.Library;

import smile.classification.Classifier;
import smile.classification.ClassifierTrainer;
import smile.classification.SoftClassifier;

/** Binary Classifier: k = (x[i] > threshold). */
public class Stump implements SoftClassifier<FeatureVec>
{
  public final int     iDim;
  public final double  threshold;
  public final boolean invert;
  public final double  sigma;

  public Stump(int iDim, double threshold)
  {
    this(iDim, threshold, false);
  }

  public Stump(int iDim, double threshold, boolean invert)
  {
    this(iDim, threshold, invert, 1.0);
  }

  public Stump(int iDim, double threshold, boolean invert, double sigma)
  {
    this.iDim = iDim;
    this.threshold = threshold;
    this.invert = invert;
    this.sigma = sigma;
  }

  @Override
  public int predict(FeatureVec x)
  {
    double v = x.get(iDim);
    if (invert) {
      return v < threshold ? 1 : 0;
    } else {
      return v > threshold ? 1 : 0;
    }
  }

  @Override
  public int predict(FeatureVec x, double[] posteriori)
  {
    double v = x.get(iDim);
    if (invert) {
      posteriori[1] = Library.sigmoid(-v, sigma, -threshold);
    } else {
      posteriori[1] = Library.sigmoid(v, sigma, threshold);
    }
    posteriori[0] = 1.0 - posteriori[1];
    // System.out.printf("%f -> %d [%.1f, %.1f]\n", x[iDim], k, 100.0 * posteriori[0], 100.0 * posteriori[1]);
    return predict(x);
  }

  @Override
  public String toString()
  {
    return String.format("[Stump: %d, %.2f%s]", iDim, threshold, invert ? ", inv" : "");
  }

  public static class Trainer extends ClassifierTrainer<FeatureVec>
  {
    private int     nTryDims = -1;
    private boolean useWeights;

    public Trainer(int nTryDims, boolean useWeights)
    {
      this.nTryDims = nTryDims;
      this.useWeights = useWeights;
    }

    @Override
    public Classifier<FeatureVec> train(FeatureVec[] x, int[] y)
    {
      final int D = x[0].getNumDims();
      int nTryDims = this.nTryDims;
      assert nTryDims <= D;
      if (nTryDims <= 0) {
        nTryDims = D;
      }

      int[] ii = null;
      if (nTryDims == D) {
        ii = Library.genIdentityArray(nTryDims);
      } else {
        int[] a = Library.shuffle(D);
        ii = new int[nTryDims];
        System.arraycopy(a, 0, ii, 0, nTryDims);
      }

      ClassificationModel best = null;
      double bestAccuracy = -1.0;
      for (int i = 0; i < nTryDims; ++i) {
        int d = ii[i];
        double threshold = 0.0;
        Stump stump = new Stump(d, threshold);
        ClassificationModel model = new ClassificationModel(null, stump);
        double accuracy = model.accuracy(x, y, useWeights);
        if (best == null || accuracy > bestAccuracy) {
          best = model;
          bestAccuracy = accuracy;
        }
      }
      // System.out.println(best.model);
      return (Stump) best.modelFV;
    }
  }
}