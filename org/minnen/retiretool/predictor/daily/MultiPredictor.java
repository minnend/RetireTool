package org.minnen.retiretool.predictor.daily;

import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.util.Library;

public class MultiPredictor extends Predictor
{
  private final long          assetMap;
  public final List<TimeCode> timeCodes = new ArrayList<>();

  public MultiPredictor(Predictor[] predictors, long assetMap, String assetName, String alternativeAsset,
      BrokerInfoAccess brokerAccess)
  {
    super("Multiscale", brokerAccess, new String[] { assetName, alternativeAsset });
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

  private boolean calcInOut()
  {
    int code = 0;
    for (int i = 0; i < predictors.length; i++) {
      code <<= 1;
      String name = predictors[i].calcSelectOne();
      if (name.equals(assetChoices[0])) {
        ++code;
      }
    }

    int prevCode = (timeCodes.isEmpty() ? -1 : timeCodes.get(timeCodes.size() - 1).code);
    if (code != prevCode) {
      timeCodes.add(new TimeCode(brokerAccess.getTime(), code));
    }

    if (assetMap < 0) {
      int nYes = Library.numBits(code);
      int nNo = predictors.length - nYes;
      // System.out.printf("code=%d (%d)  #yes=%d  #no=%d  vote=%b\n", code, predictors.length, nYes, nNo,
      // nNo <= Math.abs(assetMap));
      return nNo <= Math.abs(assetMap);
    } else {
      return ((assetMap >> code) & 1L) > 0;
    }
  }
}
