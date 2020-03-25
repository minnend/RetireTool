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
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;

public class BengenSWRs
{
  public static void main(String[] args) throws IOException
  {
    SwrLib.setup(SwrLib.getDefaultBengenFile(), null, Inflation.Real); // DMSWR data not needed

    // Bengen SWR for different retirement durations.
    Sequence seqBengenSWR = new Sequence("Bengen SWR");
    List<String> labels = new ArrayList<>();
    for (int retirementYears = 10; retirementYears <= 60; ++retirementYears) {
      final int swr = BengenTable.getSWR(retirementYears, 75);
      seqBengenSWR.addData(swr / 100.0);
      labels.add(String.format("%d", retirementYears));
      // System.out.printf("%d %d\n", retirementYears, swr);
    }
    File file = new File(DataIO.getOutputPath(), "bengen-swr-durations.html");
    ChartConfig config = ChartConfig.build(file, ChartConfig.Type.Bar, "Bengen SWR", labels.toArray(new String[0]),
        null, "100%", "500px", 0, 8, 0.5, ChartScaling.LINEAR, ChartTiming.INDEX, 0, seqBengenSWR);
    config.setAxisLabelFontSize(24);
    config.setTickFormatter(null, "return this.value + '%';");
    config.setTitleConfig("margin: 0, y: 32, style: { fontSize: 32 }");
    config.setTickInterval(2, 1);
    config.setDataLabelConfig(true, -90, "#fff", 2, 1, 4, 16, false);
    config.setAnimation(false);
    Chart.saveChart(config);
  }
}
