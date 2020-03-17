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
import org.minnen.retiretool.swr.BengenMethod;
import org.minnen.retiretool.swr.SwrLib;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.util.Writer;

public class BengenTable
{
  /** Values have valid SWR fields, queries ignore SWR field. */
  public static Map<BengenEntry, BengenEntry> bengenMap       = new HashMap<>();

  /** Values hold sequences with all Bengen SWRs for a given retirement duration and stock percentage. */
  public static Map<BengenEntry, Sequence>    bengenSequences = new HashMap<>();

  /** Values are SWR for the given retirement duration and stock percentage. */
  public static Map<BengenEntry, Integer>     bengenSWRs      = new HashMap<>();

  public static BengenEntry get(int retirementYears, int percentStock, long time)
  {
    BengenEntry key = new BengenEntry(time, retirementYears, percentStock);
    return bengenMap.getOrDefault(key, null);
  }

  public static Sequence getSeq(int retirementYears, int percentStock)
  {
    BengenEntry key = new BengenEntry(retirementYears, percentStock);
    return bengenSequences.getOrDefault(key, null);
  }

  public static int getSWR(int retirementYears, int percentStock)
  {
    BengenEntry key = new BengenEntry(retirementYears, percentStock);
    return bengenSWRs.getOrDefault(key, null);
  }

  public static void clear()
  {
    bengenMap.clear();
    bengenSequences.clear();
    bengenSWRs.clear();
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
    clear();
    final int[] percentStockList = new int[] { 0, 10, 20, 25, 30, 40, 50, 60, 70, 75, 80, 90, 100 };

    try (Writer writer = new Writer(file)) {
      writer.writeln("# Bengen safe withdrawal rates (SWR).");
      writer.writeln("# Withdrawal rates are annual, and it is assumed that the monthly withdrawal rate is SWR/12.0.");
      writer.writeln("# Fields:");
      writer.writeln("# 1) retirement duration in years");
      writer.writeln("# 2) percent stock");
      writer.writeln("# 3) retirement month");
      writer.writeln("# 4) safe withdrawal rate in basis points (500=5.0%)");

      for (int nRetireYears = 1; nRetireYears <= 60; ++nRetireYears) {
        final long a = TimeLib.getTime();
        for (int percentStock : percentStockList) {
          Sequence seq = BengenMethod.findSwrSequence(nRetireYears, percentStock);
          int swr = (int) Math.round(seq.getMin().get(0) * 100);
          System.out.printf("%d, %3d [%s] -> %d\n", nRetireYears, percentStock, TimeLib.formatYM(seq.getEndMS()), swr);
          for (FeatureVec v : seq) {
            swr = (int) Math.round(v.get(0) * 100.0);
            BengenEntry bengen = new BengenEntry(v.getTime(), nRetireYears, percentStock, swr);
            bengenMap.put(bengen, bengen);
            writer.writeln(bengen.toCSV());
          }
        }
        final long b = TimeLib.getTime();
        System.out.printf("%d years -> %d ms\n", nRetireYears, b - a);
      }
    }
  }

  public static void loadTable(File file) throws IOException
  {
    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      BengenEntry bengenKey = null;
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

        BengenEntry info = BengenEntry.fromCSV(line);
        assert SwrLib.time(SwrLib.indexForTime(info.time)) == info.time;

        bengenMap.put(info, info);

        // Create new sequence when retirement scenario changes.
        if (bengenKey == null || info.retirementYears != bengenKey.retirementYears
            || info.percentStock != bengenKey.percentStock) {
          if (seq != null) { // store previous sequence and SWR
            bengenSequences.put(bengenKey, seq);
            final int swr = (int) Math.round(seq.getMin().get(0));
            bengenSWRs.put(bengenKey, swr);
          }

          bengenKey = new BengenEntry(info.retirementYears, info.percentStock);
          seq = new Sequence(String.format("Bengen (%d, %d)", info.retirementYears, info.percentStock));
        }

        // Add new month to sequence.
        assert seq.isEmpty() || info.time > seq.getEndMS();
        seq.addData(info.swr, info.time);
      }
      if (seq != null) { // store last sequence and final SWR
        bengenSequences.put(bengenKey, seq);
        final int swr = (int) Math.round(seq.getMin().get(0));
        bengenSWRs.put(bengenKey, swr);
      }
    }
  }

  private static void verifyTable() throws IOException
  {
    for (BengenEntry bengen : bengenMap.values()) {
      MonthlyInfo info = SwrLib.runPeriod(bengen);
      assert info.ok() : bengen;
    }
    System.out.printf("Verified entries: %d\n", bengenMap.size());
  }

  public static void main(String[] args) throws IOException
  {
    final String mode = "verify";

    if (mode.equals("generate")) {
      SwrLib.setup(null, null); // don't load bengen or dmswr table
      File file = new File(DataIO.getFinancePath(), "bengen-table.csv");
      generateTable(file);
    } else {
      SwrLib.setup(SwrLib.getDefaultBengenFile(), null); // only load bengen table
      System.out.printf("Bengen entries: %d\n", bengenMap.size());
      System.out.printf("Bengen sequences: %d\n", bengenSequences.size());
      verifyTable();
    }
  }
}
