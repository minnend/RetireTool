package org.minnen.retiretool.data;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.time.Month;

public class FredIO
{
  public static final String auth     = System.getenv("fred.auth");
  public static final File   fredPath = new File(DataIO.financePath, "fred");

  /** @return URL for human-readable web page. */
  public static URL buildURLForWeb(String seriesID)
  {
    try {
      String address = "https://fred.stlouisfed.org/series/" + seriesID;
      return new URL(address);
    } catch (MalformedURLException e) {
      e.printStackTrace();
      return null;
    }
  }

  public static URL buildURLForJSON(String seriesID)
  {
    return buildURLForJSON(seriesID, LocalDate.of(1700, Month.JANUARY, 1));
  }

  public static URL buildURLForJSON(String seriesID, LocalDate startDate)
  {
    try {
      // https://research.stlouisfed.org/docs/api/fred/series.html
      String startDateString = String.format("%04d-%02d-%02d", startDate.getYear(), startDate.getMonthValue(),
          startDate.getDayOfMonth());
      String address = String.format(
          "https://api.stlouisfed.org/fred/series?series_id=%s&api_key=%s&realtime_start=%s&file_type=json", seriesID,
          FredIO.auth, startDateString);
      System.out.printf("URL: %s\n", address);
      return new URL(address);
    } catch (MalformedURLException e) {
      e.printStackTrace();
      return null;
    }
  }

  public static URL buildURLForCSV(String seriesID)
  {
    try {
      // Example Link: https://fred.stlouisfed.org/graph/fredgraph.csv?id=TB3MS
      String address = "https://fred.stlouisfed.org/graph/fredgraph.csv?id=" + seriesID;
      return new URL(address);
    } catch (MalformedURLException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Download FRED financial data into the given file, or create a file if the path is a directory.
   * 
   * @param path Directory in which to store data.
   * @param seriesID series to download
   * @param replaceAgeMs If the file exists and is older than this value (in ms), replace it; otherwise, don't download
   *          new data.
   * @return File in which that data is stored or null if there was an error.
   */
  public static File downloadData(File path, String seriesID, long replaceAgeMs)
  {
    path = getFile(path, seriesID);

    if (path.exists()) {
      if (!path.isFile() || !path.canWrite()) {
        System.err.printf("Path is not a writeable file (%s).\n", path.getPath());
        return null;
      }
      if (!DataIO.isFileOlder(path, replaceAgeMs)) {
        // System.out.printf("Recent file already exists (%s).\n", path.getName());
        return path;
      }
    }

    System.out.printf("Download FRED data: %s\n", seriesID);
    URL url = buildURLForCSV(seriesID);
    DataIO.copyUrlToFile(url, path);
    return path;
  }

  public static File getFile(File path, String seriesID)
  {
    if (!path.exists()) {
      path.mkdirs();
    }
    if (path.isDirectory()) {
      return new File(path, seriesID.toUpperCase() + ".csv");
    }
    return path;
  }
}
