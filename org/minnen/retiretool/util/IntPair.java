package org.minnen.retiretool.util;

public class IntPair implements Comparable<IntPair>
{
  public int first, second;

  public IntPair(int code1, int code2)
  {
    this.first = code1;
    this.second = code2;
  }

  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = 1;
    result = prime * result + first;
    result = prime * result + second;
    return result;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    IntPair other = (IntPair) obj;
    if (first != other.first) return false;
    if (second != other.second) return false;
    return true;
  }

  @Override
  public int compareTo(IntPair other)
  {
    if (other == this) return 0;
    if (other == null) return -1;
    if (second < other.second) return -1;
    if (second > other.second) return 1;
    if (first < other.first) return -1;
    if (first > other.first) return 1;
    return 0;
  }
}
