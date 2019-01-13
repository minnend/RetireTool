package org.minnen.retiretool.playground;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.minnen.retiretool.data.DataIO;
import org.minnen.retiretool.util.TimeLib;

public class DownloadUrl
{
  private static long download(URL url, File file) throws IOException
  {
    try (ReadableByteChannel rbc = Channels.newChannel(url.openStream());
        FileOutputStream fos = new FileOutputStream(file)) {
      return fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
    }
  }

  private static long copyUrlToFile(URL url, File file) throws IOException
  {
    try (InputStream input = url.openStream()) {
      return Files.copy(input, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static long copy(URL url, File file) throws IOException
  {
    System.out.printf("Follow Redirects: %s\n", HttpURLConnection.getFollowRedirects());
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    System.out.printf("Connect Timeout: %d\n", con.getConnectTimeout());
    System.out.printf("Read Timeout: %d\n", con.getReadTimeout());
    // con.setConnectTimeout(1000);
    // con.setReadTimeout(1000);
    System.out.printf("Request Method: %s\n", con.getRequestMethod());
    System.out.printf("Response Message: %s\n", con.getResponseMessage());
    System.out.printf("Response Code: %d\n", con.getResponseCode());

    for (int i = 0;; ++i) {
      String s = con.getHeaderField(i);
      if (s == null) break;
      System.out.printf("Header %d: %s\n", i, s);
    }

    long n = 0;
    try (InputStreamReader is = new InputStreamReader(con.getInputStream());
        BufferedReader br = new BufferedReader(is)) {
      while (true) {
        String line = br.readLine();
        if (line == null) break;
        System.out.printf("| %s\n", line);
        n += line.length();

      }
    }
    return n;
  }

  public static void main(String[] args) throws Exception
  {
    // URL url = new URL("http://ichart.finance.yahoo.com/table.csv?s=^GSPC&ignore=.csv");
    URL url = new URL("https://ichart.finance.yahoo.com/table.csv?s=^GSPC");
    // URL url = new URL("http://finance.yahoo.com/d/quotes.csv?s=AAPL&f=snabopghydr1qd1veb4j4rs7s6");
    File file = new File(DataIO.getOutputPath(), "test.txt");
    long a = TimeLib.getTime();
    // long n = download(url, file);
    // long n = copyUrlToFile(url, file);
    long n = copy(url, file);
    long b = TimeLib.getTime();
    System.out.printf("n=%d  time=%d\n", n, b - a);
  }

}
