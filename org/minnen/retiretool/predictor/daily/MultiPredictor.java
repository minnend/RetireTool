package org.minnen.retiretool.predictor.daily;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.util.Library;

/** Combines the in/out votes from multiple predictors. */
public class MultiPredictor extends Predictor
{
  private final long assetMap;

  /** If the vote is positive ("in") hold `assetName`, else `alternativeAsset`. */
  public MultiPredictor(Predictor[] predictors, long assetMap, String assetName, String alternativeAsset,
      BrokerInfoAccess brokerAccess)
  {
    super("MultiPredictor", brokerAccess, new String[] { assetName, alternativeAsset });
    this.predictorType = PredictorType.SelectOne;
    this.predictors = predictors;
    this.assetMap = assetMap;
    reset(); // child predictors may have already been used.
  }

  @Override
  protected String calcSelectOne()
  {
    return (calcInOut() ? assetChoices[0] : assetChoices[1]);
  }

  /** At each point in time, the strategy is either "in" the market or "out" of the market. */
  private boolean calcInOut()
  {
    // Every internal predictors votes on being "in" the market.
    int code = 0;
    for (int i = 0; i < predictors.length; i++) {
      code <<= 1;
      String name = predictors[i].calcSelectOne();
      if (name.equals(assetChoices[0])) {
        ++code;
      }
    }

    // Keep track of the times that the in/out decision changed.
    int prevCode = (timeCodes.isEmpty() ? -1 : timeCodes.get(timeCodes.size() - 1).code);
    if (code != prevCode) {
      timeCodes.add(new TimeCode(brokerAccess.getTime(), code));
    }

    if (assetMap < 0) {
      int nYes = Library.numBits(code);
      int nNo = predictors.length - nYes;
      // System.out.printf("code=%d (%d) #yes=%d #no=%d vote=%b\n", code, predictors.length, nYes, nNo,
      // nNo <= Math.abs(assetMap));
      return nNo <= Math.abs(assetMap); // combined vote is "in" if few enough constituents voted "out"
    } else {
      // Check the corresponding bit in the asset map.
      return ((assetMap >> code) & 1L) > 0;
    }
  }
}
