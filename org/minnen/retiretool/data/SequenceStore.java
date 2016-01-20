package org.minnen.retiretool.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.minnen.retiretool.LinearFunc;
import org.minnen.retiretool.data.Sequence.EndpointBehavior;
import org.minnen.retiretool.util.FinLib;
import org.minnen.retiretool.util.Random;
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
  public static final Random         rng         = new Random();

  private final List<Sequence>       seqs        = new ArrayList<>();
  private final Map<String, Integer> nameToIndex = new HashMap<>();
  private final Map<String, String>  aliasMap    = new HashMap<>();
  private final Map<String, String>  nameToOrig  = new HashMap<>();

  private long                       commonStart = TimeLib.TIME_ERROR;
  private long                       commonEnd   = TimeLib.TIME_ERROR;

  public long getCommonStartTime()
  {
    return commonStart;
  }

  public long getCommonEndTime()
  {
    return commonEnd;
  }

  public void setCommonTimes(long commonStart, long commonEnd)
  {
    this.commonStart = commonStart;
    this.commonEnd = commonEnd;
  }

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

  public String removeAlias(String alias)
  {
    String target = aliasMap.get(alias);
    aliasMap.remove(alias);
    nameToIndex.remove(alias);
    return target;
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
   * @param seq sequence to add
   * @param name new name for the sequence
   * @return index in the store
   */
  public int add(Sequence seq, String name)
  {
    seq.setName(name);
    int index = getIndex(name);
    if (index < 0) {
      assert !nameToIndex.containsKey(name) : name;

      // Add the new sequence to the store.
      index = seqs.size();
      seqs.add(seq);
      nameToIndex.put(name, index);
      nameToOrig.put(name, name);
    } else {
      assert nameToIndex.containsKey(name) : name;

      // Update sequence.
      seqs.set(index, seq);
    }

    assert get(name) == seq;
    return index;
  }

  public boolean remove(String name)
  {
    int index = getIndex(name);
    if (index < 0) return false;
    seqs.remove(index);
    nameToIndex.remove(name);
    nameToOrig.remove(name);
    return true;
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
   * @param startMs first accessible ms (inclusive).
   * @param endMs last accessible ms (inclusive).
   * @param key key to use for locking / unlocking sequences.
   */
  public void lock(long startMs, long endMs, long key)
  {
    for (Sequence seq : seqs) {
      IndexRange range = seq.getIndices(startMs, endMs, EndpointBehavior.Inside);
      seq.lock(range.iStart, range.iEnd, key);
    }
  }

  public void relock(long startMs, long endMs, long key)
  {
    for (Sequence seq : seqs) {
      seq.relock(startMs, endMs, EndpointBehavior.Inside, key);
    }
  }

  /** Unlock all sequences currently in this store. */
  public void unlock(long key)
  {
    for (Sequence seq : seqs) {
      seq.unlock(key);
    }
  }

  public Sequence genNoisy(String name, LinearFunc priceSDev, int iDim)
  {
    Sequence seq = null;
    String nameOrig = name + "-orig";
    if (hasName(nameOrig)) {
      seq = get(nameOrig);
    } else {
      seq = get(name);
      add(seq, nameOrig);
    }

    Sequence noisySeq = new Sequence(seq.getName() + "-noisy");
    for (FeatureVec x : seq) {
      x = new FeatureVec(x);
      double price = x.get(iDim);
      double sdev = priceSDev.calc(price);
      double noisy = price + rng.nextGaussian() * sdev;
      if (noisy <= 0.0) {
        noisy = price * (0.01 + rng.nextDouble() * 0.2);
      }

      // Price should always be a whole number of pennies.
      noisy = Math.round(noisy * 100.0) / 100.0;

      x.set(iDim, noisy);
      noisySeq.addData(x);
    }

    add(noisySeq);
    alias(name, noisySeq.getName());

    return noisySeq;
  }

  public void removeNoisy(String name)
  {
    String noisyName = name + "-noisy";
    remove(noisyName);
    removeAlias(name);
  }

  @Override
  public Iterator<Sequence> iterator()
  {
    return seqs.iterator();
  }
}
