package org.minnen.retiretool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class stores sequences that hold cumulative returns for a particular asset / strategy. The store ensures that
 * all sequences have the same length and start / end times. It also ensures that the sequences are normalized to start
 * at the value ($1 by default).
 * 
 * Each cumulative returns sequence in the store can be accessed by index or by name.
 * 
 * @author David Minnen
 */
public class CumulativeReturnsStore implements Iterable<Sequence>
{
  public final double                startValue;
  public final List<Sequence>        seqs            = new ArrayList<>();
  public final List<CumulativeStats> cumulativeStats = new ArrayList<>();
  public final List<DurationalStats>     durationStats   = new ArrayList<>();
  public final Map<String, Integer>  nameToIndex     = new HashMap<>();

  public CumulativeReturnsStore()
  {
    this(1.0);
  }

  public CumulativeReturnsStore(double startValue)
  {
    this.startValue = startValue;
  }

  /**
   * Add the given sequence using its internal name.
   *
   * @param cumulativeReturns cumulative returns sequence to add
   * @return index in the store
   */
  public int add(Sequence cumulativeReturns)
  {
    return add(cumulativeReturns, cumulativeReturns.getName());
  }

  /**
   * Add the given sequence using its internal name.
   *
   * @param cumulativeReturns cumulative returns sequence to add
   * @param name new name for the cumulative returns sequence
   * @return index in the store
   */
  public int add(Sequence cumulativeReturns, String name)
  {
    return add(cumulativeReturns, name, true);
  }

  /**
   * Add the given sequence using its internal name.
   *
   * @param cumulativeReturns cumulative returns sequence to add
   * @param name new name for the cumulative returns sequence
   * @param normalizeStartValue if true, adjust the sequence so that the first value matches the store's start value
   * @return index in the store
   */
  public int add(Sequence cumulativeReturns, String name, boolean normalizeStartValue)
  {
    assert !nameToIndex.containsKey(name) : name;

    // Make sure new sequence matches existing sequences.
    if (!seqs.isEmpty()) {
      Sequence seq = seqs.get(0);
      assert cumulativeReturns.length() == seq.length();
      assert cumulativeReturns.getStartMS() == seq.getStartMS();
      assert cumulativeReturns.getEndMS() == seq.getEndMS();
    }

    // Normalize cumulative returns if requested.
    if (normalizeStartValue) {
      double x = cumulativeReturns.getFirst(0);
      if (Math.abs(x - startValue) >= 1e-8) {
        cumulativeReturns._div(x);
      }
    }
    assert Math.abs(cumulativeReturns.getFirst(0) - startValue) < 1e-8;

    // Add the new sequence to the store.
    final int index = seqs.size();
    cumulativeReturns.setName(name);
    seqs.add(cumulativeReturns);
    nameToIndex.put(name.toLowerCase(), index);
    assert get(name) == cumulativeReturns;
    // System.out.printf("Added: \"%s\"\n", name);
    return index;
  }

  public int size()
  {
    return seqs.size();
  }

  public Sequence get(int i)
  {
    return seqs.get(i);
  }

  public Sequence get(String name)
  {
    return seqs.get(nameToIndex.get(name.toLowerCase()));
  }

  public Set<String> getNames()
  {
    return nameToIndex.keySet();
  }

  @Override
  public Iterator<Sequence> iterator()
  {
    return seqs.iterator();
  }
}
