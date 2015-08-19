package org.minnen.retiretool;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Chart
{
  public enum ChartType {
    Line, Bar, Area
  };

  public static String chart2name(ChartType chartType)
  {
    if (chartType == ChartType.Line) {
      return "line";
    } else if (chartType == ChartType.Bar) {
      return "column";
    } else if (chartType == ChartType.Area) {
      return "area";
    } else {
      return "ERROR";
    }
  }

  public static void saveLineChart(File file, String title, int width, int height, boolean logarithmic,
      Sequence... seqs) throws IOException
  {
    saveHighChart(file, ChartType.Line, title, null, null, width, height, Double.NaN, Double.NaN, logarithmic ? 0.5
        : Double.NaN, logarithmic, 0, seqs);
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

  public static void saveHighChart(File file, ChartType chartType, String title, String[] labels, String[] colors,
      int width, int height, double ymin, double ymax, double minorTickInterval, boolean logarithmic, int dim,
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
      if (chartType != ChartType.Line) {
        writer.write(String.format("  chart: { type: '%s' },\n", chart2name(chartType)));
      }
      writer.write("  xAxis: { categories: [");
      if (labels != null) {
        assert labels.length == seqs[0].size();
        for (int i = 0; i < labels.length; ++i) {
          writer.write("'" + labels[i] + "'");
          if (i < labels.length - 1) {
            writer.write(",");
          }
        }
      } else {
        for (int i = 0; i < seqs[0].size(); ++i) {
          writer.write("'" + Library.formatMonth(seqs[0].getTimeMS(i)) + "'");
          if (i < seqs[0].size() - 1) {
            writer.write(",");
          }
        }
      }
      writer.write("] },\n");

      writer.write("  yAxis: {\n");
      if (logarithmic) {
        writer.write("   type: 'logarithmic',\n");
      }
      if (!Double.isNaN(minorTickInterval)) {
        writer.write(String.format("   minorTickInterval: %.3f,\n", minorTickInterval));
      }
      if (!Double.isNaN(ymin)) {
        writer.write(String.format("   min: %.3f,\n", ymin));
      }
      if (!Double.isNaN(ymax)) {
        writer.write(String.format("   max: %.3f,\n", ymax));
      }
      writer.write("   title: { text: null }\n");
      writer.write("  },\n");

      if (chartType == ChartType.Line) {
        writer.write("  chart: {\n");
        writer.write("   zoomType: 'x'\n");
        writer.write("  },\n");
      }

      if (colors != null) {
        writer.write("  colors: [");
        for (int i = 0; i < colors.length; ++i) {
          writer.write(String.format("'%s'", colors[i]));
          if (i < colors.length - 1) {
            writer.write(',');
          }
        }
        writer.write("],\n");
      }

      writer.write("  plotOptions: {\n");
      if (chartType == ChartType.Bar) {
        writer.write("   column: {\n");
        if (colors != null) {
          writer.write("    colorByPoint: true,\n");
        }
        writer.write("    pointPadding: 0,\n");
        writer.write("    groupPadding: 0.1,\n");
        writer.write("    borderWidth: 0\n");
        writer.write("   }\n");
      } else if (chartType == ChartType.Area) {
        writer.write("   area: {\n");
        writer.write("    fillColor: {\n");
        writer.write("      linearGradient: { x1: 0, y1: 0, x2: 0, y2: 1 },\n");
        writer.write("      stops: [\n");
        writer.write("        [0, Highcharts.getOptions().colors[0]],\n");
        writer.write("        [1, Highcharts.Color(Highcharts.getOptions().colors[0]).setOpacity(0).get('rgba')]\n");
        writer.write("      ]},\n");
        writer.write("    marker: { radius: 2 },\n");
        writer.write("    lineWidth: 1,\n");
        writer.write("    states: {\n");
        writer.write("      hover: { lineWidth: 1 }\n");
        writer.write("    },\n");
        // writer.write("    threshold: null\n");
        writer.write("   }\n");
      }
      writer.write("  },\n");

      writer.write("  series: [\n");
      for (int i = 0; i < seqs.length; ++i) {
        Sequence seq = seqs[i];
        writer.write("  { name: '" + seq.getName() + "',\n");
        writer.write("    data: [");
        for (int t = 0; t < seqs[0].length(); ++t) {
          writer.write(String.format("%.4f%s", seqs[i].get(t, dim), t == seqs[i].size() - 1 ? "" : ", "));
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

  public static void saveHighChartScatter(File file, String title, int width, int height, int dim, Sequence returns1,
      Sequence returns2) throws IOException
  {
    assert returns1.length() == returns2.length();

    // Split return pairs into above / below y=x line.
    Sequence above = new Sequence();
    Sequence below = new Sequence();
    for (int i = 0; i < returns1.length(); ++i) {
      double x = returns1.get(i, dim);
      double y = returns2.get(i, dim);
      FeatureVec v = new FeatureVec(2, x, y);
      if (x >= y) {
        below.addData(v);
      } else {
        above.addData(v);
      }
    }

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      writer.write("<html><head>\n");
      writer.write("<script src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.8.2/jquery.min.js\"></script>\n");
      writer.write("<script src=\"js/highcharts.js\"></script>\n");
      writer.write("  <script type=\"text/javascript\">\n");
      writer.write("$(function () {\n");
      writer.write(" $('#chart').highcharts({\n");
      writer.write("  title: { text: '" + title + "' },\n");
      writer.write("  chart: {\n");
      writer.write("   type: 'scatter',\n");
      writer.write("   zoomType: 'xy'\n");
      writer.write("  },\n");
      writer.write("  xAxis: {\n");
      writer.write("   title: {\n");
      writer.write("    enabled: true,\n");
      writer.write("    text: '" + returns1.getName() + "'\n");
      writer.write("   }\n");
      writer.write("  },\n");
      writer.write("  yAxis: {\n");
      writer.write("   title: { text: '" + returns2.getName() + "' }\n");
      writer.write("  },\n");
      writer.write("  legend: { enabled: false },\n");
      writer.write("  plotOptions: {\n");
      writer.write("   scatter: {\n");
      writer.write("    marker: { radius: 3 },\n");
      writer.write("    tooltip: {\n");
      writer.write("     headerFormat: '',\n");
      writer.write("     pointFromat: '{point.x}% vs. {point.y}%'\n");
      writer.write("    }\n");
      writer.write("   }\n");
      writer.write("  },\n");
      writer.write("  series: [{\n");
      writer.write("   name: 'Returns (above)',\n");
      writer.write("   color: 'rgba(83, 223, 83, 0.5)',\n");
      writer.write("   data: [");
      for (int i = 0; i < above.length(); ++i) {
        double x = above.get(i, 0);
        double y = above.get(i, 1);
        writer.write(String.format("[%.3f,%.3f]", x, y));
        if (i < above.length() - 1) {
          writer.write(",");
        }
      }
      writer.write("]}, {\n");
      writer.write("   name: 'Returns (below)',\n");
      writer.write("   color: 'rgba(223, 83, 83, 0.5)',\n");
      writer.write("   data: [");
      for (int i = 0; i < below.length(); ++i) {
        double x = below.get(i, 0);
        double y = below.get(i, 1);
        writer.write(String.format("[%.3f,%.3f]", x, y));
        if (i < below.length() - 1) {
          writer.write(",");
        }
      }
      writer.write("  ]}]\n");
      writer.write(" });\n");
      writer.write("});\n");

      writer.write("</script></head><body style=\"width:" + width + "px;\">\n");
      writer.write("<div id=\"chart\" style=\"width:100%; height:" + height + "px;\" />\n");
      writer.write("</body></html>\n");
    }
  }

  /**
   * Generate an HTML file with a sortable table of strategy statistics.
   * 
   * Documentation for sortable table: http://tablesorter.com/docs/
   * 
   * @param file save HTML in this file
   * @param strategyStats List of strategies to include
   * @throws IOException if there is a problem writing to the file
   */
  public static void saveStatsTable(File file, InvestmentStats... strategyStats) throws IOException
  {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      writer.write("<html><head>\n");
      writer.write("<title>Strategy Statistics</title>\n");
      writer.write("<script src=\"http://code.jquery.com/jquery.min.js\"></script>\n");
      writer.write("<script type=\"text/javascript\" src=\"js/jquery.tablesorter.min.js\"></script>\n");
      writer.write("<script type=\"text/javascript\">\n");
      writer
          .write(" $(document).ready(function() { $(\"#myTable\").tablesorter( {sortList: [[11,1]], widgets: ['zebra']} ); } );\n");
      writer.write("</script>\n");
      writer
          .write("<link rel=\"stylesheet\" href=\"themes/blue/style.css\" type=\"text/css\" media=\"print, projection, screen\" />\n");
      writer.write("</head><body style=\"width:1200px\">\n");
      writer.write("<h2>Statistics for Different Strategies / Assets from 1871 to 2015</h2>");
      writer.write("<table id=\"myTable\" class=\"tablesorter\">\n");
      writer.write("<thead><tr>\n");
      writer.write(" <th>Strategy / Asset</th>\n");
      writer.write(" <th>CAGR</th>\n");
      writer.write(" <th>StdDev</th>\n");
      writer.write(" <th>Drawdown</th>\n");
      writer.write(" <th>Down 10%</th>\n");
      writer.write(" <th>New High %</th>\n");
      writer.write(" <th>Worst AR</th>\n");
      writer.write(" <th>25% AR</th>\n");
      writer.write(" <th>Median AR</th>\n");
      writer.write(" <th>75% AR</th>\n");
      writer.write(" <th>Best AR</th>\n");
      writer.write(" <th>Score</th>\n");
      writer.write("</tr></thead>\n");
      writer.write("<tbody>\n");
      for (InvestmentStats stats : strategyStats) {
        writer.write("<tr>\n");
        String name = stats.name();
        Pattern p = Pattern.compile("(.+)\\s+\\(\\d.*\\)$");
        Matcher m = p.matcher(name);
        if (m.matches()) {
          name = m.group(1);
        }
        writer.write(String.format("<td><b>%s</b></td>\n", name));
        writer.write(String.format("<td>%.2f</td>\n", stats.cagr));
        writer.write(String.format("<td>%.2f</td>\n", stats.devAnnualReturn));
        writer.write(String.format("<td>%.2f</td>\n", stats.maxDrawdown));
        writer.write(String.format("<td>%.2f</td>\n", stats.percentDown10));
        writer.write(String.format("<td>%.2f</td>\n", stats.percentNewHigh));
        writer.write(String.format("<td>%.2f</td>\n", stats.annualPercentiles[0]));
        writer.write(String.format("<td>%.2f</td>\n", stats.annualPercentiles[1]));
        writer.write(String.format("<td>%.2f</td>\n", stats.annualPercentiles[2]));
        writer.write(String.format("<td>%.2f</td>\n", stats.annualPercentiles[3]));
        writer.write(String.format("<td>%.2f</td>\n", stats.annualPercentiles[4]));
        writer.write(String.format("<td>%.2f</td>\n", stats.calcScore()));
        writer.write("</tr>\n");
      }
      writer.write("</tbody>\n</table>\n");
      writer.write("<h2>Explanation of Each Column</h2>");
      writer.write("<div><ul>");
      writer.write(" <li><b>Strategy / Asset</b> - Name of the strategy or asset class</li>\n");
      writer.write(" <li><b>CAGR</b> - Compound Annual Growth Rate</li>\n");
      writer.write(" <li><b>StdDev</b> - Standard deviation of annual returns</li>\n");
      writer.write(" <li><b>Drawdown</b> - Maximum drawdown (largest gap from peak to trough)</li>\n");
      writer
          .write(" <li><b>Down 10%</b> - Percentage of time (months) strategy is 10% or more below the previous peak</li>\n");
      writer.write(" <li><b>New High %</b> - Percentage of time (months) strategy hits a new high</li>\n");
      writer.write(" <li><b>Worst AR</b> - Return of worst year (biggest drop)</li>\n");
      writer.write(" <li><b>25% AR</b> - Return of year at the 25th percentile</li>\n");
      writer.write(" <li><b>Median AR</b> - Median annual return (50th percentile)</li>\n");
      writer.write(" <li><b>75% AR</b> - Return of year at the 75th percentile</li>\n");
      writer.write(" <li><b>Best AR</b> - Return of best year (biggest gain)</li>\n");
      writer
          .write(" <li><b>Score</b> - Semi-arbitrary combination of statistics used to rank strategies (higher is better)</li>\n");
      writer.write("</ul></div>");
      writer.write("</body></html>\n");
    }
  }

  public static void printDecadeTable(Sequence cumulativeReturns)
  {
    // Find start of decade.
    Calendar cal = Library.now();
    cal.setLenient(true);
    int iStart = -1;
    for (int i = 0; i < cumulativeReturns.length(); ++i) {
      cal.setTimeInMillis(cumulativeReturns.getTimeMS(i));
      if (cal.get(Calendar.MONTH) == 0 && cal.get(Calendar.YEAR) % 10 == 0) {
        iStart = i;
        break;
      }
    }
    if (iStart < 0) {
      return;
    }

    System.out.printf("<table id=\"decadeTable\" class=\"tablesorter\"><thead>\n");
    System.out
        .printf("<tr><th>Decade</th><th>CAGR</th><th>StdDev</th><th>Drawdown</th><th>Down 10%%</th><th>Total<br/>Return</th></tr>\n");
    System.out.printf("</thead><tbody>\n");

    for (int i = iStart; i + 120 < cumulativeReturns.length(); i += 120) {
      cal.setTimeInMillis(cumulativeReturns.getTimeMS(i));
      Sequence decade = cumulativeReturns.subseq(i, 121);
      InvestmentStats stats = InvestmentStats.calcInvestmentStats(decade);
      System.out.printf(" <tr><td>%ds</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td>%.2fx</td></tr>\n",
          cal.get(Calendar.YEAR), stats.cagr, stats.devAnnualReturn, stats.maxDrawdown, stats.percentDown10,
          stats.totalReturn);
    }
    System.out.printf("</tbody></table>\n");
  }
}
