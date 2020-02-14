package org.minnen.retiretool.util;

import java.util.Arrays;

import com.joptimizer.functions.ConvexMultivariateRealFunction;
import com.joptimizer.functions.LinearMultivariateRealFunction;
import com.joptimizer.functions.PDQuadraticMultivariateRealFunction;
import com.joptimizer.optimizers.JOptimizer;
import com.joptimizer.optimizers.OptimizationRequest;

import smile.math.Math;

public class PortfolioOpt
{
  public static double[] minvar(double[][] corrMatrix)
  {
    return minvar(corrMatrix, 0.0, 1.0);
  }

  public static double[] minvar(double[][] corrMatrix, double minWeight, double maxWeight)
  {
    final int n = corrMatrix.length;
    final boolean bConstrainedMaxWeight = (maxWeight > 0.0 && maxWeight < 1.0);

    // Initial guess is equal weights.
    double[] guess = new double[n];
    Arrays.fill(guess, 1.0 / n);

    // Enforce sum(weights)=1.0 via Ax=b (where x == weights).
    double[][] A = new double[1][n];
    Arrays.fill(A[0], 1.0);
    double[] b = new double[] { 1.0 };

    // Objective function.
    double[][] P = corrMatrix;
    PDQuadraticMultivariateRealFunction objective = new PDQuadraticMultivariateRealFunction(P, null, 0);

    // We want to be long-only so weights must be constrained to be >= 0.0.
    final int nInequalities = (bConstrainedMaxWeight ? 2 * n : n);
    ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[nInequalities];
    for (int i = 0; i < n; ++i) {
      double[] a = new double[n];
      a[i] = -1.0;
      inequalities[i] = new LinearMultivariateRealFunction(a, minWeight);

      if (bConstrainedMaxWeight) {
        a = new double[n];
        a[i] = 1.0;
        inequalities[i + n] = new LinearMultivariateRealFunction(a, -maxWeight - 1e-11);
      }
    }

    // Setup optimization problem.
    OptimizationRequest or = new OptimizationRequest();
    or.setF0(objective);
    or.setA(A);
    or.setB(b);
    or.setFi(inequalities);
    or.setToleranceFeas(1.0e-6);
    or.setTolerance(1.0e-6);
    or.setInitialPoint(guess);

    // Find the solution.
    JOptimizer opt = new JOptimizer();
    opt.setOptimizationRequest(or);
    try {
      opt.optimize();
      double[] w = opt.getOptimizationResponse().getSolution();
      assert Math.abs(Library.sum(w) - 1.0) < 1e-6;
      return w;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }
}
