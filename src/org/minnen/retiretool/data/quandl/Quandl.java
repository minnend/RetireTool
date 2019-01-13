package org.minnen.retiretool.data.quandl;

import java.util.ArrayList;
import java.util.List;

public class Quandl
{
  public static final List<QuandlSeries> series = new ArrayList<>();

  static {
    series.add(new QuandlSeries("ISM-PMI", // Institute for Supply Management - Purchasing Managers Index
        "https://www.quandl.com/api/v3/datasets/ISM/MAN_PMI.csv",
        "https://www.quandl.com/data/ISM/MAN_PMI-PMI-Composite-Index"));
  }
}
