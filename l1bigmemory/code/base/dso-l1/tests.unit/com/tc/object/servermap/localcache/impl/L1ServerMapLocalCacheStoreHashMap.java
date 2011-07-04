/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package com.tc.object.servermap.localcache.impl;

import com.tc.object.servermap.localcache.L1ServerMapLocalCacheStore;
import com.tc.object.servermap.localcache.L1ServerMapLocalCacheStoreListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class L1ServerMapLocalCacheStoreHashMap<K, V> implements L1ServerMapLocalCacheStore<K, V> {
  private final List<L1ServerMapLocalCacheStoreListener<K, V>> listeners     = new CopyOnWriteArrayList<L1ServerMapLocalCacheStoreListener<K, V>>();
  private final HashMap<K, V>                                  backingCache  = new HashMap<K, V>();
  private final int                                            maxElementInMemory;
  private final HashSet<K>                                     pinnedEntries = new HashSet<K>();
  private final AtomicInteger                                  cacheSize     = new AtomicInteger();

  public L1ServerMapLocalCacheStoreHashMap(int maxInMemory) {
    if (maxInMemory == 0) {
      this.maxElementInMemory = Integer.MAX_VALUE;
    } else {
      this.maxElementInMemory = maxInMemory;
    }
  }

  public boolean addListener(L1ServerMapLocalCacheStoreListener<K, V> listener) {
    return listeners.add(listener);
  }

  public synchronized V get(K key) {
    return backingCache.get(key);
  }

  public V put(K key, V value) {
    V oldValue = null;
    int size;
    synchronized (this) {
      oldValue = backingCache.put(key, value);
      size = this.backingCache.size();
    }

    doCapacityEviction(key, size);

    return oldValue;
  }

  public V putPinnedEntry(K key, V value) {
    V oldValue = null;
    int size;
    synchronized (this) {
      oldValue = backingCache.put(key, value);
      size = this.backingCache.size();
      pinEntry(key);
    }

    doCapacityEviction(key, size);

    return oldValue;
  }

  private void doCapacityEviction(K key, int size) {
    /**
     * capacity eviction
     */
    if (maxElementInMemory != Integer.MAX_VALUE && size > maxElementInMemory) {
      removeExcept(size - maxElementInMemory, key);
    }
  }

  private synchronized void pinEntry(K key) {
    pinnedEntries.add(key);
  }

  public synchronized void unpinEntry(K key) {
    pinnedEntries.remove(key);
  }

  public V remove(K key) {
    final V value;
    synchronized (this) {
      value = backingCache.remove(key);
    }
    return value;
  }

  public int evict(int count) {
    return removeExcept(count, null);
  }

  public boolean removeListener(L1ServerMapLocalCacheStoreListener<K, V> listener) {
    return listeners.remove(listener);
  }

  private void notifyListeners(Map<K, V> evictedElements) {
    for (L1ServerMapLocalCacheStoreListener<K, V> listener : listeners) {
      listener.notifyElementsEvicted(evictedElements);
    }
  }

  private int removeExcept(int count, K key) {
    Map<K, V> tempMap = new HashMap<K, V>();
    synchronized (this) {
      Iterator<Entry<K, V>> iterator = backingCache.entrySet().iterator();
      int deletedElements = 0;
      while (iterator.hasNext() && deletedElements < count) {
        Entry<K, V> entry = iterator.next();
        if ((key != null && key.equals(entry.getKey())) || pinnedEntries.contains(entry.getKey())) {
          continue;
        }
        tempMap.put(entry.getKey(), entry.getValue());
        iterator.remove();
        deletedElements++;
      }
    }

    notifyListeners(tempMap);
    return tempMap.size();
  }

  public synchronized int size() {
    return this.cacheSize.get();
  }

  // TODO: Remove it using an iterator
  public synchronized void clear() {
    this.evict(Integer.MAX_VALUE);
  }

  public synchronized Set getKeySet() {
    return this.backingCache.keySet();
  }

  @Override
  public String toString() {
    return "L1ServerMapLocalCacheStoreHashMap [backingCache=" + backingCache + ", maxElementInMemory="
           + maxElementInMemory + ", pinnedEntries=" + pinnedEntries + "]";
  }

  public AtomicInteger getSizeObject() {
    return this.cacheSize;
  }
}
