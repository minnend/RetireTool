package org.minnen.retiretool.data;

import java.io.File;

public class QuandlIO
{
  private static String auth = System.getenv("quandl.auth");

  public static String getAuth()
  {
    return auth;
  }

  public static void setAuth(String newAuth)
  {
    auth = newAuth;
  }

  public static File getPath()
  {
    return new File(DataIO.getFinancePath(), "quandl");
  }

  public static File downloadData(File path, String name, String baseURL, long replaceAgeMs)
  {
    path = getFile(path, name);
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

    System.out.printf("Download Quandl data: %s\n", name);
    String url = baseURL + "?api_key=" + auth;
    DataIO.copyUrlToFile(url, path);
    return path;
  }

  // TODO: combine with fred.
  public static File getFile(File path, String name)
  {
    if (!path.exists()) {
      path.mkdirs();
    }
    if (path.isDirectory()) {
      return new File(path, name.toUpperCase() + ".csv");
    }
    return path;
  }
}
