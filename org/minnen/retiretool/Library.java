package org.minnen.retiretool;

import java.text.*;
import java.util.regex.*;
import java.util.*;

import static java.util.Calendar.*;

public final class Library
{
  public final static long             LNAN         = Long.MIN_VALUE;
  public final static long             TIME_ERROR   = LNAN;
  public final static int              INDEX_ERROR  = Integer.MIN_VALUE;

  public final static double           FPMIN        = Double.MIN_VALUE;
  public final static double           INF          = Double.POSITIVE_INFINITY;
  public final static double           NEGINF       = Double.NEGATIVE_INFINITY;

  /** log(0.0) = -infinity */
  public final static double           LOG_ZERO     = NEGINF;

  /** log(1.0) = 0.0 */
  public final static double           LOG_ONE      = 0.0;

  public final static double           LOG_TWO      = Math.log(2.0);
  public final static double           MINV_ABS     = 1.0e-9;
  public final static double           TWO_PI       = 2.0 * Math.PI;
  public final static double           PI_OVER_TWO  = Math.PI / 2.0;
  public final static double           SQRT_2PI     = Math.sqrt(TWO_PI);
  public final static double           SQRT_2       = Math.sqrt(2.0);
  public static final double           ONE_TWELFTH  = 1.0 / 12.0;

  public static TimeZone               utc          = TimeZone.getTimeZone("GMT");
  public final static SimpleDateFormat sdfTime      = getSDF("yyyy MMM d HH:mm:ss");
  public final static SimpleDateFormat sdfDate      = getSDF("d MMM yyyy");
  public final static SimpleDateFormat sdfMonth     = getSDF("MMM yyyy");
  public final static DecimalFormat    df           = new DecimalFormat();
  public final static long             AppStartTime = getTime();
  public static final String           os           = System.getProperty("os.name");
  public static final boolean          bWindows     = os.startsWith("Win");

  public static enum MatrixOrder {
    RowMajor, ColumnMajor
  }

  static {
    df.setMaximumFractionDigits(4);
  }

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
    cal.setFirstDayOfWeek(SUNDAY);
    return cal;
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
    Calendar cal = now();
    cal.setLenient(true);
    cal.setTimeInMillis(0);
    cal.set(Calendar.DATE, day);
    cal.set(Calendar.MONTH, month - 1);
    cal.set(Calendar.YEAR, year);
    return cal.getTimeInMillis();
  }

  /** @return milliseconds since start of the day for the given time (specified in ms since epoch) */
  public static long getMillisInDay(long ms)
  {
    Calendar cal = now();
    cal.setLenient(true);
    cal.setTimeInMillis(ms);
    int day = cal.get(Calendar.DAY_OF_MONTH);
    int month = cal.get(Calendar.MONTH) + 1;
    int year = cal.get(Calendar.YEAR);
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
  public static String formatDate(Date date)
  {
    if (date == null)
      return null;
    return sdfDate.format(date);
  }

  /** @return string representation of the given day (year, month, date) */
  public static String formatDate(long ms)
  {
    if (ms == TIME_ERROR || ms == Long.MAX_VALUE)
      return null;
    return sdfDate.format(new Date(ms));
  }

  /** @return string representation of the given month (year, month) */
  public static String formatMonth(long ms)
  {
    if (ms == TIME_ERROR || ms == Long.MAX_VALUE)
      return null;
    return sdfMonth.format(new Date(ms));
  }

  /** @return date at midnight that starts the given day */
  public static Date toMidnight(Date date)
  {
    return new Date(toMidnight(date.getTime()));
  }

  /** @return ms since epoch at midnight that starts the day of the given time */
  public static long toMidnight(long ms)
  {
    final long d1 = 1000 * 60 * 60 * 24; // ms in one day
    long days = ms / d1;
    // if data is before 1970, ms is neg and floor does the wrong thing
    if (ms < 0)
      days -= 1;
    return days * d1;
  }

  /** @return string representation of the given time (year, month, date, hour, minute, second) */
  public static String formatTime(Date date)
  {
    if (date == null)
      return "null";
    return sdfTime.format(date);
  }

  /** @return string representation of the given time (year, month, date, hour, minute, second) */
  public static String formatTime(long ms)
  {
    if (ms == TIME_ERROR || ms == Long.MAX_VALUE)
      return null;
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
    if (ms > 18 * month)
      sRet = String.format(sNum + " years", ms / year);
    else if (ms > 6 * week)
      sRet = String.format(sNum + " months", ms / month);
    else if (ms > 10 * day)
      sRet = String.format(sNum + " weeks", ms / week);
    else if (ms > day)
      sRet = String.format(sNum + " days", ms / day);
    else if (ms > hour)
      sRet = String.format(sNum + " hours", ms / hour);
    else if (ms > minute)
      sRet = String.format(sNum + " min", ms / minute);
    else if (ms > second)
      sRet = String.format(sNum + "s", ms / second);
    else
      sRet = String.format("%dms", ms);

    if (bNeg)
      sRet = "-" + sRet;

    return sRet;
  }

  /**
   * Convert a string to the time it represents; yyyy MM dd hh mm ss[.s*] OR yyyy MM dd hh mm ss uuu OR X+ (ms since
   * epoch)
   * 
   * @return time represented by the given string
   */
  public static Calendar str2cal(String s)
  {
    String sRexSeconds = "(\\d{4}).+?(\\d{2}).+?(\\d{2}).+?(\\d{2}).+?(\\d{2}).+?(\\d{2}(?:\\.\\d+)?)";
    String sRexMS = "(\\d{4}).+?(\\d{2}).+?(\\d{2}).+?(\\d{2}).+?(\\d{2}).+?(\\d{2}).+?(\\d{3})";

    // try parsine data in piecewise (seconds) fromat
    Pattern pat = Pattern.compile(sRexSeconds);
    Matcher m = pat.matcher(s);
    if (m.find()) {
      int year = Integer.parseInt(m.group(1));
      int month = Integer.parseInt(m.group(2)) - 1;
      int day = Integer.parseInt(m.group(3));
      int hour = Integer.parseInt(m.group(4));
      int minute = Integer.parseInt(m.group(5));
      double seconds = Double.parseDouble(m.group(6));
      int ms = (int) Math.round((seconds - Math.floor(seconds)) * 1000);

      Calendar cal = now();
      cal.set(year, month, day, hour, minute, (int) seconds);
      cal.setTimeInMillis(cal.getTimeInMillis() + ms);
      return cal;
    }

    // try parsing data in piecewise + ms format
    pat = Pattern.compile(sRexMS);
    m = pat.matcher(s);
    if (m.find()) {
      int year = Integer.parseInt(m.group(1));
      int month = Integer.parseInt(m.group(2)) - 1;
      int day = Integer.parseInt(m.group(3));
      int hour = Integer.parseInt(m.group(4));
      int minute = Integer.parseInt(m.group(5));
      int seconds = Integer.parseInt(m.group(6));
      int ms = Integer.parseInt(m.group(7));

      Calendar cal = now();
      cal.set(year, month, day, hour, minute, seconds);
      cal.setTimeInMillis(cal.getTimeInMillis() + ms);
      return cal;
    }

    // try parsing date as ms since epoch
    try {
      long ms = Long.parseLong(s);
      Calendar cal = now();
      cal.setTimeInMillis(ms);
      return cal;
    } catch (NumberFormatException e) {
    }

    return null;
  }

  /** @return sdf for the given format set to UTC */
  public static SimpleDateFormat getSDF(String sFormat)
  {
    SimpleDateFormat sdf = new SimpleDateFormat(sFormat);
    sdf.setTimeZone(utc);
    return sdf;
  }

  public static void copy(double from[], double[] to)
  {
    int n = Math.min(from.length, to.length);
    for (int i = 0; i < n; i++)
      to[i] = from[i];
  }

  public static void copy(double from[], double[] to, int iStartFrom, int iStartTo, int len)
  {
    for (int i = 0; i < len; i++)
      to[i + iStartTo] = from[i + iStartFrom];
  }

  public static double[][] allocMatrixDouble(int nRows, int nCols)
  {
    return allocMatrixDouble(nRows, nCols, 0.0);
  }

  public static double[][] allocMatrixDouble(int nRows, int nCols, double init)
  {
    double a[][] = new double[nRows][nCols];
    for (int i = 0; i < nRows; i++)
      Arrays.fill(a[i], init);
    return a;
  }

  /**
   * Try to parse the string as a double.
   * 
   * @param s string to parse
   * @param failValue return this value if parse fails
   * @return numeric value of s or failValue if parsing fails
   */
  public static double tryParse(String s, double failValue)
  {
    try {
      return Double.parseDouble(s);
    } catch (NumberFormatException nfe) {
      return failValue;
    }
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

  public static String getDurationString(int nMonths)
  {
    if (nMonths == 12) {
      return "1 year";
    }
    if (nMonths < 18) {
      return String.format("%d months");
    }
    if (nMonths % 12 == 0) {
      return String.format("%d years", nMonths / 12);
    }
    return String.format("%.1f years", nMonths / 12.0);
  }
}
