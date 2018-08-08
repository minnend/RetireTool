package org.minnen.retiretool.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;

/** Buffered file writer that allows printf syntax. */
public class Writer extends BufferedWriter
{
  public Writer(File f) throws IOException
  {
    super(new FileWriter(f));
  }

  public Writer(StringWriter sw)
  {
    super(sw);
  }

  public void write(String format, Object... args) throws IOException
  {
    super.write(String.format(format, args));
  }

  public void writeln() throws IOException
  {
    super.write("\n");
  }
}
