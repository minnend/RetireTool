package org.minnen.retiretool.swr.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.swr.BengenMethod;
import org.minnen.retiretool.swr.SwrLib;
import org.minnen.retiretool.util.Writer;

public class BengenTable
{
  /** Values have valid SWR fields, queries ignore SWR field. */
  public static Map<BengenInfo, BengenInfo> bengenMap = new HashMap<>();

  public static BengenInfo get(BengenInfo bengenQuery)
  {
    return bengenMap.get(bengenQuery);
  }

  /**
   * Generate a file containing Bengen SWR result.
   * 
   * Each line in the CSV file has the form: retirement_years, percent_stock, yyyy-mm, swr
   * 
   * The SWR is an integer representing basis points, i.e. 500 = 5.0%.
   * 
   * @param file write results to this file.
   * @throws IOException
   */
  private static void generateTable(File file) throws IOException
  {
    final int[] retireYearsToCalc = new int[] { 0, 10, 20, 25, 30, 40, 50, 60, 70, 75, 80, 90, 100 };

    try (Writer writer = new Writer(file)) {
      for (int nRetireYears = 1; nRetireYears <= 50; ++nRetireYears) {
        for (int percentStock : retireYearsToCalc) {
          Sequence seq = BengenMethod.findSwrSequence(nRetireYears, percentStock);
          System.out.printf("%d, %d %s\n", nRetireYears, percentStock, seq);
          for (FeatureVec v : seq) {
            final int swr = (int) Math.round(v.get(0) * 100.0);
            BengenInfo bengen = new BengenInfo(v.getTime(), nRetireYears, percentStock, swr);
            bengenMap.put(bengen, bengen);
            writer.writeln(bengen.toCSV());
          }
        }
      }
    }
  }

  private static void loadTable(File file) throws IOException
  {
    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
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

        BengenInfo bengen = BengenInfo.fromCSV(line);
        assert SwrLib.time(SwrLib.indexForTime(bengen.time)) == bengen.time;

        bengenMap.put(bengen, bengen);
      }
    }
  }

  private static void verifyTable() throws IOException
  {
    for (BengenInfo bengen : bengenMap.values()) {
      MonthlyInfo info = SwrLib.runPeriod(bengen);
      assert info.ok();
    }
    System.out.printf("Verified entries: %d\n", bengenMap.size());
  }

  public static void main(String[] args) throws IOException
  {
    SwrLib.setup();

    File file = new File(DataIO.getOutputPath(), "bengen-table.csv");
    // generateTable(file);
    loadTable(file);
    System.out.printf("Bengen entries: %d\n", bengenMap.size());

    // verifyTable();
  }
}
