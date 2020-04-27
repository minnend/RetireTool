package org.minnen.retiretool.swr.paper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.swr.SwrLib;
import org.minnen.retiretool.swr.data.BengenTable;
import org.minnen.retiretool.util.FinLib.Inflation;
import org.minnen.retiretool.util.Writer;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

public class BengenSWRs
{
  public static void main(String[] args) throws IOException
  {
    SwrLib.setup(SwrLib.getDefaultBengenFile(), null, Inflation.Real); // DMSWR data not needed

    // Save text file with Bengen SWRs.
    File file = new File(DataIO.getFinancePath(), "bengen-swr.csv");
    try (Writer writer = new Writer(file)) {
      writer.writeln("# Bengen Safe Withdrawal Rates (MinSWR)");
      writer.writeln("# Fields:");
      writer.writeln("# 1) Retirement duration in years");
      writer.writeln("# 2) Percent stock in a stock/bond portfolio");
      writer.writeln("# 3) Safe withdrawal rate (annualized)");
      for (int retirementYears = 1; retirementYears <= 60; ++retirementYears) {
        for (int percentStock : SwrLib.percentStockList) {
          final int swr = BengenTable.getSWR(retirementYears, percentStock);
          writer.writef("%d,%d,%d\n", retirementYears, percentStock, swr);
        }
      }
    }

    // Bengen SWR for different retirement durations.
    final int percentStock = 75;
    Sequence seqBengenSWR = new Sequence("Bengen SWR");
    List<String> labels = new ArrayList<>();
    for (int retirementYears = 10; retirementYears <= 60; ++retirementYears) {
      final int swr = BengenTable.getSWR(retirementYears, percentStock);
      seqBengenSWR.addData(swr / 100.0);
      labels.add(String.format("%d", retirementYears));
      // System.out.printf("%d %d\n", retirementYears, swr);
    }

    // Generate graph showing Bengen SWRs.
    file = new File(DataIO.getOutputPath(), "bengen-swr-durations.html");
    ChartConfig config = ChartConfig.build(file, ChartConfig.Type.Bar, "Safe Withdrawl Rates for the 4% Rule",
        labels.toArray(new String[0]), null, "100%", "800px", 0, 7.5, 0.5, ChartScaling.LINEAR, ChartTiming.INDEX, 0,
        seqBengenSWR);
    config.setAxisTitles("Retirement Duration (years)", "Withdrawal Rate");
    config.setAxisLabelFontSize(28);
    config.setAxisTitleFontSize(28);
    config.setTickFormatter(null, "return this.value + '%';");
    config.setTitleConfig("margin: 0, y: 50, style: { fontSize: 42 }");
    config.setTickInterval(2, 1);
    config.setDataLabelConfig(true, -90, "#fff", 2, 1, 4, 20, false);
    config.setAnimation(false);
    Chart.saveChart(config);
  }
}
