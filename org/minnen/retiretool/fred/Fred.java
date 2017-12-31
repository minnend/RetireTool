package org.minnen.retiretool.fred;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.fred.FredSeries.Frequency;

public class Fred
{
  public static final List<FredSeries> series = new ArrayList<>();

  static {
    series.add(new FredSeries("cpi", "CPIAUCNS", "CPI for All Urban Consumers: All Items (Not Seasonally Adjusted)",
        Frequency.Monthly));
    // Note: 3-month constant maturity data only goes back to 1982; secondary market goes to 1934.
    series.add(
        new FredSeries("3-month-treasury", "TB3MS", "3-Month Treasury Bill: Secondary Market Rate", Frequency.Monthly));
    series.add(new FredSeries("1-year-treasury", "DGS1", "1-Year Treasury Constant Maturity Rate", Frequency.Daily));
    series.add(new FredSeries("2-year-treasury", "DGS2", "2-Year Treasury Constant Maturity Rate", Frequency.Daily));
    series.add(new FredSeries("5-year-treasury", "DGS5", "5-Year Treasury Constant Maturity Rate", Frequency.Daily));
    series.add(new FredSeries("7-year-treasury", "DGS7", "7-Year Treasury Constant Maturity Rate", Frequency.Daily));
    series.add(new FredSeries("10-year-treasury", "DGS10", "10-Year Treasury Constant Maturity Rate", Frequency.Daily));
    series.add(new FredSeries("30-year-treasury", "DGS30", "30-Year Treasury Constant Maturity Rate", Frequency.Daily));
  }

  public static FredSeries getName(String name) throws IOException
  {
    name = name.toLowerCase();
    for (FredSeries fred : series) {
      if (fred.name.equals(name)) {
        fred.loadData();
        return fred;
      }
    }
    return null;
  }

  public static void main(String[] args) throws IOException
  {
    for (FredSeries fred : series) {
      fred.loadData();
      System.out.println(fred);
    }
  }
}
