package org.minnen.retiretool.data.sharesoutstandinghistory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.apache.commons.lang3.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.data.Sequence;

public class SharesOutstandingIO
{
  public static File getPath()
  {
    return new File(DataIO.getFinancePath(), "shares-outstanding-history");
  }

  public static String getFilename(String symbol)
  {
    return String.format("%s-shares-outstanding.csv", symbol.toUpperCase());
  }

  public static File getFile(String symbol)
  {
    File path = getPath();
    if (!path.exists() && !path.mkdirs()) return null;
    return new File(path, getFilename(symbol));
  }

  /** Download HTML, parse, and save CSV file. */
  public static File download(String symbol, long replaceAgeMs) throws IOException
  {
    File file = getFile(symbol);
    try {
      if (!DataIO.shouldDownloadUpdate(file, replaceAgeMs)) return file;
    } catch (IOException e) {
      System.err.println(e.getMessage());
      return null;
    }
    System.out.printf("Download shares outstanding: %s\n", symbol);

    String url = String.format("https://www.sharesoutstandinghistory.com/%s/", symbol.toLowerCase());
    String html = DataIO.copyUrlToString(url);
    if (html == null) return null;
    Document doc = Jsoup.parse(html);

    Element section = doc.selectFirst("td.infotablebox");
    Element table = section.getElementsByTag("table").first();
    Elements rows = table.getElementsByTag("tr");
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("M/d/y", Locale.getDefault());

    Sequence seq = new Sequence(symbol + " - shares outstanding");
    for (Element row : rows) {
      Elements cells = row.getElementsByTag("td");
      if (cells.size() != 2) continue;
      String key = cells.get(0).text().trim();
      key = StringEscapeUtils.unescapeHtml4(key);
      if (key.toLowerCase().equals("date")) continue;
      LocalDate date = LocalDate.parse(key, dtf);

      String value = cells.get(1).text().trim();
      value = StringEscapeUtils.unescapeHtml4(value);
      long shares = Math.round(DataIO.parseDouble(value));
      seq.addData(shares, date);
    }

    if (seq.isEmpty()) return null;
    DataIO.saveDateValueCSV(file, seq, 0);
    return file;
  }

  public static Sequence loadData(String symbol, long replaceAgeMs) throws IOException
  {
    File file = download(symbol, replaceAgeMs);
    if (file == null || !file.exists()) return null;
    return DataIO.loadDateValueCSV(file);
  }

  public static void main(String[] args) throws IOException
  {
    Sequence data = loadData("HSY", 0);
    System.out.println(data);
  }
}
