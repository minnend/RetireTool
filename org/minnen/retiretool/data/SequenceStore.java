package org.minnen.retiretool.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.TimeLib;

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
  private final List<Sequence>       seqs        = new ArrayList<>();
  private final Map<String, Integer> nameToIndex = new HashMap<>();
  private final Map<String, String>  aliasMap    = new HashMap<>();
  private final Map<String, String>  nameToOrig  = new HashMap<>();

  public void clear()
  {
    seqs.clear();
    nameToIndex.clear();
    aliasMap.clear();
    nameToOrig.clear();
  }

  public int alias(String from, String to)
  {
    aliasMap.put(from, to);

    if (hasName(to)) {
      int index = nameToIndex.get(to);
      nameToIndex.put(from, index);
      return index;
    } else {
      throw new RuntimeException(String.format("Can't find target sequence name (%s)", to));
    }
  }

  /**
   * Add the given sequence using its internal name.
   *
   * @param seq sequence to add
   * @return index in the store
   */
  public int add(Sequence seq)
  {
    return add(seq, seq.getName());
  }

  /**
   * Add the given sequence using its internal name.
   *
   * @param seq cumulative returns sequence to add
   * @param name new name for the cumulative returns sequence
   * @param normalizeStartValue if true, adjust the sequence so that the first value matches the store's start value
   * @return index in the store
   */
  public int add(Sequence seq, String name)
  {
    assert !nameToIndex.containsKey(name) : name;

    // Add the new sequence to the store.
    final int index = seqs.size();
    seq.setName(name);
    seqs.add(seq);
    nameToIndex.put(name, index);
    nameToOrig.put(name, name);
    assert get(name) == seq;

    // System.out.printf("Added: \"%s\"\n", name);
    return index;
  }

  public int size()
  {
    return seqs.size();
  }

  public boolean hasName(String name)
  {
    return getIndex(name) >= 0;
  }

  public int getIndex(String name)
  {
    // name = aliasMap.getOrDefault(name, name);
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
    return seqs.get(index);
  }

  public Sequence tryGet(String name)
  {
    int index = getIndex(name);
    return (index < 0 ? null : seqs.get(index));
  }

  public Sequence tryGet(int id)
  {
    return (id < 0 ? null : seqs.get(id));
  }

  public List<Sequence> getSeqs(String... names)
  {
    List<Sequence> list = new ArrayList<>();
    for (String name : names) {
      list.add(get(name));
    }
    return list;
  }

  public Collection<String> getNames()
  {
    return nameToIndex.keySet();
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
   * Lock all sequences currently in the store.
   * 
   * @param startMS first accessible ms (inclusive)
   * @param endMS last accessible ms (inclusive)
   */
  public void lock(long startMS, long endMS)
  {
    for (Sequence seq : seqs) {
      int iStart = (startMS == TimeLib.TIME_BEGIN ? 0 : seq.getIndexAtOrAfter(startMS));
      int iEnd = seq.getIndexAtOrBefore(endMS);
      seq.lock(iStart, iEnd);
    }
  }

  /** Unlock all sequences currently in this store. */
  public void unlock()
  {
    for (Sequence seq : seqs) {
      seq.unlock();
    }
  }

  @Override
  public Iterator<Sequence> iterator()
  {
    return seqs.iterator();
  }
}
