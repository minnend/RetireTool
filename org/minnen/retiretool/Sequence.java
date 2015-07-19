package org.minnen.retiretool;

import java.util.*;

import org.minnen.retiretool.Datum;
import org.minnen.retiretool.FeatureVec;

/**
 * A Sequence represents a list of multidimentional feature vectors.
 * 
 * Each sequence has a specific frequency and the absolute time of every data point can be computed.
 */
public class Sequence extends Datum implements Iterable<FeatureVec>
{
  public static final String KeyName    = "Seq.Name";
  public static final String KeyStartMS = "Seq.StartMS";

  /** data stored in this data set */
  private List<FeatureVec>   data;

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

  /**
   * Create a named sequence with the given sampling frequency
   * 
   * @param name name of this sequence
   * @param msStart start time (in ms) of the sequence
   */
  public Sequence(String name, long msStart)
  {
    setName(name);
    setStartMS(msStart);
    init();
  }

  /** initialize this sequence (constructors call this function) */
  protected void init()
  {
    data = new ArrayList<FeatureVec>();
  }

  /**
   * Duplicate the given sequence
   * 
   * @param seq sequence to dup
   */
  public Sequence(Sequence seq)
  {
    this(seq.getName(), seq);
  }

  /**
   * Duplicate the given sequence, but give the dup a new name
   * 
   * @param name new name
   * @param seq sequence to duplicate
   */
  public Sequence(String name, Sequence seq)
  {
    copyFrom(seq);
    copyMeta(seq);
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
    return getMeta(KeyName, "Anonymous");
  }

  public void setName(String name)
  {
    if (name != null)
      setMeta(KeyName, name);
    else
      removeMeta(KeyName);
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
    return data.get(0).getNumDims();
  }

  public int size()
  {
    return data.size();
  }

  /** @return i^th feature vector */
  public FeatureVec get(int i)
  {
    return data.get(i);
  }

  /** @return value of the d^th dimension in the i^th feature vector */
  public double get(int i, int d)
  {
    return data.get(i).get(d);
  }

  /** set the i^th feature vector */
  public void set(int i, FeatureVec fv)
  {
    data.set(i, fv);
  }

  /** set the d^th dimension in the i^th feature vector */
  public void set(int i, int d, double x)
  {
    data.get(i).set(d, x);
  }

  /** @return last feature vector in this data set */
  public FeatureVec getLast()
  {
    return data.get(data.size() - 1);
  }

  /** @return value of given dimension of last feature vector in this data set */
  public double getLast(int d)
  {
    return data.get(data.size() - 1).get(d);
  }

  /** @return length of this sequence */
  public int length()
  {
    return data.size();
  }

  /** set the start time (ms since epoch) for this sequence */
  public void setStartMS(long ms)
  {
    setMeta(KeyStartMS, ms);
  }

  /**
   * @return start time (time of first sample in ms) of this sequence
   */
  public long getStartMS()
  {
    return getMeta(KeyStartMS, Library.AppStartTime);
  }

  /**
   * @return end time (time of last sample in ms) of this sequence
   */
  public long getEndMS()
  {
    int T = length();
    if (T == 0)
      return getStartMS();
    return getTimeMS(T - 1);
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
    FeatureVec fv = data.get(i);
    assert fv.hasTime();
    return fv.getTime();
  }

  /** Clear all dates, effectively forcing this seq to appear uniformly sampled */
  public void removeDates()
  {
    for (FeatureVec fv : data)
      fv.removeTime();
  }

  public void setDate(int ix, long ms)
  {
    data.get(ix).setTime(ms);
    if (ix == 0)
      setStartMS(ms);
  }

  /** @return true if this data set has no data */
  public boolean isEmpty()
  {
    return data.isEmpty();
  }

  /**
   * @return new sequence formed from the concatenation of this sequence and the given sequence in different dimensions
   *         (does not modify this sequence).
   */
  public Sequence appendDims(Sequence seq)
  {
    Sequence ret = new Sequence(appendDims(seq));
    ret.copyMeta(seq);
    return ret;
  }

  public int addData(FeatureVec value)
  {
    assert (value != null);
    data.add(value);
    if (data.size() == 1 && value.hasTime())
      setStartMS(value.getTime());
    return data.size() - 1;
  }

  /** add a time stamped feature vector to the end of this sequence */
  public int addData(FeatureVec value, long ms)
  {
    int ix = addData(value);
    setDate(ix, ms);
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
    setDate(ix, ms);
    return ix;
  }

  /**
   * Divide each element of each frame by the given value.
   * 
   * @param x value to divide by
   * @return this data set
   */
  public Sequence div(double x)
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

  /** add all data from the given sequence to this sequence */
  public void append(Sequence seq)
  {
    if (isEmpty())
      setStartMS(seq.getStartMS());
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
    Sequence ret = new Sequence(getName(), getStartMS());

    // loop through remaining time steps
    int n = size();
    for (int i = 0; i < n; i++)
      ret.addData(get(i).selectDims(dims));
    ret.copyMeta(this);
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
    return extractDim(iDim, iStart, size() - iStart);
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
    return extractDim(iDim, 0, size());
  }
}
