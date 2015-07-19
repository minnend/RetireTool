package org.minnen.retiretool;

import java.util.*;

/** Abstract base class for all data objects (FeatureVec and Sequence). */
public abstract class Datum
{
  private List<String> metaKeys;
  private List<Object> metaData;

  /** copy the contents of the given datum into this object */
  public void copyFrom(Datum datum)
  {
    copyMeta(datum);
  }

  /** @return set containing all metadata keys */
  public List<String> getMetaKeys()
  {
    return metaKeys;
  }

  /** copy meta data from the given datum, overwriting existing entries in this datum */
  public void copyMeta(Datum datum)
  {
    final int N = datum.getNumMeta();
    for (int i = 0; i < N; i++)
      setMeta(datum.getMetaKey(i), datum.getMetaData(i));
  }

  /** @return number of meta data entries */
  public int getNumMeta()
  {
    if (metaKeys == null)
      return 0;
    return metaKeys.size();
  }

  /** @return metadata key by index */
  public String getMetaKey(int i)
  {
    return metaKeys.get(i);
  }

  /** @return metadata value by index */
  public Object getMetaData(int i)
  {
    return metaData.get(i);
  }

  /** @return index of the given key (-1 if not found) */
  public int getMetaIndex(String key)
  {
    if (metaKeys == null)
      return -1;
    final int N = metaKeys.size();
    for (int i = 0; i < N; i++)
      if (key == metaKeys.get(i))
        return i;
    if (key == null)
      return -1;
    for (int i = 0; i < N; i++)
      if (key.equals(metaKeys.get(i)))
        return i;

    return -1;
  }

  /** add a new object to this datum's metadata */
  public Object setMeta(String key, Object data)
  {
    int ix = -1;
    if (metaKeys == null) {
      metaKeys = new ArrayList<String>();
      metaData = new ArrayList<Object>();
    } else
      ix = getMetaIndex(key);
    if (ix < 0) {
      metaKeys.add(key);
      metaData.add(data);
      return null;
    } else {
      Object old = metaData.get(ix);
      metaData.set(ix, data);
      return old;
    }
  }

  /** retrieve metadata by name */
  public Object getMeta(String key)
  {
    int ix = getMetaIndex(key);
    if (ix < 0)
      return null;
    return getMetaData(ix);
  }

  /** @return requested metadata or default value if metadata unspecified */
  public String getMeta(String key, String def)
  {
    if (containsMeta(key))
      return (String) getMeta(key);
    return def;
  }

  /** @return requested metadata or default value if metadata unspecified */
  public double getMeta(String key, double def)
  {
    if (containsMeta(key))
      return (Double) getMeta(key);
    return def;
  }

  /** @return requested metadata or default value if metadata unspecified */
  public int getMeta(String key, int def)
  {
    if (containsMeta(key))
      return (Integer) getMeta(key);
    return def;
  }

  /** @return requested metadata or default value if metadata unspecified */
  public long getMeta(String key, long def)
  {
    if (containsMeta(key))
      return (Long) getMeta(key);
    return def;
  }

  /** remove metadata by key name */
  public void removeMeta(String key)
  {
    int ix = getMetaIndex(key);
    removeMetaData(ix);
  }

  /** remove metadata by index */
  public void removeMetaData(int ix)
  {
    if (metaKeys == null)
      return;
    if (ix < 0 || ix >= metaKeys.size())
      return;
    metaKeys.remove(ix);
    metaData.remove(ix);
  }

  /** remove all metadata */
  public void removeAllMeta()
  {
    if (metaKeys == null)
      return;
    metaKeys.clear();
    metaData.clear();
  }

  /** @return true if the given metadata key exists */
  public boolean containsMeta(String key)
  {
    int ix = getMetaIndex(key);
    return (ix >= 0);
  }
}
