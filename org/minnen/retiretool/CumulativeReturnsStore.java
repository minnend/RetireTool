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
  public final double                 startValue;
  public int                          defaultStatsDurationInMonths = 10 * 12;
  private final List<Sequence>        seqs                         = new ArrayList<>();
  private final List<CumulativeStats> cumulativeStats              = new ArrayList<>();
  private final List<DurationalStats> durationalStats              = new ArrayList<>();
  private final Map<String, Integer>  nameToIndex                  = new HashMap<>();

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

    // Calculate cumulative stats for this strategy.
    CumulativeStats cstats = CumulativeStats.calc(cumulativeReturns);
    cumulativeReturns.setName(String.format("%s (%.2f%%)", cumulativeReturns.getName(), cstats.cagr));
    cumulativeStats.add(cstats);
    assert cumulativeStats.size() == seqs.size();

    // Calculate durational stats for this strategy.
    DurationalStats dstats = DurationalStats.calc(cumulativeReturns, defaultStatsDurationInMonths);
    durationalStats.add(dstats);
    assert durationalStats.size() == seqs.size();

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

  public int getIndex(String name)
  {
    return nameToIndex.get(name.toLowerCase());
  }

  public Sequence get(String name)
  {
    return seqs.get(getIndex(name));
  }

  public CumulativeStats getCumulativeStats(int i)
  {
    return cumulativeStats.get(i);
  }

  public CumulativeStats getCumulativeStats(String name)
  {
    return cumulativeStats.get(getIndex(name));
  }

  public List<CumulativeStats> getCumulativeStats(String... names)
  {
    List<CumulativeStats> stats = new ArrayList<>();
    for (String name : names) {
      stats.add(getCumulativeStats(name));
    }
    return stats;
  }

  public DurationalStats getDurationalStats(int i)
  {
    return durationalStats.get(i);
  }

  public DurationalStats getDurationalStats(String name)
  {
    return durationalStats.get(getIndex(name));
  }

  public List<DurationalStats> getDurationalStats(String... names)
  {
    List<DurationalStats> stats = new ArrayList<>();
    for (String name : names) {
      stats.add(getDurationalStats(name));
    }
    return stats;
  }

  public List<Sequence> getSequences(String... names)
  {
    List<Sequence> seqs = new ArrayList<>();
    for (String name : names) {
      seqs.add(get(name));
    }
    return seqs;
  }

  public Set<String> getNames()
  {
    return nameToIndex.keySet();
  }

  /**
   * Calculate durational states for each stored sequence using the given duration.
   */
  public void calcDurationalStats(int nMonths)
  {
    for (int i = 0; i < seqs.size(); ++i) {
      durationalStats.set(i, DurationalStats.calc(seqs.get(i), nMonths));
    }
  }

  @Override
  public Iterator<Sequence> iterator()
  {
    return seqs.iterator();
  }
}
