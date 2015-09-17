package org.minnen.retiretool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.minnen.retiretool.data.Sequence;
import org.minnen.retiretool.stats.CumulativeStats;
import org.minnen.retiretool.stats.DurationalStats;

/**
 * This class stores sequences that hold cumulative returns for a particular asset / strategy. The store ensures that
 * all sequences have the same length and start / end times. It also ensures that the sequences are normalized to start
 * at the value ($1 by default).
 * 
 * Each cumulative returns sequence in the store can be accessed by index or by name.
 * 
 * @author David Minnen
 */
public class SequenceStore implements Iterable<Sequence>
{
  public final double                 startValue;
  public final int                    defaultStatsDuration = 10 * 12;

  private final List<Sequence>        returns              = new ArrayList<>();
  private final List<Sequence>        miscSeqs             = new ArrayList<>();
  private final List<CumulativeStats> cumulativeStats      = new ArrayList<>();
  private final List<DurationalStats> durationalStats      = new ArrayList<>();
  private final Map<String, Integer>  nameToIndex          = new HashMap<>();
  private final Map<String, Integer>  miscNameToIndex      = new HashMap<>();
  private final Map<String, String>   aliasMap             = new HashMap<>();

  private final List<List<Sequence>>  seqLists             = new ArrayList<List<Sequence>>();

  public SequenceStore()
  {
    this(1.0);
  }

  public SequenceStore(double startValue)
  {
    this.startValue = startValue;

    seqLists.add(returns);
    seqLists.add(miscSeqs);
  }

  public void alias(String from, String to)
  {
    aliasMap.put(from.toLowerCase(), to.toLowerCase());
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
   * Add the given sequence using the given name.
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
    if (!returns.isEmpty()) {
      Sequence seq = returns.get(0);
      assert cumulativeReturns.length() == seq.length();
      assert cumulativeReturns.getStartMS() == seq.getStartMS();
      assert cumulativeReturns.getEndMS() == seq.getEndMS();
    }

    // Normalize cumulative returns if requested.
    if (normalizeStartValue) {
      assert cumulativeReturns.length() > 0;
      double x = cumulativeReturns.getFirst(0);
      if (Math.abs(x - startValue) >= 1e-8) {
        cumulativeReturns._div(x);
      }
    }
    assert Math.abs(cumulativeReturns.getFirst(0) - startValue) < 1e-8;

    // Add the new sequence to the store.
    final int index = returns.size();
    cumulativeReturns.setName(name);
    returns.add(cumulativeReturns);
    nameToIndex.put(name.toLowerCase(), index);
    assert get(name) == cumulativeReturns;

    // Calculate cumulative stats for this strategy.
    CumulativeStats cstats = CumulativeStats.calc(cumulativeReturns);
    cumulativeReturns.setName(String.format("%s (%.2f%%)", cumulativeReturns.getName(), cstats.cagr));
    cumulativeStats.add(cstats);
    assert cumulativeStats.size() == returns.size();

    // Calculate durational stats for this strategy.
    DurationalStats dstats = DurationalStats.calc(cumulativeReturns, defaultStatsDuration);
    durationalStats.add(dstats);
    assert durationalStats.size() == returns.size();

    // System.out.printf("Added: \"%s\"\n", name);
    return index;
  }

  /**
   * Add the given sequence to the misc set using its internal name.
   *
   * @param seq sequence to add
   * @return index in the misc store
   */
  public int addMisc(Sequence seq)
  {
    return addMisc(seq, seq.getName());
  }

  /**
   * Add the given sequence to the misc set using the given name.
   *
   * @param misc sequence to add
   * @param name new name for the sequence
   * @return index in the misc store
   */
  public int addMisc(Sequence misc, String name)
  {
    assert !miscNameToIndex.containsKey(name) : name;

    // Make sure new sequence matches existing sequences.
    if (!miscSeqs.isEmpty()) {
      Sequence seq = miscSeqs.get(0);
      assert misc.length() == seq.length();
      assert misc.getStartMS() == seq.getStartMS();
      assert misc.getEndMS() == seq.getEndMS();
    }

    // Add the new sequence to the store.
    final int index = miscSeqs.size();
    misc.setName(name);
    miscSeqs.add(misc);
    miscNameToIndex.put(name.toLowerCase(), index);
    assert getMisc(name) == misc;

    // System.out.printf("Added Misc Seq: \"%s\"\n", name);
    return index;
  }

  public int size()
  {
    return returns.size();
  }

  public int getNumReturns()
  {
    return returns.size();
  }

  public int getNumMisc()
  {
    return miscSeqs.size();
  }

  public Sequence get(int i)
  {
    return returns.get(i);
  }

  public int getIndex(String name)
  {
    name = name.toLowerCase();
    name = aliasMap.getOrDefault(name, name);
    if (!nameToIndex.containsKey(name)) {
      String s = FinLib.getBaseName(name);
      if (s.equals(name)) {
        return -1;
      } else {
        return getIndex(s);
      }
    }
    return nameToIndex.get(name);
  }

  public Sequence get(String name)
  {
    return returns.get(getIndex(name));
  }

  public Sequence getMisc(int i)
  {
    return miscSeqs.get(i);
  }

  public int getMiscIndex(String name)
  {
    name = name.toLowerCase();
    name = aliasMap.getOrDefault(name, name);
    if (!miscNameToIndex.containsKey(name)) {
      String s = FinLib.getBaseName(name);
      if (s.equals(name)) {
        return -1;
      } else {
        return getMiscIndex(s);
      }
    }
    return miscNameToIndex.get(name);
  }

  public Sequence getMisc(String name)
  {
    return miscSeqs.get(getMiscIndex(name));
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

  public List<Sequence> getReturns(String... names)
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
    for (int i = 0; i < returns.size(); ++i) {
      durationalStats.set(i, DurationalStats.calc(returns.get(i), nMonths));
    }
  }

  /**
   * Lock all sequences currently in the store.
   * 
   * @param startMS first accessible ms (inclusive)
   * @param endMS last accessible ms (inclusive)
   */
  public void lock(long startMS, long endMS)
  {
    for (List<Sequence> seqs : seqLists) {
      for (Sequence seq : seqs) {
        int iStart = seq.getIndexAtOrAfter(startMS);
        int iEnd = seq.getIndexAtOrBefore(endMS);
        seq.lock(iStart, iEnd);
      }
    }
  }

  /** Unlock all sequences currently in this store. */
  public void unlock()
  {
    for (List<Sequence> seqs : seqLists) {
      for (Sequence seq : seqs) {
        seq.unlock();
      }
    }
  }

  @Override
  public Iterator<Sequence> iterator()
  {
    return returns.iterator();
  }
}
