package org.minnen.retiretool.data.fred;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.data.fred.FredSeries.Frequency;

public class Fred
{
  public static final List<FredSeries> series = new ArrayList<>();

  static {
    add("cpi", "CPIAUCNS", "CPI for All Urban Consumers: All Items (Not Seasonally Adjusted)", Frequency.Monthly);
    // Note: 3-month constant maturity data only goes back to 1982; secondary market goes to 1934.
    add("3-month-treasury", "TB3MS", "3-Month Treasury Bill: Secondary Market Rate", Frequency.Monthly);
    add("1-year-treasury", "DGS1", "1-Year Treasury Constant Maturity Rate", Frequency.Daily);
    add("2-year-treasury", "DGS2", "2-Year Treasury Constant Maturity Rate", Frequency.Daily);
    add("5-year-treasury", "DGS5", "5-Year Treasury Constant Maturity Rate", Frequency.Daily);
    add("7-year-treasury", "DGS7", "7-Year Treasury Constant Maturity Rate", Frequency.Daily);
    add("10-year-treasury", "DGS10", "10-Year Treasury Constant Maturity Rate", Frequency.Daily);
    add("30-year-treasury", "DGS30", "30-Year Treasury Constant Maturity Rate", Frequency.Daily);
    add("unemployment initial claims", "ICSA", "Unemployment insurance weekly claims", Frequency.Weekly);
    add("unemployment rate", "UNRATE", "Civilian Unemployment Rate", Frequency.Monthly);
    add("recession probability", "RECPROUSM156N", "Smoothed U.S. Recession Probabilities", Frequency.Monthly);
    add("national activity index", "CFNAI", "Chicago Fed National Activity Index", Frequency.Monthly);
    add("leading index", "USSLIND", "Leading Index for the United States", Frequency.Monthly);

    // Component series from:
    // http://www.philosophicaleconomics.com/2013/12/the-single-greatest-predictor-of-future-stock-market-returns/
    add("NCBEILQ027S", "Nonfinancial corporate business; corporate equities; liability, Level", Frequency.Quarterly);
    add("FBCELLQ027S", "Financial business; corporate equities; liability, Leve", Frequency.Quarterly);
    add("TCMILBSNNCB", "Nonfinancial Corporate Business; Credit Market Instruments; Liability, Level",
        Frequency.Quarterly);
    add("TCMILBSHNO", "Households and Nonprofit Organizations; Credit Market Instruments; Liability, Level",
        Frequency.Quarterly);
    add("FGTCMDODNS", "Federal Government; Credit Market Instruments; Liability, Level", Frequency.Quarterly);
    add("SLGTCMDODNS",
        "State and Local Governments, Excluding Employee Retirement Funds; Credit Market Instruments; Liability",
        Frequency.Quarterly);
    add("WCMITCMFODNS", "Rest of the World; Credit Market Instruments; Liability, Level", Frequency.Quarterly);
  }

  /** Shortcut / wrapper to create and add a new FredSeries. */
  private static void add(String name, String seriesID, String description, Frequency frequency)
  {
    series.add(new FredSeries(name, seriesID, description, frequency));
  }

  /** Shortcut / wrapper to create and add a new FredSeries (name = series ID). */
  private static void add(String seriesID, String description, Frequency frequency)
  {
    add(seriesID, seriesID, description, frequency);
  }

  public static void main(String[] args) throws IOException
  {
    for (FredSeries fred : series) {
      if (!fred.loadData()) {
        System.err.println("Failed to load: " + fred);
      }
    }
    for (FredSeries fred : series) {
      System.out.println(fred);
    }
  }
}
