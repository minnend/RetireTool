package org.minnen.retiretool.viz;

import java.io.File;

import org.minnen.retiretool.data.Sequence;

public class ChartConfig
{
  public static enum Type {
    Unknown, Line, Bar, Area, PosNegArea, Scatter, Bubble
  };

  public File     file;
  public Sequence data;
  public Type     type              = Type.Unknown;
  public String   title;
  public String   xAxisTitle;
  public String   yAxisTitle;
  public int      width             = 1200;
  public int      height            = 600;
  public int      axisTitleFontSize = 16;
  public boolean  showLegend        = false;
  public boolean  showDataLabels    = false;
  public boolean  showToolTips      = true;
  public String[] dimNames;

  // Specific to scatter plots.
  public int      radius            = 3;

  public static String chart2name(Type chartType)
  {
    if (chartType == Type.Line) {
      return "line";
    } else if (chartType == Type.Bar) {
      return "column";
    } else if (chartType == Type.Area || chartType == Type.PosNegArea) {
      return "area";
    } else if (chartType == Type.Scatter) {
      return "scatter";
    } else if (chartType == Type.Bubble) {
      return "bubble";
    } else {
      return "ERROR";
    }
  }

  public static String getQuotedString(String s)
  {
    return getQuotedString(s, "null");
  }

  public static String getQuotedString(String s, String defaultIfNull)
  {
    if (s == null) return defaultIfNull;
    return "'" + s + "'";
  }

  public ChartConfig(File file)
  {
    this.file = file;
  }

  public ChartConfig setData(Sequence data)
  {
    this.data = data;
    return this;
  }

  public ChartConfig setType(Type type)
  {
    this.type = type;
    return this;
  }

  public ChartConfig setTitle(String title)
  {
    this.title = title;
    return this;
  }

  public ChartConfig setXAxisTitle(String xAxisTitle)
  {
    this.xAxisTitle = xAxisTitle;
    return this;
  }

  public ChartConfig setYAxisTitle(String yAxisTitle)
  {
    this.yAxisTitle = yAxisTitle;
    return this;
  }

  public ChartConfig setAxisTitles(String xAxisTitle, String yAxisTitle)
  {
    this.xAxisTitle = xAxisTitle;
    this.yAxisTitle = yAxisTitle;
    return this;
  }

  public ChartConfig setWidth(int width)
  {
    this.width = width;
    return this;
  }

  public ChartConfig setHeight(int height)
  {
    this.height = height;
    return this;
  }

  public ChartConfig setSize(int width, int height)
  {
    this.width = width;
    this.height = height;
    return this;
  }

  public ChartConfig setRadius(int radius)
  {
    this.radius = radius;
    return this;
  }

  public ChartConfig setDimNames(String... names)
  {
    this.dimNames = names;
    return this;
  }

  public ChartConfig showDataLabels(boolean show)
  {
    this.showDataLabels = show;
    return this;
  }

  public ChartConfig showLegend(boolean show)
  {
    this.showLegend = show;
    return this;
  }

  public ChartConfig showToolTips(boolean show)
  {
    this.showToolTips = show;
    return this;
  }
}
