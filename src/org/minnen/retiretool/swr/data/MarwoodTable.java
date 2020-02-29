package org.minnen.retiretool.swr.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.swr.MarwoodMethod;
import org.minnen.retiretool.swr.SwrLib;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.Writer;

public class MarwoodTable
{

  private static void generateTable(File file) throws IOException
  {
    final int[] retireYearsToCalc = new int[] { 0, 10, 20, 25, 30, 40, 50, 60, 70, 75, 80, 90, 100 };

    try (Writer writer = new Writer(file)) {
      for (int nRetireYears = 1; nRetireYears <= 50; ++nRetireYears) {
        for (int percentStock : retireYearsToCalc) {
          Sequence seq = MarwoodMethod.findMarwoodSWR(nRetireYears, percentStock);
          System.out.printf("%d, %d %s\n", nRetireYears, percentStock, seq);
          for (FeatureVec v : seq) {
            final int swr = (int) Math.round(v.get(0) * 100.0);
            writer.write("%d,%d,%s,%d\n", nRetireYears, percentStock, TimeLib.formatYM(v.getTime()), swr);
          }
          break;
        }
      }
    }
  }

  private static void verifyTable(File file) throws IOException
  {
    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      String line = null;
      int nTested = 0;
      while (true) {
        line = reader.readLine();
        if (line == null) break;

        // Remove comments.
        int i = line.indexOf("#");
        if (i >= 0) {
          line = line.substring(0, i);
        }

        // Remove whitespace and skip empty lines.
        line = line.trim();
        if (line.isEmpty()) continue;

        String[] fields = line.split(",");
        assert fields.length == 4;
        final int nRetireYears = Integer.parseInt(fields[0]);
        assert nRetireYears > 0;
        final int percentStock = Integer.parseInt(fields[1]);
        assert percentStock >= 0 && percentStock <= 100;
        final long time = TimeLib.parseDate(fields[2]);
        final int swr = Integer.parseInt(fields[3]);
        assert swr > 0 && swr <= 100000;

        i = SwrLib.indexForTime(time);
        assert SwrLib.time(i) == time;
        MonthlyInfo info = SwrLib.runPeriod(i, swr / 100.0, nRetireYears, percentStock, null);
        assert info.ok();

        ++nTested;
      }

      System.out.printf("Verified entries: %d\n", nTested);
    }
  }

  public static void main(String[] args) throws IOException
  {
    SwrLib.setup();

    File file = new File(DataIO.getOutputPath(), "dmswr-table.csv");
    generateTable(file);
    // verifyTable(file);
  }
}
