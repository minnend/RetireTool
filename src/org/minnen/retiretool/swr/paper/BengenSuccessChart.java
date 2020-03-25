package org.minnen.retiretool.swr.paper;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.swr.BengenMethod;
import org.minnen.retiretool.swr.SwrLib;
import org.minnen.retiretool.util.Writer;
import org.minnen.retiretool.viz.Colors;
import org.minnen.retiretool.util.FinLib.Inflation;

public class BengenSuccessChart
{
  public static void createSuccessChart(File file, int[] retirementYearsList, int[] percentStockList,
      int[] withdrawalRateList) throws IOException
  {
    String[] colors = Colors.buildRedToGreenColorMap();

    StringWriter sw = new StringWriter(8192);
    try (Writer writer = new Writer(sw)) {
      writer.write("<html><head>\n");
      writer.write("<title>Bengen Success Chart</title>\n");
      writer.write("<link rel='stylesheet' href='css/success-chart.css'>\n");
      writer.write("</head>\n");

      writer.write("<body>\n");
      writer.write("<table class='success-chart'>\n");

      writer.write("<tr>");
      writer.write("<th class='thick-right' colspan=2 rowspan=2></th>");
      writer.writef("<th colspan=%d>Annual Withdrawal Rate</th>", withdrawalRateList.length);
      writer.write("</tr>\n");

      writer.write("<tr>");
      for (int withdrawalRate : withdrawalRateList) {
        writer.writef("<th>%.2f%%</th>", withdrawalRate / 100.0);
      }
      writer.write("</tr>\n");

      for (int iPercent = 0; iPercent < percentStockList.length; ++iPercent) {
        final int percentStock = percentStockList[iPercent];
        writer.writef("<tr class='thick-top'><th rowspan=%d>%d%%<br/>Stock</th>\n", retirementYearsList.length,
            percentStock);
        for (int iYears = 0; iYears < retirementYearsList.length; ++iYears) {
          if (iYears > 0) writer.write("<tr>");
          final int retirementYears = retirementYearsList[iYears];
          final int n = SwrLib.lastIndex(retirementYears) + 1;
          writer.writef("<th class='thick-right'>%d Years</th>", retirementYears);
          for (int withdrawalRate : withdrawalRateList) {
            final int nWin = BengenMethod.getSuccessFail(withdrawalRate, retirementYears, percentStock).first;
            int percent = (int) Math.round(nWin * 100.0 / n);
            if (percent == 100 && nWin < n) percent = 99; // don't round to 100%
            writer.writef("<td style='background-color: %s'>%d%%</td>", colors[percent], percent);
          }
          writer.write("</tr>\n");
        }
      }

      writer.write("</table>\n");
      writer.write("</body></html>\n");
    }

    // Dump string to file.
    try (Writer writer = new Writer(file)) {
      writer.write(sw.toString());
    }
  }

  public static void main(String[] args) throws IOException
  {
    SwrLib.setup(SwrLib.getDefaultBengenFile(), null, Inflation.Real); // DMSWR data not needed

    File file = new File(DataIO.getOutputPath(), "bengen-success-chart.html");
    int[] durations = new int[] { 30, 40, 50 };
    int[] percentStockList = new int[] { 100, 75, 50, 25, 0 };
    int[] withdrawalRates = new int[] { 300, 325, 350, 375, 400, 425, 450, 475, 500 };
    createSuccessChart(file, durations, percentStockList, withdrawalRates);
  }
}
