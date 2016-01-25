package org.minnen.retiretool.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/** Buffered file writer that allows printf syntax. */
public class Writer extends BufferedWriter
{
  public Writer(File f) throws IOException
  {
    super(new FileWriter(f));
  }

  public void write(String format, Object... args) throws IOException
  {
    super.write(String.format(format, args));
  }
}
