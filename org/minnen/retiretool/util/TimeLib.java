package org.minnen.retiretool.util;

import static java.util.Calendar.SUNDAY;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Stack;
import java.util.TimeZone;

import org.minnen.retiretool.data.Sequence;

public class TimeLib
{
  public final static long             TIME_ERROR = Library.LNAN;
  public final static long             TIME_BEGIN = Long.MIN_VALUE + 1;
  public final static long             TIME_END   = Long.MAX_VALUE - 1;
  public final static long             MS_IN_HOUR = 60 * 60 * 1000L;
  public final static long             MS_IN_DAY  = 24 * MS_IN_HOUR;

  public static TimeZone               utc        = TimeZone.getTimeZone("GMT");
  public final static SimpleDateFormat sdfTime    = getSDF("yyyy MMM d HH:mm:ss");
  public final static SimpleDateFormat sdfDate    = getSDF("d MMM yyyy");
  public final static SimpleDateFormat sdfMonth   = getSDF("MMM yyyy");
  public final static SimpleDateFormat sdfYMD     = getSDF("yyyy-MM-dd");

  public final static Stack<Calendar>  calendars  = new Stack<Calendar>();

  /**
   * Return the current time in milliseconds. This function just forwards the request to System.currentTimeMillis();
   * 
   * @return the current time in milliseconds (since midnight Jan 1, 1970, UTC)
   */
  public static long getTime()
  {
    return System.currentTimeMillis();
  }

  /**
   * @return current time (UTC) with first day of week set to sunday
   */
  public static Calendar now()
  {
    Calendar cal = Calendar.getInstance(utc);
    cal.setLenient(true);
    cal.setFirstDayOfWeek(SUNDAY);
    return cal;
  }

  public static Calendar ms2cal(long timeMS)
  {
    Calendar cal = now();
    cal.setTimeInMillis(timeMS);
    return cal;
  }

  public static double fractionalMonthsBetween(long from, long to)
  {
    Calendar cal = borrowCal(from);
    int year1 = cal.get(Calendar.YEAR);
    int month1 = cal.get(Calendar.MONTH);
    int day1 = cal.get(Calendar.DAY_OF_MONTH);

    cal.setTimeInMillis(to);
    int year2 = cal.get(Calendar.YEAR);
    int month2 = cal.get(Calendar.MONTH);
    int day2 = cal.get(Calendar.DAY_OF_MONTH);
    returnCal(cal);

    return (year2 - year1) * 12 + (month2 - month1) + (day2 - day1) / 30.0;
  }

  public static int monthsBetween(long from, long to)
  {
    Calendar cal = borrowCal(from);
    int year1 = cal.get(Calendar.YEAR);
    int month1 = cal.get(Calendar.MONTH);

    cal.setTimeInMillis(to);
    int year2 = cal.get(Calendar.YEAR);
    int month2 = cal.get(Calendar.MONTH);
    returnCal(cal);

    return Math.abs((year2 - year1) * 12 + (month2 - month1));
  }

  /**
   * Returns index corresponding to first entry of new decade (e.g. January 1880).
   * 
   * @param seq Sequence with timestamps
   * @return index for first decade or -1 if none
   */
  public static int findStartofFirstDecade(Sequence seq)
  {
    // Find start of decade.
    Calendar cal = now();
    int iStart = -1;
    for (int i = 0; i < seq.length(); ++i) {
      cal.setTimeInMillis(seq.getTimeMS(i));
      if (cal.get(Calendar.MONTH) == 0 && cal.get(Calendar.YEAR) % 10 == 0) {
        iStart = i;
        break;
      }
    }
    return iStart;
  }

  public static long calcCommonStart(Sequence... seqs)
  {
    long last = seqs[0].getStartMS();
    for (Sequence seq : seqs) {
      if (seq.getStartMS() > last) {
        last = seq.getStartMS();
      }
    }
    return last;
  }

  public static long calcCommonEnd(Sequence... seqs)
  {
    long last = seqs[0].getEndMS();
    for (Sequence seq : seqs) {
      if (seq.getEndMS() < last) {
        last = seq.getEndMS();
      }
    }
    return last;
  }

  /** Borrow a Calendar set to the given time (ms since epoch). */
  public static Calendar borrowCal(long timeMS)
  {
    assert timeMS != TIME_ERROR;
    Calendar cal = borrowCal();
    cal.setTimeInMillis(timeMS);
    return cal;
  }

  /** Borrow a Calendar set to an arbitrary time. */
  public static Calendar borrowCal()
  {
    if (calendars.isEmpty()) {
      return now();
    } else {
      return calendars.pop();
    }
  }

  public static long returnCal(Calendar cal)
  {
    assert cal != null;
    calendars.push(cal);
    return cal.getTimeInMillis();
  }

  public static void returnCals(Calendar... cals)
  {
    for (Calendar cal : cals) {
      returnCal(cal);
    }
  }

  public static boolean isSameDay(long time1, long time2)
  {
    Calendar cal1 = borrowCal(time1);
    Calendar cal2 = borrowCal(time2);

    boolean bSame = (cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
        && cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR));

    returnCals(cal1, cal2);
    return bSame;
  }

  public static boolean isSameMonth(long time1, long time2)
  {
    Calendar cal1 = borrowCal(time1);
    Calendar cal2 = borrowCal(time2);

    boolean bSame = (cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) && cal1.get(Calendar.YEAR) == cal2
        .get(Calendar.YEAR));

    returnCals(cal1, cal2);
    return bSame;
  }

  /**
   * @return calendar object representing the given date
   */
  public static Calendar date2cal(Date date)
  {
    Calendar cal = now();
    cal.setTimeInMillis(date.getTime());
    return cal;
  }

  public static String getDayOfWeekName(int dow)
  {
    switch (dow) {
    case Calendar.SUNDAY:
      return "Sunday";
    case Calendar.MONDAY:
      return "Monday";
    case Calendar.TUESDAY:
      return "Tuesday";
    case Calendar.WEDNESDAY:
      return "Wednesday";
    case Calendar.THURSDAY:
      return "Thursday";
    case Calendar.FRIDAY:
      return "Friday";
    case Calendar.SATURDAY:
      return "Saturday";
    }
    return null;
  }

  /**
   * @param day day of month
   * @param month month of year (January = 1)
   * @param year Gregorian year
   * @return milliseconds since the epoch for midnight on the specified date (midnight = start of day, not end of day)
   */
  public static long getTime(int day, int month, int year)
  {
    Calendar cal = borrowCal(0L);
    setTime(cal, day, month, year);
    return returnCal(cal);
  }

  /**
   * @param day day of month
   * @param month month of year (January = 1)
   * @param year Gregorian year
   * @return milliseconds since the epoch for midnight on the specified date (midnight = start of day, not end of day)
   */
  public static Calendar setTime(Calendar cal, int day, int month, int year)
  {
    assert day >= 1 && day <= 31;
    assert month >= 1 && month <= 12;
    cal.set(Calendar.DATE, day);
    cal.set(Calendar.MONTH, month - 1);
    cal.set(Calendar.YEAR, year);
    cal.set(Calendar.HOUR_OF_DAY, 8);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    return cal;
  }

  /** @return milliseconds since start of the day for the given time (specified in ms since epoch) */
  public static long getMillisSinceStartOfDay(long ms)
  {
    Calendar cal = borrowCal();
    cal.setTimeInMillis(ms);
    int day = cal.get(Calendar.DAY_OF_MONTH);
    int month = cal.get(Calendar.MONTH) + 1;
    int year = cal.get(Calendar.YEAR);
    returnCal(cal);
    long startOfDay = getTime(day, month, year);
    return ms - startOfDay;
  }

  /**
   * @param day day of month
   * @param month month of year (January = 1)
   * @param year Gregorian year
   * @return milliseconds since the epoch for the last moment on the specified date (i.e., end of day)
   */
  public static long getEOD(int day, int month, int year)
  {
    return getTime(day, month, year) + (24 * 60 * 60 * 1000 - 1);
  }

  /** @return string representation of the given day (year, month, date) */
  public static String formatDate(long ms)
  {
    if (ms == TIME_ERROR || ms == Long.MAX_VALUE) return null;
    return sdfDate.format(new Date(ms));
  }

  /** @return string representation of the given month (year, month) */
  public static String formatMonth(long ms)
  {
    if (ms == TIME_ERROR || ms == Long.MAX_VALUE) return null;
    return sdfMonth.format(new Date(ms));
  }

  /** @return string representation of the given time as year-month-day */
  public static String formatYMD(long ms)
  {
    if (ms == TIME_ERROR || ms == Long.MAX_VALUE) return null;
    return sdfYMD.format(new Date(ms));
  }

  /** @return ms since epoch at midnight that starts the day of the given time */
  public static long toMidnight(long ms)
  {
    assert ms != TIME_ERROR;
    long days = ms / MS_IN_DAY;
    // if data is before 1970, ms is neg and floor does the wrong thing
    if (ms < 0) days -= 1;
    return days * MS_IN_DAY;
  }

  /** @return ms for the given time moved to the first day of the month */
  public static long toFirstOfMonth(long ms)
  {
    Calendar cal = borrowCal(ms);
    cal.set(Calendar.DATE, 1);
    return returnCal(cal);
  }

  /** @return ms for the given time moved to the last day of the month */
  public static long toEndOfMonth(long ms)
  {
    // Move to next month.
    Calendar cal = borrowCal(ms);
    cal.add(Calendar.MONTH, 1);

    // Move to first day of next month.
    cal.setTimeInMillis(toFirstOfMonth(cal.getTimeInMillis()));

    // Move one day back, which is the last day of the original month.
    cal.add(Calendar.DATE, -1);
    return returnCal(cal);
  }

  /** @return ms for the given time moved to the last business day of the month */
  public static long toLastBusinessDayOfMonth(long ms)
  {
    // Move to next month.
    Calendar cal = borrowCal(ms);
    cal.add(Calendar.MONTH, 1);

    // Move to first day of next month.
    ms = toFirstOfMonth(cal.getTimeInMillis());
    returnCal(cal);

    // Return previous business day.
    return toPreviousBusinessDay(ms);
  }

  /** @return string representation of the given time (year, month, date, hour, minute, second) */
  public static String formatTime(Date date)
  {
    if (date == null) return "null";
    return sdfTime.format(date);
  }

  /** @return string representation of the given time (year, month, date, hour, minute, second) */
  public static String formatTime(long ms)
  {
    if (ms == TIME_ERROR || ms == Long.MAX_VALUE) return null;
    return sdfTime.format(new Date(ms));
  }

  /** @return human readable string representing the given amount of time */
  public static String formatDuration(long ms)
  {
    return formatDuration(ms, 1);
  }

  /** @return human readable string representing the given amount of time */
  public static String formatDuration(long ms, int nSigDig)
  {
    boolean bNeg = (ms < 0);
    ms = Math.abs(ms);

    double second = 1000.0;
    double minute = 60 * second;
    double hour = 60 * minute;
    double day = 24 * hour;
    double week = 7 * day;
    double month = 30.4167 * day;
    double year = 365 * day;

    String sNum = String.format("%%.%df", nSigDig);
    String sRet;
    if (ms > 18 * month) sRet = String.format(sNum + " years", ms / year);
    else if (ms > 6 * week) sRet = String.format(sNum + " months", ms / month);
    else if (ms > 10 * day) sRet = String.format(sNum + " weeks", ms / week);
    else if (ms > day) sRet = String.format(sNum + " days", ms / day);
    else if (ms > hour) sRet = String.format(sNum + " hours", ms / hour);
    else if (ms > minute) sRet = String.format(sNum + " min", ms / minute);
    else if (ms > second) sRet = String.format(sNum + "s", ms / second);
    else sRet = String.format("%dms", ms);

    if (bNeg) sRet = "-" + sRet;

    return sRet;
  }

  public static String formatDurationMonths(int nMonths)
  {
    if (nMonths == 12) {
      return "1 year";
    }
    if (nMonths < 18) {
      return String.format("%d months", nMonths);
    }
    if (nMonths % 12 == 0) {
      return String.format("%d years", nMonths / 12);
    }
    return String.format("%.1f years", nMonths / 12.0);
  }

  /** @return sdf for the given format set to UTC */
  public static SimpleDateFormat getSDF(String sFormat)
  {
    SimpleDateFormat sdf = new SimpleDateFormat(sFormat);
    sdf.setTimeZone(utc);
    return sdf;
  }

  public static long toPreviousBusinessDay(long time)
  {
    Calendar cal = TimeLib.borrowCal(time);
    while (true) {
      cal.add(Calendar.DAY_OF_YEAR, -1);
      int day = cal.get(Calendar.DAY_OF_WEEK);
      if (day != Calendar.SATURDAY && day != Calendar.SUNDAY) {
        break;
      }
    }
    return returnCal(cal);
  }

  public static long toNextBusinessDay(long time)
  {
    Calendar cal = TimeLib.borrowCal(time);
    while (true) {
      cal.add(Calendar.DAY_OF_YEAR, 1);
      int day = cal.get(Calendar.DAY_OF_WEEK);
      if (day != Calendar.SATURDAY && day != Calendar.SUNDAY) {
        break;
      }
    }
    return returnCal(cal);
  }

  /**
   * Return time for closest business day for the given month and day.
   * 
   * @param timeInMonth Specify the month using any time in that month.
   * @param dayOfMonth We want the equivalent time on this day (or closest business day).
   * @param bAcceptDifferentMonth if false only days in the same month are considered.
   * @return time (in ms) of the closest business day on the requested day.
   */
  public static long getClosestBusinessDay(long timeInMonth, int dayOfMonth, boolean bAcceptDifferentMonth)
  {
    Calendar cal = TimeLib.borrowCal(timeInMonth);
    long baseTime = TimeLib.getTime(dayOfMonth, cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR));

    // If base is a business day, we're done.
    int day = cal.get(Calendar.DAY_OF_WEEK);
    if (day != Calendar.SATURDAY && day != Calendar.SUNDAY) {
      TimeLib.returnCal(cal);
      return baseTime;
    }

    long prevTime = toPreviousBusinessDay(baseTime);
    long nextTime = toNextBusinessDay(baseTime);
    assert prevTime < baseTime;
    assert nextTime > baseTime;

    if (!bAcceptDifferentMonth) {
      Calendar calPrev = TimeLib.borrowCal(prevTime);
      if (calPrev.get(Calendar.MONTH) != cal.get(Calendar.MONTH)) {
        TimeLib.returnCals(cal, calPrev);
        return nextTime;
      }

      Calendar calNext = TimeLib.borrowCal(nextTime);
      if (calNext.get(Calendar.MONTH) != cal.get(Calendar.MONTH)) {
        TimeLib.returnCals(cal, calNext);
        return prevTime;
      }
    }

    TimeLib.returnCal(cal);
    return (baseTime - prevTime < nextTime - baseTime ? prevTime : nextTime);
  }
}
