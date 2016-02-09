package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.ml.RegressionModel;
import org.minnen.retiretool.ml.Example;

import smile.regression.RidgeRegression;

public class TestRegression
{
  public final static double tol = 1e-6;

  @Test
  public void testOLS_1D_Bias()
  {
    List<Example> examples = new ArrayList<>();
    examples.add(Example.forRegression(new FeatureVec(1, 1), 0));
    examples.add(Example.forRegression(new FeatureVec(1, 2), 1));
    examples.add(Example.forRegression(new FeatureVec(1, 3), 2));
    examples.add(Example.forRegression(new FeatureVec(1, 4), 3));
    RidgeRegression.Trainer trainer = new RidgeRegression.Trainer(0.0);
    RegressionModel model = RegressionModel.learn(examples, trainer);
    assertEquals(1.5, model.predict(2.5), tol);
  }
}
