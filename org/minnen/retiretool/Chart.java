package org.minnen.retiretool;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Chart
{
  public static void saveLineChart(File file, String title, int width, int height, boolean logarithmic,
      Sequence... seqs) throws IOException
  {
    saveLineHighChart(file, title, width, height, logarithmic, seqs);
  }

  public static void saveLineGoogleChart(File file, String title, int width, int height, boolean logarithmic,
      Sequence... seqs) throws IOException
  {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      writer.write("<html><head><script type=\"text/javascript\"\n");
      writer
          .write(" src=\"https://www.google.com/jsapi?autoload={ 'modules':[{ 'name':'visualization', 'version':'1', 'packages':['corechart'] }] }\"></script>\n");
      writer.write("  <script type=\"text/javascript\">\n");
      writer.write("  google.setOnLoadCallback(drawChart);\n");
      writer.write("   function drawChart() {\n");
      writer.write("    var data = google.visualization.arrayToDataTable([\n");
      writer.write("     ['Date', ");
      for (int i = 0; i < seqs.length; ++i) {
        writer.write("'" + seqs[i].getName() + "'");
        if (i < seqs.length - 1) {
          writer.write(", ");
        }
      }
      writer.write("],\n");
      int dt = 1;
      for (int t = 0; t < seqs[0].length(); t += dt) {
        writer.write("     ['" + Library.formatMonth(seqs[0].getTimeMS(t)) + "', ");
        for (int i = 0; i < seqs.length; ++i) {
          writer.write(String.format("%.2f%s", seqs[i].get(t, 0), i == seqs.length - 1 ? "" : ", "));
        }
        writer.write(t + dt >= seqs[0].length() ? "]\n" : "],\n");
      }
      writer.write("    ]);\n");
      writer.write("    var options = {\n");
      writer.write("     title: '" + title + "',\n");
      writer.write("     legend: { position: 'right' },\n");
      writer.write("     vAxis: {\n");
      writer.write("      logScale: " + (logarithmic ? "true" : "false") + "\n");
      writer.write("     },\n");
      writer.write("     chartArea: {\n");
      writer.write("      left: 100,\n");
      writer.write("      width: \"75%\",\n");
      writer.write("      height: \"80%\"\n");
      writer.write("     }\n");
      writer.write("    };\n");
      writer.write("    var chart = new google.visualization.LineChart(document.getElementById('chart'));\n");
      writer.write("    chart.draw(data, options);\n");
      writer.write("  }\n");
      writer.write("</script></head><body>\n");
      writer.write("<div id=\"chart\" style=\"width: " + width + "px; height: " + height + "px\"></div>\n");
      writer.write("</body></html>\n");
    }
  }

  public static void saveLineHighChart(File file, String title, int width, int height, boolean logarithmic,
      Sequence... seqs) throws IOException
  {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      writer.write("<html><head>\n");
      writer.write("<script src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.8.2/jquery.min.js\"></script>\n");
      writer.write("<script src=\"js/highcharts.js\"></script>\n");
      writer.write("  <script type=\"text/javascript\">\n");

      writer.write("$(function () {\n");
      writer.write(" $('#chart').highcharts({\n");
      writer.write("  title: { text: '" + title + "' },\n");
      writer.write("  xAxis: { categories: [");
      for (int i = 0; i < seqs[0].size(); ++i) {
        writer.write("'" + Library.formatMonth(seqs[0].getTimeMS(i)) + "'");
        if (i < seqs[0].size() - 1) {
          writer.write(",");
        }
      }
      writer.write("] },\n");

      writer.write("  yAxis: {\n");
      if (logarithmic) {
        writer.write("   type: 'logarithmic',\n");
        writer.write("   minorTickInterval: 0.5,\n");
      }
      writer.write("   title: { text: null }\n");
      writer.write("  },\n");

      writer.write("  series: [\n");
      for (int i = 0; i < seqs.length; ++i) {
        Sequence seq = seqs[i];
        writer.write("  { name: '" + seq.getName() + "',\n");
        writer.write("    data: [");
        for (int t = 0; t < seqs[0].length(); ++t) {
          writer.write(String.format("%.2f%s", seqs[i].get(t, 0), t == seqs[i].size() - 1 ? "" : ", "));
        }
        writer.write("] }");
        if (i < seqs.length - 1) {
          writer.write(',');
        }
        writer.write("\n");
      }
      writer.write("  ]\n");
      writer.write(" });\n");
      writer.write("});\n");

      writer.write("</script></head><body style=\"width:" + width + "px;\">\n");
      writer.write("<div id=\"chart\" style=\"width:100%; height:" + height + "px;\" />\n");
      writer.write("</body></html>\n");
    }
  }
}
