package org.minnen.retiretool.util;

/** Marks a class as serializable to a string. */
public interface StringSerializable
{
  public String serializeToString();

  public boolean parseString(String serialized);
}
