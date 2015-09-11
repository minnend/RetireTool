package org.minnen.retiretool;

import java.util.*;

import org.minnen.retiretool.FeatureVec;

/**
 * A Sequence represents a list of multidimentional feature vectors.
 * 
 * Each sequence has a specific frequency and the absolute time of every data point can be computed.
 */
public class Sequence implements Iterable<FeatureVec>
{
  /** data stored in this data set */
  private List<FeatureVec> data;
  private String           name;
  private int              lockStart = -1;
  private int              lockEnd   = -1;

  /**
   * Create an anonymous sequence at 1Hz
   */
  public Sequence()
  {
    init();
  }

  /**
   * Create a named sequence
   * 
   * @param name name of this sequence
   */
  public Sequence(String name)
  {
    setName(name);
    init();
  }

  /** initialize this sequence (constructors call this function) */
  protected void init()
  {
    data = new ArrayList<FeatureVec>();
  }

  /**
   * Creates a new 1D sequence with the given name that contains the specified data.
   * 
   * @param name - name of the new sequence
   * @param data - data for the sequence
   */
  public Sequence(String name, double data[])
  {
    this(name);
    for (int i = 0; i < data.length; i++)
      addData(new FeatureVec(1, data[i]));
  }

  /**
   * Creates a new 1D sequence with the given name that contains the specified data.
   * 
   * @param data - data for the sequence
   */
  public Sequence(double data[])
  {
    this(null, data);
  }

  public String getName()
  {
    return name;
  }

  public void setName(String name)
  {
    this.name = name;
  }

  public String toString()
  {
    return String.format("[Seq: len=%d, %dD  [%s]->[%s]]", length(), getNumDims(), Library.formatTime(getStartMS()),
        Library.formatTime(getEndMS()));
  }

  /**
   * Return the dimensionality of this data set. We assume that the first feature vector has the correct dimensionality.
   * Return 0 if no data is loaded.
   */
  public int getNumDims()
  {
    if (data.isEmpty())
      return 0;
    return get(0).getNumDims();
  }

  public void lock(int iStart, int iEnd)
  {
    assert (iStart >= -1 && iStart <= length() - 1);
    assert (iEnd >= -1 && iEnd <= length() - 1);
    assert (iStart <= iEnd);
    lockStart = iStart;
    lockEnd = iEnd;
  }

  public void clearLock()
  {
    lockStart = lockEnd = -1;
  }

  public boolean isLocked()
  {
    return lockStart >= 0 || lockEnd >= 0;
  }

  /** @return First real (internal) index that respects lock. */
  private int getFirstIndex()
  {
    return Math.max(lockStart, 0);
  }

  /** @return Last real (internal) index that respects lock. */
  private int getLastIndex()
  {
    if (lockEnd < 0) {
      return data.size() - 1;
    } else {
      return lockEnd;
    }
  }

  /** @return index adjusted to respect current lock boundaries. */
  private int adjustIndex(int i)
  {
    int iStart = getFirstIndex();
    int iEnd = getLastIndex();
    if (i < 0 || i >= length()) {
      throw new IndexOutOfBoundsException(String.format("%d vs. [%d, %d]", i, iStart, iEnd));
    }
    return iStart + i;
  }

  /** @return length of this sequence */
  public int size()
  {
    return getLastIndex() - getFirstIndex() + 1;
  }

  /** @return length of this sequence */
  public int length()
  {
    return size();
  }

  /** @return i^th feature vector */
  public FeatureVec get(int i)
  {
    i = adjustIndex(i);
    return data.get(i);
  }

  /** @return value of the d^th dimension in the i^th feature vector */
  public double get(int i, int d)
  {
    return get(i).get(d);
  }

  /** set the i^th feature vector */
  public void set(int i, FeatureVec fv)
  {
    i = adjustIndex(i);
    data.set(i, fv);
  }

  /** set the d^th dimension in the i^th feature vector */
  public void set(int i, int d, double x)
  {
    get(i).set(d, x);
  }

  /** @return first feature vector in this sequence. */
  public FeatureVec getFirst()
  {
    return get(0);
  }

  /** @return value of given dimension of first feature vector in this sequence. */
  public double getFirst(int d)
  {
    return getFirst().get(d);
  }

  /** @return last feature vector in this sequence. */
  public FeatureVec getLast()
  {
    return get(length() - 1);
  }

  /** @return value of given dimension of last feature vector in this sequence. */
  public double getLast(int d)
  {
    return getLast().get(d);
  }

  /** @return FeatureVec with minimum value for each dimension. */
  public FeatureVec getMin()
  {
    if (isEmpty()) {
      return null;
    }
    FeatureVec v = new FeatureVec(getNumDims());
    v.copyFrom(get(0));
    for (int i = 1; i < length(); ++i) {
      v._min(get(i));
    }
    return v;
  }

  /** @return FeatureVec with maximum value for each dimension. */
  public FeatureVec getMax()
  {
    if (isEmpty()) {
      return null;
    }
    FeatureVec v = new FeatureVec(getNumDims());
    v.copyFrom(get(0));
    for (int i = 1; i < length(); ++i) {
      v._max(get(i));
    }
    return v;
  }

  /**
   * @return start time (time of first sample in ms) of this sequence
   */
  public long getStartMS()
  {
    if (isEmpty()) {
      return Library.TIME_ERROR;
    } else {
      return getFirst().getTime();
    }
  }

  /**
   * @return end time (time of last sample in ms) of this sequence
   */
  public long getEndMS()
  {
    if (isEmpty()) {
      return Library.TIME_ERROR;
    } else {
      return getLast().getTime();
    }
  }

  /**
   * @return length of this sequence in milliseconds
   */
  public long getLengthMS()
  {
    return getEndMS() - getStartMS();
  }

  /** @return time in ms of the given data frame */
  public long getTimeMS(int i)
  {
    FeatureVec fv = get(i);
    assert fv.hasTime();
    return fv.getTime();
  }

  public void setTime(int i, long ms)
  {
    get(i).setTime(ms);
  }

  /** @return true if this data set has no data */
  public boolean isEmpty()
  {
    return data.isEmpty();
  }

  /**
   * Append the vectors in the given seq to each vector in this sequence.
   * 
   * @param seq holds vectors to append
   * @return this Sequence
   */
  public Sequence _appendDims(Sequence seq)
  {
    assert !isLocked();
    assert length() == seq.length();
    for (int i = 0; i < length(); ++i) {
      get(i)._appendDims(seq.get(i));
    }
    return this;
  }

  public int addData(FeatureVec value)
  {
    assert (value != null);
    data.add(value);
    return data.size() - 1;
  }

  /** add a time stamped feature vector to the end of this sequence */
  public int addData(FeatureVec value, long ms)
  {
    int ix = addData(value);
    setTime(ix, ms);
    return ix;
  }

  /**
   * Add (append) a value to this data set.
   * 
   * @param value x to add
   * @return index where fv was placed
   */
  public int addData(double value)
  {
    assert isEmpty() || getNumDims() == 1;
    data.add(new FeatureVec(1, value));
    return data.size() - 1;
  }

  /** add a time stamped feature vector to the end of this sequence */
  public int addData(double x, long ms)
  {
    int ix = addData(x);
    setTime(ix, ms);
    return ix;
  }

  /**
   * Multiply each element of each frame by the given value.
   * 
   * @param x value to divide by
   * @return this data set
   */
  public Sequence _mul(double x)
  {
    for (FeatureVec fv : data)
      fv._mul(x);
    return this;
  }

  /**
   * Divide each element of each frame by the given value.
   * 
   * @param x value to divide by
   * @return this data set
   */
  public Sequence _div(double x)
  {
    for (FeatureVec fv : data)
      fv._div(x);
    return this;
  }

  /**
   * @return index of the data point closest to the given time
   */
  public int getClosestIndex(long ms)
  {
    int n = length();
    if (n == 0)
      return -1;
    int a = 0;
    long ta = getTimeMS(a);
    int b = n - 1;
    long tb = getTimeMS(b);
    if (ms <= ta)
      return a;
    if (ms >= tb)
      return b;
    while (a + 1 < b) {
      int m = (a + b) / 2;
      long tm = getTimeMS(m);
      if (tm == ms)
        return m;
      if (ms < tm)
        b = m;
      else
        a = m;
    }

    long da = Math.abs(ms - getTimeMS(a));
    long dap1 = (a + 1 < n ? Math.abs(ms - getTimeMS(a + 1)) : Long.MAX_VALUE);
    if (da <= dap1)
      return a;
    else
      return a + 1;
  }

  /**
   * @return index of the data point at or before the given time (if possible).
   */
  public int getIndexAtOrBefore(long ms)
  {
    int i = getClosestIndex(ms);
    if (i > 0 && ms < get(i).getTime()) {
      --i;
    }
    assert getTimeMS(i) <= ms || i == 0;
    return i;
  }

  /**
   * @return index of the data point at or before the given time (if possible).
   */
  public int getIndexAtOrAfter(long ms)
  {
    int i = getClosestIndex(ms);
    if (i < length() - 1 && ms > get(i).getTime()) {
      ++i;
    }
    assert getTimeMS(i) >= ms || i == length() - 1;
    return i;
  }

  /** add all data from the given sequence to this sequence */
  public void append(Sequence seq)
  {
    data.addAll(seq.data);
  }

  /**
   * Returns a new dataset comprised of all time steps in this dataset but with different dimensions. The new dataset
   * will have one dimension corresponding to each (zero-based) element in dims.
   * 
   * Thus, to select the first and third dimensions from a 3D dataset: Sequence data2D = data3D.selectDims(new
   * int[]{0,2});
   */
  public Sequence extractDims(int... dims)
  {
    Sequence ret = new Sequence(getName());

    // loop through remaining time steps
    int n = length();
    for (int i = 0; i < n; i++)
      ret.addData(get(i).selectDims(dims));
    return ret;
  }

  @Override
  public Iterator<FeatureVec> iterator()
  {
    return data.iterator();
  }

  /**
   * Extract a subset (suffix) from the given dimension and return it in an array.
   * 
   * @param iDim - index of the dimension from which to extract
   * @param iStart - first index in the subset
   * @return double array representing the subseq
   */
  public double[] extractDim(int iDim, int iStart)
  {
    return extractDim(iDim, iStart, length() - iStart);
  }

  /**
   * Extract a subset from the given dimension and return it in an array.
   * 
   * @param iDim - index of the dimension from which to extract
   * @param iStart - first index in the subset
   * @param len - length of subset to extract
   * @return double array representing the subseq
   */
  public double[] extractDim(int iDim, int iStart, int len)
  {
    double[] ret = new double[len];
    for (int i = 0; i < len; i++)
      ret[i] = get(i + iStart, iDim);
    return ret;
  }

  /**
   * Extract the given dimension and return it in an array.
   * 
   * @param iDim - index of the dimension to retrieve
   * @return the given dimension as a double array
   */
  public double[] extractDim(int iDim)
  {
    return extractDim(iDim, 0, length());
  }

  /** @return index in data sequence for the given year and month (January == 1). */
  public int getIndexForDate(int year, int month)
  {
    long ms = Library.getTime(1, month, year);
    return getClosestIndex(ms);
  }

  /** @return subsequence starting at index iStart through end of the sequence. */
  public Sequence subseq(int iStart)
  {
    return subseq(iStart, length() - iStart);
  }

  /** @return subsequence with numElements starting at index iStart. */
  public Sequence subseq(int iStart, int numElements)
  {
    assert iStart >= 0 && numElements > 0;
    Sequence seq = new Sequence(name);
    for (int i = 0; i < numElements; ++i) {
      seq.addData(get(iStart + i));
    }
    assert seq.length() == numElements;
    return seq;
  }

  /** @return smallest subsequence that contains start and end times. */
  public Sequence subseq(long startMs, long endMs)
  {
    assert startMs <= endMs;
    int i = getIndexAtOrBefore(startMs);
    int j = getIndexAtOrAfter(endMs);
    assert i <= j;
    return subseq(i, j - i + 1);
  }

  /** @return new sequence equal to this sequence added (summed with) the given sequence. */
  public Sequence add(Sequence seq)
  {
    assert length() == seq.length();
    Sequence ret = new Sequence(getName() + " + " + seq.getName());
    for (int i = 0; i < length(); ++i) {
      FeatureVec x = get(i);
      FeatureVec y = seq.get(i);
      ret.addData(x.add(y), getTimeMS(i));
    }
    return ret;
  }

  /** @return new sequence equal to given sequence subtracted from this sequence. */
  public Sequence sub(Sequence seq)
  {
    assert length() == seq.length();
    Sequence ret = new Sequence(getName() + " - " + seq.getName());
    for (int i = 0; i < length(); ++i) {
      FeatureVec x = get(i);
      FeatureVec y = seq.get(i);
      ret.addData(x.sub(y), getTimeMS(i));
    }
    return ret;
  }

  /** @return new sequence equal to given sequence multiplied with this sequence (component-wise). */
  public Sequence mul(Sequence seq)
  {
    assert length() == seq.length();
    Sequence ret = new Sequence(getName() + " - " + seq.getName());
    for (int i = 0; i < length(); ++i) {
      FeatureVec x = get(i);
      FeatureVec y = seq.get(i);
      ret.addData(x.mul(y), getTimeMS(i));
    }
    return ret;
  }

  /** @return new sequence equal to this sequence divided by the given sequence (component-wise). */
  public Sequence divide(Sequence divisor)
  {
    assert length() == divisor.length();
    Sequence seq = new Sequence(getName() + " / " + divisor.getName());
    for (int i = 0; i < length(); ++i) {
      FeatureVec x = get(i);
      FeatureVec y = divisor.get(i);
      seq.addData(x.div(y), getTimeMS(i));
    }
    return seq;
  }

}
