package org.minnen.retiretool.predictor.daily;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.DiscreteDistribution;
import org.minnen.retiretool.util.Library;
import org.minnen.retiretool.util.TimeLib;

/** Abstract parent class for all broker-based predictors. */
public abstract class Predictor
{
  /**
   * Type of Predictor.
   *
   * <ul>
   * <li>InOut = One asset with prediction of holding (in) or avoiding (out).
   * <li>SelectOne = Choose one asset and go all-in.
   * <li>Distribution = Generate a distribution over all assets.
   * </ul>
   */
  public enum PredictorType {
    InOut, SelectOne, Distribution
  }

  /** Name of this predictor. */
  public final String           name;

  /** Names of assets available to this predictor. */
  public final String[]         assetChoices;

  /** Access object for getting information from a broker. */
  public final BrokerInfoAccess brokerAccess;

  /** Sub-predictors that are aggregated by this predictor (e.g. to support ensembles). */
  public Predictor[]            predictors;

  /** Timestamp for last feedback; used to detect out-of-order feedback. */
  protected long                lastFeedbackMS = TimeLib.TIME_BEGIN;

  /** Type of predictor. */
  protected PredictorType       predictorType;

  /** Reusable distribution array to reduce object creation. */
  private DiscreteDistribution  distribution;

  public Predictor(String name, BrokerInfoAccess brokerAccess, String... assetChoices)
  {
    this.name = name;
    this.assetChoices = assetChoices;
    this.brokerAccess = brokerAccess;
  }

  public final String selectAsset()
  {
    if (predictorType == PredictorType.InOut) {
      boolean bIn = calcInOut();
      return assetChoices[bIn ? 0 : 1];
    } else if (predictorType == PredictorType.SelectOne) {
      int index = calcSelectOne();
      return assetChoices[index];
    } else {
      assert predictorType == PredictorType.Distribution : predictorType;
      DiscreteDistribution distribution = selectDistribution();
      int index = Library.argmax(distribution.weights);
      assert Math.abs(distribution.weights[index] - 1.0) < 1e-6;
      return assetChoices[index];
    }
  }

  public final DiscreteDistribution selectDistribution()
  {
    // Create / reset distribution.
    if (distribution == null || distribution.size() != assetChoices.length) {
      distribution = new DiscreteDistribution(assetChoices);
    } else {
      distribution.clear();
    }

    if (predictorType == PredictorType.InOut) {
      boolean bIn = calcInOut();
      distribution.weights[bIn ? 0 : 1] = 1.0;
    } else if (predictorType == PredictorType.SelectOne) {
      int index = calcSelectOne();
      distribution.weights[index] = 1.0;
    } else {
      assert predictorType == PredictorType.Distribution;
      calcDistribution(distribution);
    }

    assert distribution.isNormalized();
    return distribution;
  }

  protected boolean calcInOut()
  {
    throw new RuntimeException("Predictors that decide in/out status should override calcInOut().");
  }

  protected int calcSelectOne()
  {
    throw new RuntimeException("Predictors that select a single asset should override calcSelectOne().");
  }

  protected void calcDistribution(DiscreteDistribution distribution)
  {
    throw new RuntimeException("Predictors that generate a distribution should override calcDistribution().");
  }

  public void feedback(long timeMS, int iCorrect, double observedReturn)
  {
    // Default behavior is to ignore feedback but protect against rewinds.
    assert timeMS > lastFeedbackMS;
    lastFeedbackMS = timeMS;
  }

  public void reset()
  {
    lastFeedbackMS = TimeLib.TIME_BEGIN;
    if (predictors != null) {
      for (Predictor predictor : predictors) {
        predictor.reset();
      }
    }
  }
}
