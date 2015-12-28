package org.minnen.retiretool.broker;

import java.time.LocalDate;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.util.TimeLib;

public class TimeInfo
{
  public final long      time;
  public final long      prevTime;
  public final long      nextTime;
  public final LocalDate date;
  public final LocalDate prevDate;
  public final LocalDate nextDate;
  public final boolean   isFirstDayOfYear;
  public final boolean   isLastDayOfYear;
  public final boolean   isFirstDayOfMonth;
  public final boolean   isLastDayOfMonth;
  public final boolean   isFirstDayOfWeek;
  public final boolean   isLastDayOfWeek;

  public TimeInfo(Sequence guideSeq)
  {
    this(guideSeq.getStartMS(), TimeLib.toPreviousBusinessDay(guideSeq.getStartMS()), guideSeq.length() > 1 ? guideSeq
        .getTimeMS(1) : TimeLib.toNextBusinessDay(guideSeq.getStartMS()));
  }

  public TimeInfo(long time, long prevTime, long nextTime)
  {
    assert time != prevTime;
    assert time != nextTime;
    assert prevTime != nextTime;

    this.time = time;
    this.prevTime = prevTime;
    this.nextTime = nextTime;

    this.date = TimeLib.ms2date(time);
    this.prevDate = TimeLib.ms2date(prevTime);
    this.nextDate = TimeLib.ms2date(nextTime);

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

    isFirstDayOfWeek = !TimeLib.isSameWeek(prevDate, date);
    isLastDayOfWeek = !TimeLib.isSameWeek(date, nextDate);
  }

  public String toString()
  {
    return String.format("[%s|%s|%s]", TimeLib.formatDate(prevTime), TimeLib.formatDate(time),
        TimeLib.formatDate(nextTime));
  }
}
