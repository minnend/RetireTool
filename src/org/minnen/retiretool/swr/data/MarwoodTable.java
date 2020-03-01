package org.minnen.retiretool.swr.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.swr.MarwoodMethod;
import org.minnen.retiretool.swr.SwrLib;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.Writer;

public class MarwoodTable
{
  /** Values have valid SWR fields, queries ignore SWR field. */
  public static Map<MarwoodEntry, MarwoodEntry> marwoodMap       = new HashMap<>();

  /** Values hold sequences with all DM-SWRs for a given retirement duration, lookback window, and stock percentage. */
  public static Map<MarwoodEntry, Sequence>     marwoodSequences = new HashMap<>();

  /** Values are SWR for the given retirement duration, lookback window, and stock percentage. */
  public static Map<MarwoodEntry, Integer>      marwoodSWRs      = new HashMap<>();

  public static MarwoodEntry get(int retirementYears, int lookbackYears, int percentStock, long time)
  {
    MarwoodEntry key = new MarwoodEntry(time, retirementYears, lookbackYears, percentStock);
    return marwoodMap.get(key);
  }

  public static Sequence getSeq(int retirementYears, int lookbackYears, int percentStock)
  {
    MarwoodEntry key = new MarwoodEntry(retirementYears, lookbackYears, percentStock);
    return marwoodSequences.get(key);
  }

  public static int getSWR(int retirementYears, int lookbackYears, int percentStock)
  {
    MarwoodEntry key = new MarwoodEntry(retirementYears, lookbackYears, percentStock);
    return marwoodSWRs.get(key);
  }

  public static void clear()
  {
    marwoodMap.clear();
    marwoodSequences.clear();
    marwoodSWRs.clear();
  }

  private static void generateTable(File file) throws IOException
  {
    // TODO generate table for more stock percentages.
    final int[] percentStockList = new int[] { 70 }; // 0, 10, 20, 25, 30, 40, 50, 60, 70, 75, 80, 90, 100 };
    final int[] lookbackYearsList = new int[] { 5, 10, 15, 20 };

    try (Writer writer = new Writer(file)) {
      for (int retirementYears = 1; retirementYears <= 30; ++retirementYears) {
        for (int lookbackYears : lookbackYearsList) {
          for (int percentStock : percentStockList) {
            Sequence seq = MarwoodMethod.findMarwoodSWR(retirementYears, lookbackYears, percentStock);
            System.out.printf("%d, %d, %d %s\n", retirementYears, lookbackYears, percentStock, seq);
            for (FeatureVec v : seq) {
              MarwoodEntry info = new MarwoodEntry(retirementYears, lookbackYears, percentStock, v);
              writer.writeln(info.toCSV());
            }
          }
        }
      }
    }
  }

  public static void loadTable(File file) throws IOException
  {
    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      MarwoodEntry marwoodKey = null;
      Sequence seq = null;
      while (true) {
        String line = reader.readLine();
        if (line == null) break;

        // Remove comments.
        int i = line.indexOf("#");
        if (i >= 0) {
          line = line.substring(0, i);
        }

        // Remove whitespace and skip empty lines.
        line = line.trim();
        if (line.isEmpty()) continue;

        MarwoodEntry info = MarwoodEntry.fromCSV(line);
        assert SwrLib.time(SwrLib.indexForTime(info.time)) == info.time;

        marwoodMap.put(info, info);

        // Create new sequence when retirement scenario changes.
        if (marwoodKey == null || info.retirementYears != marwoodKey.retirementYears
            || info.lookbackYears != marwoodKey.lookbackYears || info.percentStock != marwoodKey.percentStock) {
          if (seq != null) { // store previous sequence and SWR
            marwoodSequences.put(marwoodKey, seq);
            final int swr = (int) Math.round(seq.getMin().get(0));
            marwoodSWRs.put(marwoodKey, swr);
          }

          marwoodKey = new MarwoodEntry(info.retirementYears, info.lookbackYears, info.percentStock);
          seq = new Sequence(
              String.format("Marwood (%d, %d, %d)", info.retirementYears, info.lookbackYears, info.percentStock));
        }

        // Add new month to sequence.
        assert seq.isEmpty() || info.time > seq.getEndMS();
        seq.addData(info.swr, info.time);
      }
      if (seq != null) { // store last sequence and final SWR
        marwoodSequences.put(marwoodKey, seq);
        final int swr = (int) Math.round(seq.getMin().get(0));
        marwoodSWRs.put(marwoodKey, swr);
      }
    }
  }

  private static void verifyTable() throws IOException
  {
    for (MarwoodEntry marwood : marwoodMap.values()) {
      MonthlyInfo info = SwrLib.runPeriod(marwood);
      assert info.ok();
    }
    System.out.printf("Verified entries: %d\n", marwoodMap.size());
  }

  public static void main(String[] args) throws IOException
  {
    SwrLib.setup();
    System.out.printf("DM-SWR entries: %d\n", marwoodMap.size());
    System.out.printf("DM-SWR sequences: %d\n", marwoodSequences.size());

    // File file = new File(DataIO.getFinancePath(), "dmswr-table.csv");
    // generateTable(file);

    verifyTable();
  }
}
