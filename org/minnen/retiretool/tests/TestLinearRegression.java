package org.minnen.retiretool.tests;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.minnen.retiretool.ml.LinearRegression;
import org.minnen.retiretool.ml.RegressionExample;

public class TestLinearRegression
{
  public final static double tol = 1e-6;

  @Test
  public void testOLS_1D_NoBias()
  {
    List<RegressionExample> examples = new ArrayList<>();
    examples.add(new RegressionExample(1, 1));
    examples.add(new RegressionExample(2, 2));
    examples.add(new RegressionExample(3, 3));
    examples.add(new RegressionExample(4, 4));
    LinearRegression model = LinearRegression.learnRidge(examples, 0.0);
    assertArrayEquals(new double[] { 1.0 }, model.weights.get(), tol);
    assertEquals(0.0, model.intercept, tol);
    assertEquals(2.5, model.predict(2.5), tol);
  }

  @Test
  public void testOLS_1D_Bias()
  {
    List<RegressionExample> examples = new ArrayList<>();
    examples.add(new RegressionExample(1, 0));
    examples.add(new RegressionExample(2, 1));
    examples.add(new RegressionExample(3, 2));
    examples.add(new RegressionExample(4, 3));
    LinearRegression model = LinearRegression.learnRidge(examples, 0.0);
    assertArrayEquals(new double[] { 1.0 }, model.weights.get(), tol);
    assertEquals(-1.0, model.intercept, tol);
    assertEquals(1.5, model.predict(2.5), tol);
  }
}
