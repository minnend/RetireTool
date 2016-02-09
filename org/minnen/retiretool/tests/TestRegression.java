package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.minnen.retiretool.ml.RegressionModel;
import org.minnen.retiretool.ml.RegressionExample;

import smile.regression.RidgeRegression;

public class TestRegression
{
  public final static double tol = 1e-6;

  @Test
  public void testOLS_1D_Bias()
  {
    List<RegressionExample> examples = new ArrayList<>();
    examples.add(new RegressionExample(1, 0));
    examples.add(new RegressionExample(2, 1));
    examples.add(new RegressionExample(3, 2));
    examples.add(new RegressionExample(4, 3));
    RidgeRegression.Trainer trainer = new RidgeRegression.Trainer(0.0);
    RegressionModel model = RegressionModel.learn(examples, trainer);
    assertEquals(1.5, model.predict(2.5), tol);
  }
}
