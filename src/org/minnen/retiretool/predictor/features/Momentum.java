package org.minnen.retiretool.predictor.features;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.util.FinLib;

public class Momentum extends FeatureExtractor
{
  public enum ReturnOrMul {
    Return, Mul
  }

  public enum CompoundPeriod {
    Total, Annually, Monthly, Weekly
  }

  public final int            nTriggerA;
  public final int            nTriggerB;
  public final int            nBaseA;
  public final int            nBaseB;
  public final int            iPrice;
  public final ReturnOrMul    returnOrMul;
  public final CompoundPeriod compoundPeriod;

  public Momentum(int nTriggerA, int nTriggerB, int nBaseA, int nBaseB, ReturnOrMul returnOrMul,
      CompoundPeriod compoundPeriod, int iPrice)
  {
    super(String.format("Momentum[%d,%d]/[%d,%d]", nTriggerA, nTriggerB, nBaseA, nBaseB));
    this.nTriggerA = nTriggerA;
    this.nTriggerB = nTriggerB;
    this.nBaseA = nBaseA;
    this.nBaseB = nBaseB;
    this.returnOrMul = returnOrMul;
    this.compoundPeriod = compoundPeriod;
    this.iPrice = iPrice;
  }

  @Override
  public FeatureVec calculate(BrokerInfoAccess brokerAccess, int assetID)
  {
    Sequence seq = brokerAccess.getSeq(assetID);

    // Base momentum is the ratio of the trigger average over the base average.
    double now = seq.average(-nTriggerA, -nTriggerB, iPrice);
    double before = seq.average(-nBaseA, -nBaseB, iPrice);
    double momentum = now / before;

    // Adjust total multiplier for different compounding periods.
    if (compoundPeriod != CompoundPeriod.Total) {
      int nBusinessDays = ((nBaseA + nBaseB + 1) - (nTriggerA + nTriggerB)) / 2;
      double nWeeks = nBusinessDays / 5.0;
      // System.out.printf("%s nbd=%d nw=%.2f\n", this, nBusinessDays, nWeeks);
      if (compoundPeriod == CompoundPeriod.Annually) {
        double nYears = nWeeks / 52.0;
        momentum = Math.pow(momentum, 1.0 / nYears);
      } else if (compoundPeriod == CompoundPeriod.Monthly) {
        double nMonths = nWeeks / 4.0;
        momentum = Math.pow(momentum, 1.0 / nMonths);
      } else if (compoundPeriod == CompoundPeriod.Weekly) {
        momentum = Math.pow(momentum, 1.0 / nWeeks);
      }
    }

    // Convert multiplier to a return (1.035 -> 3.5).
    if (returnOrMul == ReturnOrMul.Return) {
      momentum = FinLib.mul2ret(momentum);
    }

    return new FeatureVec(seq.getName(), 1, momentum).setTime(brokerAccess.getTime());
  }
}
