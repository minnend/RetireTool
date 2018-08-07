package org.minnen.retiretool.data;

/** Holds a start and end index. */
public class IndexRange
{
  public final int iStart;
  public final int iEnd;

  public IndexRange(int iStart, int iEnd)
  {
    this.iStart = iStart;
    this.iEnd = iEnd;
  }

  public int length()
  {
    return iEnd - iStart + 1;
  }

  @Override
  public String toString()
  {
    return String.format("[%d,%d]", iStart, iEnd);
  }
}
