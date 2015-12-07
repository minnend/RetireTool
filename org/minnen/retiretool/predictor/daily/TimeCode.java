package org.minnen.retiretool.predictor.daily;

import java.util.List;

import org.minnen.retiretool.util.TimeLib;

/** Represents a code from a MultiPredictor at a given time. */
public class TimeCode
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

  public static int indexForTime(long time, List<TimeCode> codes)
  {
    if (codes == null || codes.isEmpty() || time < codes.get(0).time) return -1;

    // TODO replace linear scan with binary search.
    for (int i = 1; i < codes.size(); ++i) {
      if (codes.get(i).time > time) return i - 1;
    }
    return codes.size() - 1;
  }
}