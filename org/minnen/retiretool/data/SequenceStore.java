package org.minnen.retiretool.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.minnen.retiretool.FinLib;
import org.minnen.retiretool.FinLib.Inflation;
import org.minnen.retiretool.Library;
import org.minnen.retiretool.TimeLib;
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

  private final List<Sequence>        nominalReturns    = new ArrayList<>();
  private final List<Sequence>        realReturns       = new ArrayList<>();
  private final List<Sequence>        miscSeqs          = new ArrayList<>();
  private final List<CumulativeStats> cumulativeStats   = new ArrayList<>();
  private final List<DurationalStats> durationalStats   = new ArrayList<>();
  private final Map<String, Integer>  nameToIndex       = new HashMap<>();
  private final Map<String, Integer>  miscNameToIndex   = new HashMap<>();
  private final Map<String, String>   aliasMap          = new HashMap<>();
  private final Map<String, String>   nameToOrig        = new HashMap<>();

  private final List<List<Sequence>>  seqLists          = new ArrayList<List<Sequence>>();

  private int                         lastStatsDuration = -1;

  public SequenceStore()
  {
    this(1.0);
  }

  public SequenceStore(double startValue)
  {
    this.startValue = startValue;

    seqLists.add(nominalReturns);
    seqLists.add(realReturns);
    seqLists.add(miscSeqs);
  }

  public void clear()
  {
    nominalReturns.clear();
    realReturns.clear();
    miscSeqs.clear();
    cumulativeStats.clear();
    durationalStats.clear();
    nameToIndex.clear();
    miscNameToIndex.clear();
    aliasMap.clear();
    nameToOrig.clear();
    lastStatsDuration = -1;
  }

  public int getLastStatsDuration()
  {
    return lastStatsDuration;
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
    assert realReturns.isEmpty() || realReturns.size() == nominalReturns.size();

    // Get inflation data to calculate real returns.
    Sequence inflation = null;
    if (hasMisc("inflation")) {
      inflation = getMisc("inflation");
      assert !inflation.isLocked();
      inflation.lock(inflation.getClosestIndex(cumulativeReturns.getStartMS()),
          inflation.getClosestIndex(cumulativeReturns.getEndMS()));
      assert cumulativeReturns.length() == inflation.length();
    }

    // Make sure new sequence matches existing sequences.
    if (!nominalReturns.isEmpty()) {
      assert cumulativeReturns.matches(nominalReturns.get(0));
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
    final int index = nominalReturns.size();
    cumulativeReturns.setName(name);
    nominalReturns.add(cumulativeReturns);
    nameToIndex.put(name.toLowerCase(), index);
    nameToOrig.put(name.toLowerCase(), name);
    assert get(name) == cumulativeReturns;

    // Calculate and add real returns (inflation-adjusted) to the store.
    if (inflation != null) {
      Sequence real = FinLib.calcRealReturns(cumulativeReturns, inflation);
      assert real.length() == cumulativeReturns.length();
      realReturns.add(real);
      assert getReal(name) == real;
      inflation.unlock();
    }

    // Calculate cumulative stats for this strategy.
    CumulativeStats cstats = CumulativeStats.calc(cumulativeReturns);
    cumulativeReturns.setName(String.format("%s (%.2f%%)", cumulativeReturns.getName(), cstats.cagr));
    cumulativeStats.add(cstats);
    assert cumulativeStats.size() == nominalReturns.size();

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

    // Add the new sequence to the store.
    final int index = miscSeqs.size();
    misc.setName(name);
    miscSeqs.add(misc);
    miscNameToIndex.put(name.toLowerCase(), index);
    assert getMisc(name) == misc;
    nameToOrig.put(name.toLowerCase(), name);

    if (name.toLowerCase().equals("inflation")) {
      recalcNominalReturns();
    }

    // System.out.printf("Added Misc Seq: \"%s\"\n", name);
    return index;
  }

  public int size()
  {
    assert realReturns.isEmpty() || nominalReturns.size() == realReturns.size();
    return nominalReturns.size();
  }

  public int getNumReturns()
  {
    return size();
  }

  public int getNumMisc()
  {
    return miscSeqs.size();
  }

  public boolean hasName(String name)
  {
    return getIndex(name) >= 0;
  }

  public boolean hasMisc(String name)
  {
    return getMiscIndex(name) >= 0;
  }

  /** Is the name in this store (regular or misc)? */
  public boolean has(String name)
  {
    return hasName(name) || hasMisc(name);
  }

  private int getIndex(String name)
  {
    name = name.toLowerCase();
    name = aliasMap.getOrDefault(name, name);
    int index = nameToIndex.getOrDefault(name, -1);
    if (index < 0) {
      String s = FinLib.getBaseName(name);
      if (s.equals(name)) {
        return -1;
      } else {
        return getIndex(s);
      }
    }
    return index;
  }

  public Sequence get(String name)
  {
    int index = getIndex(name);
    assert index >= 0 : "Can't find sequence: " + name;
    return nominalReturns.get(index);
  }

  public Sequence tryGet(String name)
  {
    int index = getIndex(name);
    return (index < 0 ? null : nominalReturns.get(index));
  }

  public Sequence getReal(String name)
  {
    int index = getIndex(name);
    assert index >= 0 : "Can't find sequence: " + name;
    return realReturns.get(index);
  }

  private int getMiscIndex(String name)
  {
    name = name.toLowerCase();
    name = aliasMap.getOrDefault(name, name);
    int index = miscNameToIndex.getOrDefault(name, -1);
    if (index < 0) {
      String s = FinLib.getBaseName(name);
      if (s.equals(name)) {
        return -1;
      } else {
        return getMiscIndex(s);
      }
    }
    return index;
  }

  public Sequence getMisc(String name)
  {
    int index = getMiscIndex(name);
    assert index >= 0 : "Can't find misc sequence: " + name;
    return miscSeqs.get(index);
  }

  public Sequence tryGetMisc(String name)
  {
    int index = getMiscIndex(name);
    return (index < 0 ? null : miscSeqs.get(index));
  }

  public CumulativeStats getCumulativeStats(int i)
  {
    return cumulativeStats.get(i);
  }

  public CumulativeStats getCumulativeStats(String name)
  {
    int index = getIndex(name);
    assert index >= 0 : "Can't find sequence: " + name;
    return cumulativeStats.get(index);
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
    int index = getIndex(name);
    assert index >= 0 : "Can't find sequence: " + name;
    if (index < durationalStats.size()) {
      return durationalStats.get(index);
    } else {
      return null;
    }
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

  public Collection<String> getNames()
  {
    return nameToIndex.keySet();
  }

  public Collection<String> getMiscNames()
  {
    return miscNameToIndex.keySet();
  }

  public List<String> getNames(String regex)
  {
    List<String> names = new ArrayList<>();
    Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    for (String name : nameToOrig.values()) {
      if (pattern.matcher(name).find()) {
        names.add(name);
      }
    }
    Collections.sort(names);
    return names;
  }

  /**
   * Calculate durational states for each stored sequence using the given duration.
   */
  public void recalcDurationalStats(int nMonths, FinLib.Inflation inflation)
  {
    lastStatsDuration = nMonths;
    for (int i = 0; i < nominalReturns.size(); ++i) {
      Sequence returns = (inflation == FinLib.Inflation.Ignore ? nominalReturns.get(i) : realReturns.get(i));
      DurationalStats stats = DurationalStats.calc(returns, nMonths);
      if (i < durationalStats.size()) {
        durationalStats.set(i, stats);
      } else {
        durationalStats.add(stats);
      }
    }
    assert durationalStats.size() == nominalReturns.size();
  }

  public void recalcNominalReturns()
  {
    Sequence inflation = getMisc("inflation");
    assert !inflation.isLocked();

    realReturns.clear();
    for (Sequence nominal : nominalReturns) {
      inflation.lockToMatch(nominal);
      Sequence real = FinLib.calcRealReturns(nominal, inflation);
      assert real.length() == nominal.length();
      realReturns.add(real);
      assert getReal(real.getName()) == real;
      inflation.unlock();
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

  public long getStartMS()
  {
    if (nominalReturns.isEmpty()) {
      return TimeLib.TIME_ERROR;
    }
    return nominalReturns.get(0).getStartMS();
  }

  public long getEndMS()
  {
    if (nominalReturns.isEmpty()) {
      return TimeLib.TIME_ERROR;
    }
    return nominalReturns.get(0).getEndMS();
  }

  @Override
  public Iterator<Sequence> iterator()
  {
    return nominalReturns.iterator();
  }

  /** @return iterator over real (inflation-adjusted) return sequences. */
  public Iterator<Sequence> iteratorReal()
  {
    return realReturns.iterator();
  }
}
