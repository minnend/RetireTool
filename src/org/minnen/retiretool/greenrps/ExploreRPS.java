package org.minnen.retiretool.greenrps;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Month;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.FeatureVec;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

public class ExploreRPS
{
  private final Map<String, Integer> name2index = new HashMap<>();
  private String[]                   signalNames;
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
        signalNames = new String[toks.length - 1];
        signals = new Sequence[toks.length - 1];
        for (int i = 1; i < toks.length; ++i) {
          String name = toks[i].trim();
          signalNames[i - 1] = name;
          name2index.put(name, i - 1);
          signals[i - 1] = new Sequence(name);
        }
        continue;
      }

      assert toks.length == signalNames.length + 1;
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

  /** Invert the given signal; Equivalent to going long the bottom decile and shorting the top decile. */
  private void calcCumulativeReturns(int iSignal, int dir)
  {
    assert dir == 1 || dir == -1;
    Sequence seq = signals[iSignal];
    for (int i = 0; i < signalData.length(); ++i) {
      double r = dir * signalData.get(i, iSignal);
      double value = seq.get(i, 0) * FinLib.ret2mul(r);
      seq.set(i + 1, 0, value);
    }
  }

  /** Invert any signal that loses money. */
  private void flipLosers()
  {
    assert signals.length == signalData.getNumDims();
    for (int iSignal = 0; iSignal < signals.length; ++iSignal) {
      if (signals[iSignal].getLast(0) >= 1.0) continue;
      calcCumulativeReturns(iSignal, -1);
    }
  }

  public void analyze(File outputDir) throws IOException
  {
    System.out.printf("Length: %d\n", signalData.length());
    double nMonths = TimeLib.monthsBetween(signals[0].getStartMS(), signals[0].getEndMS());
    System.out.printf("Months: %.1f\n", nMonths);

    ChartConfig chartConfig = new ChartConfig(new File(outputDir, "rps-raw.html")).setType(ChartConfig.Type.Line)
        .setTitle("Return Predictive Signals").setSize(1200, 800).setLogarthimicYAxis(true)
        .setTiming(ChartTiming.MONTHLY).setData(signals);
    Chart.saveChart(chartConfig);

    flipLosers();
    chartConfig.setFile(new File(outputDir, "rps-positive.html"));
    Chart.saveChart(chartConfig);

    int iSignal = name2index.get("roavol");
    calcCumulativeReturns(iSignal, 1);
    Sequence seqA = signals[iSignal].dup();
    calcCumulativeReturns(iSignal, -1);
    Sequence seqB = signals[iSignal].dup();
    chartConfig.setFile(new File(outputDir, signalNames[iSignal] + ".html")).setData(seqA, seqB);
    Chart.saveChart(chartConfig);

  }

  public static void main(String[] args) throws IOException
  {
    File dataDir = new File(DataIO.getFinancePath(), "quant_strategy_materials");
    assert dataDir.isDirectory();
    File outputDir = DataIO.getOutputPath();
    assert dataDir.isDirectory();

    ExploreRPS explore = new ExploreRPS();
    explore.loadGreenData(new File(dataDir, "Returns_-_Equal_Weighted.csv"));
    explore.analyze(outputDir);
  }
}
