package org.minnen.retiretool.predictor.daily;

import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.broker.BrokerInfoAccess;
import org.minnen.retiretool.util.TimeLib;

public class MultiPredictor extends Predictor
{
  public static class TimeCode
  {
    public long time;
    public int  code;

    public TimeCode(long time, int code)
    {
      this.time = time;
      this.code = code;
    }

    @Override
    public String toString()
    {
      return String.format("[%s]: %d", TimeLib.formatDate(time), code);
    }
  }

  private final long          assetMap;
  public final List<TimeCode> timeCodes = new ArrayList<>();

  public MultiPredictor(Predictor[] predictors, long assetMap, String assetName, String alternativeAsset,
      BrokerInfoAccess brokerAccess)
  {
    super("Multiscale", brokerAccess, new String[] { assetName, alternativeAsset });
    this.predictorType = PredictorType.InOut;
    this.predictors = predictors;
    this.assetMap = assetMap;
    reset(); // child predictors may have already been used.
  }

  @Override
  protected boolean calcInOut()
  {
    int code = 0;
    int nIn = 0;
    for (int i = 0; i < predictors.length; i++) {
      code <<= 1;
      if (predictors[i].calcInOut()) {
        ++code;
        ++nIn;
      }
    }

    int prevCode = (timeCodes.isEmpty() ? -1 : timeCodes.get(timeCodes.size() - 1).code);
    if (code != prevCode) {
      timeCodes.add(new TimeCode(brokerAccess.getTime(), code));
    }

    if (assetMap < 0) {
      return (nIn > predictors.length / 2);
    } else {
      return ((assetMap >> code) & 1L) > 0;
    }
  }
}
