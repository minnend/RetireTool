package org.minnen.retiretool.predictor.config;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.predictor.daily.AdaptivePredictor;
import org.minnen.retiretool.predictor.daily.Predictor;
import org.minnen.retiretool.predictor.features.Momentum;

public class ConfigAdaptive extends PredictorConfig
{
  public static enum Weighting {
    MinVar, Equal
  };

  public static enum TradeFreq {
    Weekly, Monthly
  }

  public final int                     nCorrelation;
  public final double                  minWeight;
  public final double                  maxWeight;
  public final Weighting               weighting;
  public final int                     nTriggerA;
  public final int                     nTriggerB;
  public final int                     nBaseA;
  public final int                     nBaseB;
  public final double                  maxKeepFrac;
  public final int                     maxKeep;
  public final int                     pctQuantum;
  public final TradeFreq               tradeFreq;
  public final Momentum.ReturnOrMul    returnOrMul;
  public final Momentum.CompoundPeriod compoundPeriod;
  public final int                     iPrice;

  public ConfigAdaptive(int nCorrelation, double maxWeight, Weighting weighting, int nTrigger, int nBaseA, int nBaseB,
      double maxKeepFrac, int maxKeep, int pctQuantum, TradeFreq tradeFreq, int iPrice)
  {
    this.nCorrelation = nCorrelation;
    this.minWeight = 0.0;
    this.maxWeight = maxWeight;
    this.weighting = weighting;
    this.nTriggerA = nTrigger;
    this.nTriggerB = 1;
    this.nBaseA = nBaseA;
    this.nBaseB = nBaseB;
    this.maxKeepFrac = maxKeepFrac;
    this.maxKeep = maxKeep;
    this.pctQuantum = pctQuantum;
    this.tradeFreq = tradeFreq;
    this.iPrice = iPrice;

    // TODO make configurable
    returnOrMul = Momentum.ReturnOrMul.Return;
    compoundPeriod = Momentum.CompoundPeriod.Total;
  }

  @Override
  public boolean isValid()
  {
    return ((weighting != Weighting.MinVar || nCorrelation > 1) && nTriggerA >= nTriggerB && nBaseA > nBaseB && iPrice >= 0);
  }

  @Override
  public PredictorConfig genPerturbed()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Predictor build(BrokerInfoAccess brokerAccess, String... assetNames)
  {
    return new AdaptivePredictor(this, brokerAccess, assetNames);
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append(weighting == Weighting.MinVar ? "MinVar" : "EqualWeight");
    if (weighting == Weighting.MinVar) {
      sb.append(String.format(" #corr=%d", nCorrelation));
    }
    sb.append(String.format(" #mom=[%d]/[%d,%d]  #keep=(%.1f%%,%d)", nTriggerA, nBaseA, nBaseB, maxKeepFrac * 100,
        maxKeep));
    return sb.toString();
  }

  public static ConfigAdaptive buildEqualWeight(int nTrigger, int nBaseA, int nBaseB, double maxKeepFrac, int maxKeep,
      int pctQuantum, TradeFreq tradeFreq, int iPrice)
  {
    return new ConfigAdaptive(-1, -1, Weighting.Equal, nTrigger, nBaseA, nBaseB, maxKeepFrac, maxKeep, pctQuantum,
        tradeFreq, iPrice);
  }
}
