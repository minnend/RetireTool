package org.minnen.retiretool.broker;

import java.time.LocalDate;

import org.minnen.retiretool.util.TimeLib;

public class TimeInfo
{
  public final long      time;
  public final LocalDate date;
  public final boolean   isFirstDayOfYear;
  public final boolean   isLastDayOfYear;
  public final boolean   isFirstDayOfMonth;
  public final boolean   isLastDayOfMonth;

  public TimeInfo(long time)
  {
    this(time, time, time);
  }

  public TimeInfo(long time, long prevTime, long nextTime)
  {
    this.time = time;

    date = TimeLib.ms2date(time);
    LocalDate prevDate = TimeLib.ms2date(prevTime);
    LocalDate nextDate = TimeLib.ms2date(nextTime);

    // Month values are in [1, 12].
    int month = date.getMonthValue();
    int monthPrev = prevDate.getMonthValue();
    int monthNext = nextDate.getMonthValue();

    int year = date.getYear();
    int yearPrev = prevDate.getYear();
    int yearNext = nextDate.getYear();

    assert (year == yearPrev) || (year == yearPrev + 1);
    isFirstDayOfYear = (year != yearPrev);

    assert (year == yearNext) || (year + 1 == yearNext);
    isLastDayOfYear = (year != yearPrev);

    assert (month == monthPrev) || (monthPrev < 12 && month == monthPrev + 1) || (monthPrev == 12 && month == 1);
    isFirstDayOfMonth = (month != monthPrev);

    assert (month == monthNext) || (month < 12 && month + 1 == monthNext) || (month == 12 && monthNext == 1);
    isLastDayOfMonth = (month != monthNext);
  }
}
