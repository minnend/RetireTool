package org.minnen.retiretool.data.quandl;

import java.io.File;
import java.io.IOException;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.util.TimeLib;

public class QuandlSeries
{
  public final String name;
  public final String dataURL;
  public final String humanURL;
  public Sequence     data;

  public QuandlSeries(String name, String dataURL, String humanURL)
  {
    this.name = name;
    this.dataURL = dataURL;
    this.humanURL = humanURL;
  }

  public boolean loadData()
  {
    if (data != null) return true;
    try {
      File file = QuandlIO.downloadData(QuandlIO.getPath(), name, dataURL, TimeLib.MS_IN_HOUR * 8);
      this.data = DataIO.loadDateValueCSV(file);
      this.data.setName(name);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public String toString()
  {
    if (data == null) {
      return String.format("[QUANDL|%s]", name);
    } else {
      return String.format("[QUANDL|%s [%s] -> [%s]]", name, TimeLib.formatMonth(data.getStartMS()),
          TimeLib.formatMonth(data.getEndMS()));
    }
  }

  public static QuandlSeries fromName(String name) throws IOException
  {
    for (QuandlSeries quandl : Quandl.series) {
      if (quandl.name.equals(name)) {
        quandl.loadData();
        return quandl;
      }
    }
    throw new IOException("Failed to find Quandl Series: " + name);
  }

}
