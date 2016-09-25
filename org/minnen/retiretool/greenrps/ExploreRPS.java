package org.minnen.retiretool.greenrps;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Month;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;

public class ExploreRPS
{
  private final Map<String, Integer> name2index = new HashMap<>();
  private String[]                   columnNames;
  private Sequence                   signalData = new Sequence("RPS");
  private Sequence[]                 signals;

  public void loadGreenData(File file) throws IOException
  {
    if (!file.canRead()) {
      throw new IOException(String.format("Can't read RPS file (%s)", file.getPath()));
    }
    System.out.printf("Loading Green RPS data file: [%s]\n", file.getPath());
    BufferedReader in = new BufferedReader(new FileReader(file));

    String line;
    int nLines = 0;
    while ((line = in.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty()) continue;
      ++nLines;
      String[] toks = line.trim().split(",+");

      if (nLines == 1) { // Load header
        assert toks[0].equals("date");
        columnNames = new String[toks.length - 1];
        signals = new Sequence[toks.length - 1];
        for (int i = 1; i < toks.length; ++i) {
          String name = toks[i].trim();
          columnNames[i - 1] = name;
          name2index.put(name, i - 1);
          signals[i - 1] = new Sequence(name);
        }
        continue;
      }

      assert toks.length == columnNames.length + 1;
      int date = Integer.parseInt(toks[0]);
      int year = date / 10000;
      int month = (date / 100) % 100;
      int day = date % 100;
      long ms = TimeLib.toMs(year, month, day);
      FeatureVec v = new FeatureVec(toks.length - 1);
      for (int i = 0; i < v.getNumDims(); ++i) {
        // Provided returns are fractions (1.0% = 0.01) so convert to percentages (1.0% = 1.0).
        double percentReturn = Double.parseDouble(toks[i + 1]) * 100.0; 
        v.set(i, percentReturn);
      }
      signalData.addData(v, ms);
    }
    in.close();

    // Setup cumulative returns per signal.
    long startTime = TimeLib.toMs(TimeLib.ms2date(signalData.getStartMS()).withDayOfMonth(1));
    for (int i = 0; i < signals.length; ++i) {
      signals[i].addData(1.0, startTime);
    }
    for (FeatureVec v : signalData) {
      assert v.getNumDims() == signals.length;
      for (int i = 0; i < v.getNumDims(); ++i) {
        double value = signals[i].getLast(0) * FinLib.ret2mul(v.get(i));
        signals[i].addData(value, v.getTime());
      }
    }
  }

  public void analyze(File outputDir) throws IOException
  {
    System.out.printf("%d\n", signalData.length());
    Chart.saveLineChart(new File(outputDir, "rps.html"), "Return Predictive Signals", 1200, 800, true, true, signals);
  }

  public static void main(String[] args) throws IOException
  {
    File dataDir = new File("g:/research/finance/quant_strategy_materials");
    assert dataDir.isDirectory();
    File outputDir = new File("g:/web");
    assert dataDir.isDirectory();

    ExploreRPS explore = new ExploreRPS();
    explore.loadGreenData(new File(dataDir, "Returns_-_Equal_Weighted.csv"));
    explore.analyze(outputDir);
  }
}
