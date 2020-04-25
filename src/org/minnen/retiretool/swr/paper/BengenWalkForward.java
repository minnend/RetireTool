package org.minnen.retiretool.swr.paper;

import java.io.File;
import java.io.IOException;
import java.time.Month;
import java.util.Map;
import java.util.TreeMap;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.swr.SwrLib;
import org.minnen.retiretool.swr.data.BengenEntry;
import org.minnen.retiretool.swr.data.BengenTable;
import org.minnen.retiretool.util.FinLib.Inflation;
import org.minnen.retiretool.viz.Chart;
import org.minnen.retiretool.viz.ChartConfig;
import org.minnen.retiretool.viz.ChartConfig.ChartScaling;
import org.minnen.retiretool.viz.ChartConfig.ChartTiming;
import org.minnen.retiretool.util.TimeLib;

public class BengenWalkForward
{
  public static void main(String[] args) throws IOException
  {
    SwrLib.setup(SwrLib.getDefaultBengenFile(), null, Inflation.Real);

    final int retirementYears = 30;
    final int percentStock = 75;

    Map<Integer, Integer> minSwrMap = new TreeMap<>();
    for (int i = 1; i <= retirementYears; ++i) {
      minSwrMap.put(i, 10000);
    }

    Sequence seqTrueSWR = new Sequence("MinSWR (True)");
    Sequence seqEstimatedSWR = new Sequence("MinSWR (Point-in-Time)");
    Sequence seqCBSWR = new Sequence("CBSWR");
    for (int iEnd = 0; iEnd < SwrLib.length(); ++iEnd) {
      final long endTime = SwrLib.time(iEnd);

      BengenEntry bengen = BengenTable.get(endTime, retirementYears, percentStock);
      if (bengen != null) {
        seqCBSWR.addData(bengen.swr / 100.0, endTime);
      } else {
        final int maxYears = (int) Math.floor((SwrLib.length() - iEnd) / 12.0);
        if (maxYears >= 20) {
          bengen = BengenTable.get(endTime, maxYears, percentStock);
          seqCBSWR.addData(bengen.swr / 100.0, endTime);
        }
      }

      for (int years = 1; years <= retirementYears; ++years) {
        int iStart = iEnd - years * 12;
        if (iStart < 0) continue; // not enough history
        final long startTime = SwrLib.time(iStart);

        int swr = BengenTable.get(startTime, years, percentStock).swr;
        assert swr > 0 && swr <= 10000;

        // Ensure SWR is never higher than SWR for shorter retirement.
        if (years > 1) {
          swr = Math.min(swr, minSwrMap.get(years - 1));
        }

        if (swr < minSwrMap.get(years)) {
          if (years == retirementYears) {
            System.out.printf("[%s -> %d] %d -> %d\n", TimeLib.formatMonth(startTime),
                TimeLib.ms2date(endTime).getYear(), minSwrMap.get(years), swr);
          }
          minSwrMap.put(years, swr);

          // Forward update to ensure no duration inversions.
          for (int longerYears = years + 1; longerYears <= retirementYears; ++longerYears) {
            if (swr < minSwrMap.get(longerYears)) {
              if (longerYears == retirementYears) {
                System.out.printf("[%s -> %d] %d -> %d  |update from %d years\n", TimeLib.formatMonth(startTime),
                    TimeLib.ms2date(endTime).getYear(), minSwrMap.get(longerYears), swr, years);
              }
              minSwrMap.put(longerYears, swr);
            }
          }
        }
      }

      final int iStart = iEnd - retirementYears * 12;
      if (iStart >= 0) {
        final long startTime = SwrLib.time(iStart);
        seqTrueSWR.addData(minSwrMap.get(retirementYears) / 100.0, startTime);
      }

      seqEstimatedSWR.addData(minSwrMap.get(retirementYears) / 100.0, endTime);
    }

    // Don't plot early years because there's very little data to support them.
    final long startGraphTime = TimeLib.toMs(1920, Month.JANUARY, 1);
    seqTrueSWR = seqTrueSWR.subseq(seqTrueSWR.getClosestIndex(startGraphTime));
    seqEstimatedSWR = seqEstimatedSWR.subseq(seqEstimatedSWR.getClosestIndex(startGraphTime));
    seqCBSWR = seqCBSWR.subseq(seqCBSWR.getClosestIndex(startGraphTime));

    // Collect failure rate stats.
    assert seqCBSWR.getStartMS() == seqEstimatedSWR.getStartMS();
    int nFail = 0;
    int nWin = 0;
    for (int i = 0; i < seqCBSWR.length(); ++i) {
      int x = (int) Math.round(seqCBSWR.get(i, 0) * 100.0);
      int y = (int) Math.round(seqEstimatedSWR.get(i, 0) * 100.0);
      if (x < y) {
        ++nFail;
        System.out.printf("[%s]: %d vs. %d = %d\n", TimeLib.formatMonth(seqCBSWR.getTimeMS(i)), x, y, y - x);
      } else {
        ++nWin;
      }
    }
    int n = nWin + nFail;
    System.out.printf("fail=%d (%.2f%%)  win=%d (%.2f%%)\n", nFail, 100.0 * nFail / n, nWin, 100.0 * nWin / n);

    File file = new File(DataIO.getOutputPath(), "bengen-swr-walk-forward.html");
    ChartConfig config = ChartConfig.build(file, ChartConfig.Type.Area, "SWR Walk-Forward Optimization", null, null,
        "100%", "800px", 3.0, 8.0, 0.25, ChartScaling.LINEAR, ChartTiming.MONTHLY, 0, seqCBSWR, seqEstimatedSWR,
        seqTrueSWR);
    config.setAxisLabelFontSize(28);
    config.setAxisTitleFontSize(28);
    config.setLineWidth(3);
    config.setFillOpacity(0.35);
    config.setTickFormatter(null, "return this.value + '%';");
    config.setTitleConfig("margin: 0, y: 50, style: { fontSize: 36 }");
    config.setTickInterval(24, 1);
    config.setTickFormatter("return this.value.split(' ')[1];", "return this.value + '%';");
    // config.setDataLabelConfig(true, -90, "#fff", 2, 1, 4, 20, false);
    config.setLegendConfig(
        "align: 'right', verticalAlign: 'top', x: -20, y: 70, layout: 'vertical', floating: true, itemStyle: {"
            + "fontSize: 24, }, backgroundColor: '#fff', borderWidth: 1, padding: 12, itemMarginTop: 8, "
            + "itemMarginBottom: 0, shadow: true, symbolWidth: 32,");
    config.setAnimation(false);
    Chart.saveChart(config);
  }
}
