package org.minnen.retiretool.broker;

import java.util.Calendar;

import org.minnen.retiretool.Library;

public class TimeInfo
{
  public final long     time;
  public final Calendar calendar;
  public final boolean  isFirstDayOfYear;
  public final boolean  isLastDayOfYear;
  public final boolean  isFirstDayOfMonth;
  public final boolean  isLastDayOfMonth;

  public TimeInfo(long time, long prevTime, long nextTime)
  {
    this.time = time;
    calendar = Library.calFromTime(time);

    Calendar calPrev = Library.calFromTime(prevTime);
    Calendar calNext = Library.calFromTime(nextTime);

    int month = calendar.get(Calendar.MONTH);
    int monthPrev = calPrev.get(Calendar.MONTH);
    int monthNext = calNext.get(Calendar.MONTH);

    int year = calendar.get(Calendar.YEAR);
    int yearPrev = calPrev.get(Calendar.YEAR);
    int yearNext = calNext.get(Calendar.YEAR);

    assert (year == yearPrev) || (year == yearPrev + 1);
    isFirstDayOfYear = (year != yearPrev);

    assert (year == yearNext) || (year + 1 == yearNext);
    isLastDayOfYear = (year != yearPrev);

    assert (month == monthPrev) || (monthPrev < 11 && month == monthPrev + 1) || (monthPrev == 11 && month == 0);
    isFirstDayOfMonth = (month != monthPrev);

    assert (month == monthNext) || (month < 11 && month + 1 == monthNext) || (month == 11 && monthNext == 0);
    isLastDayOfMonth = (month != monthNext);
  }
}
