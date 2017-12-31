package org.minnen.retiretool.fred;

import java.io.File;
import java.io.IOException;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FredIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.util.TimeLib;

public class FredSeries
{
  public enum Frequency {
    Daily, Monthly, Yearly
  };

  public final String    name;
  public final String    seriesID;
  public final String    description;
  public final Frequency frequency;
  public Sequence        seq;

  public FredSeries(String name, String seriesID, String description, Frequency frequency)
  {
    this.name = name;
    this.seriesID = seriesID;
    this.description = description;
    this.frequency = frequency;
  }

  public boolean loadData()
  {
    if (seq != null) return true;
    try {
      File file = FredIO.downloadData(FredIO.fredPath, seriesID, TimeLib.MS_IN_HOUR * 8);
      this.seq = DataIO.loadDateValueCSV(file);
      if (frequency == Frequency.Monthly) {
        this.seq.adjustDatesToEndOfMonth();
      }
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public String toString()
  {
    if (seq == null) {
      return String.format("[FRED| %s (%s) %s]", name, seriesID, description);
    } else {
      return String.format("[FRED| %s (%s) [%s] -> [%s]]", name, seriesID, TimeLib.formatMonth(seq.getStartMS()),
          TimeLib.formatMonth(seq.getEndMS()));
    }
  }
}
