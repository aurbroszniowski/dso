/*
 * All content copyright (c) 2003-2008 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.tc.objectserver.persistence.sleepycat;

import com.sleepycat.bind.serial.ClassCatalog;
import com.tc.exception.TCRuntimeException;
import com.tc.logging.TCLogger;
import com.tc.object.ObjectID;
import com.tc.objectserver.core.api.ManagedObject;
import com.tc.objectserver.core.api.ManagedObjectState;
import com.tc.objectserver.mgmt.ObjectStatsRecorder;
import com.tc.objectserver.persistence.TCObjectDatabase;
import com.tc.objectserver.persistence.TCRootDatabase;
import com.tc.objectserver.persistence.api.ManagedObjectPersistor;
import com.tc.objectserver.persistence.api.PersistenceTransaction;
import com.tc.objectserver.persistence.api.PersistenceTransactionProvider;
import com.tc.objectserver.persistence.api.PersistentCollectionsUtil;
import com.tc.objectserver.persistence.sleepycat.SleepycatPersistor.SleepycatPersistorBase;
import com.tc.properties.TCPropertiesConsts;
import com.tc.properties.TCPropertiesImpl;
import com.tc.statistics.util.NullStatsRecorder;
import com.tc.statistics.util.StatsPrinter;
import com.tc.statistics.util.StatsRecorder;
import com.tc.text.PrettyPrintable;
import com.tc.text.PrettyPrinter;
import com.tc.util.Assert;
import com.tc.util.NullSyncObjectIdSet;
import com.tc.util.ObjectIDSet;
import com.tc.util.SyncObjectIdSet;
import com.tc.util.SyncObjectIdSetImpl;
import com.tc.util.sequence.MutableSequence;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Map.Entry;

public final class ManagedObjectPersistorImpl extends SleepycatPersistorBase implements ManagedObjectPersistor,
    PrettyPrintable {

  private static final Comparator                 MO_COMPARATOR          = new Comparator() {
                                                                           public int compare(Object o1, Object o2) {
                                                                             long oid1 = ((ManagedObject) o1).getID()
                                                                                 .toLong();
                                                                             long oid2 = ((ManagedObject) o2).getID()
                                                                                 .toLong();
                                                                             if (oid1 < oid2) {
                                                                               return -1;
                                                                             } else if (oid1 > oid2) {
                                                                               return 1;
                                                                             } else {
                                                                               return 0;
                                                                             }
                                                                           }
                                                                         };

  private static final Object                     MO_PERSISTOR_KEY       = ManagedObjectPersistorImpl.class.getName()
                                                                           + ".saveAllObjects";
  private static final Object                     MO_PERSISTOR_VALUE     = "Complete";

  private static final boolean                    MEASURE_PERF           = TCPropertiesImpl
                                                                             .getProperties()
                                                                             .getBoolean(
                                                                                         TCPropertiesConsts.L2_OBJECTMANAGER_PERSISTOR_MEASURE_PERF,
                                                                                         false);
  private static final int                        INTEGER_MAX_80_PERCENT = (int) (Integer.MAX_VALUE * 0.8);

  private final TCObjectDatabase                  objectDB;
  private final SerializationAdapterFactory       saf;
  private final MutableSequence                   objectIDSequence;
  private final TCRootDatabase                    rootDB;
  private long                                    saveCount;
  private final TCLogger                          logger;
  private final PersistenceTransactionProvider    ptp;
  private final ClassCatalog                      classCatalog;
  private final SleepycatCollectionsPersistor     collectionsPersistor;
  private final ObjectIDManager                   objectIDManager;
  private final SyncObjectIdSet                   extantObjectIDs;
  private final SyncObjectIdSet                   extantMapTypeOidSet;
  private final ObjectStatsRecorder               objectStatsRecorder;
  private final StatsRecorder                     perfMeasureStats;

  private final ThreadLocal<SerializationAdapter> threadlocalAdapter;

  public ManagedObjectPersistorImpl(TCLogger logger, ClassCatalog classCatalog,
                                    SerializationAdapterFactory serializationAdapterFactory, BerkeleyDBEnvironment env,
                                    MutableSequence objectIDSequence, TCRootDatabase rootDB,
                                    PersistenceTransactionProvider ptp,
                                    SleepycatCollectionsPersistor collectionsPersistor, boolean paranoid,
                                    ObjectStatsRecorder objectStatsRecorder) throws TCDatabaseException {
    this.logger = logger;
    this.classCatalog = classCatalog;
    this.saf = serializationAdapterFactory;
    this.objectDB = env.getObjectDatabase();
    this.objectIDSequence = objectIDSequence;
    this.rootDB = rootDB;
    this.ptp = ptp;
    this.collectionsPersistor = collectionsPersistor;

    this.threadlocalAdapter = initializethreadlocalAdapter();

    boolean oidFastLoad = TCPropertiesImpl.getProperties()
        .getBoolean(TCPropertiesConsts.L2_OBJECTMANAGER_LOADOBJECTID_FASTLOAD);
    if (!paranoid) {
      this.objectIDManager = new NullObjectIDManager();
    } else if (oidFastLoad) {
      // read objectIDs from compressed DB
      MutableSequence sequence = new SleepycatSequence(this.ptp, logger,
                                                       SleepycatSequenceKeys.OID_STORE_LOG_SEQUENCE_DB_NAME, 1000, env
                                                           .getGlobalSequenceDatabase());
      this.objectIDManager = new FastObjectIDManagerImpl(env, ptp, sequence, this);
    } else {
      // read objectIDs from object DB
      this.objectIDManager = new PlainObjectIDManagerImpl(this.objectDB, ptp);
    }

    this.extantObjectIDs = getAllObjectIDs();
    this.extantMapTypeOidSet = getAllMapsObjectIDs();

    this.objectStatsRecorder = objectStatsRecorder;

    if (MEASURE_PERF) {
      this.perfMeasureStats = new StatsPrinter(
                                               new MessageFormat("Deletes in the Last {0} ms"),
                                               new MessageFormat(
                                               // " count = {0,number,#} collections mo state = {1,number,#} time taken
                                                                 // =
                                                                 // {2,number, #}"
                                                                 " total count = {0}   collections mo state = {1}   time taken = {2} nanos"),
                                               false);
    } else {
      this.perfMeasureStats = new NullStatsRecorder();
    }
  }

  public int getObjectCount() {
    return this.extantObjectIDs.size();
  }

  public boolean addNewObject(ObjectID id) {
    int size = this.extantObjectIDs.addAndGetSize(id);
    if (size < 0) {
      // not added
      return false;
    } else if (size > INTEGER_MAX_80_PERCENT && size % 10000 == 0) {
      this.logger.warn("Total number of objects in the system close to MAX supported : " + size + " MAX : "
                       + Integer.MAX_VALUE);
    }
    return true;
  }

  public boolean containsObject(ObjectID id) {
    return this.extantObjectIDs.contains(id);
  }

  public void removeAllObjectsByID(SortedSet<ObjectID> ids) {
    this.extantObjectIDs.removeAll(ids);
  }

  public ObjectIDSet snapshotObjects() {
    return this.extantObjectIDs.snapshot();
  }

  public boolean containsMapType(ObjectID id) {
    return this.extantMapTypeOidSet.contains(id);
  }

  public boolean addMapTypeObject(ObjectID id) {
    return this.extantMapTypeOidSet.add(id);
  }

  public void removeAllMapTypeObject(Collection ids) {
    this.extantMapTypeOidSet.removeAll(ids);
  }

  public long nextObjectIDBatch(int batchSize) {
    return this.objectIDSequence.nextBatch(batchSize);
  }

  public long currentObjectIDValue() {
    return this.objectIDSequence.current();
  }

  public void setNextAvailableObjectID(long startID) {
    this.objectIDSequence.setNext(startID);
  }

  public void addRoot(PersistenceTransaction tx, String name, ObjectID id) {
    validateID(id);
    boolean status = false;
    try {
      byte[] rootNameInBytes = setStringData(name);

      status = this.rootDB.put(rootNameInBytes, id.toLong(), tx);
    } catch (Throwable t) {
      throw new DBException(t);
    }
    if (!status) { throw new DBException("Unable to write root id: " + name + "=" + id + "; status: " + status); }
  }

  public ObjectID loadRootID(String name) {
    if (name == null) { throw new AssertionError("Attempt to retrieve a null root name"); }
    try {
      byte[] rootNameInByte = setStringData(name);
      PersistenceTransaction tx = this.ptp.newTransaction();

      return new ObjectID(this.rootDB.get(rootNameInByte, tx));
    } catch (Throwable t) {
      throw new DBException(t);
    }
  }

  public Set loadRoots() {
    PersistenceTransaction tx = this.ptp.newTransaction();
    return rootDB.getRootIds(tx);
  }

  public SyncObjectIdSet getAllObjectIDs() {
    SyncObjectIdSet rv = new SyncObjectIdSetImpl();
    rv.startPopulating();
    Thread t = new Thread(this.objectIDManager.getObjectIDReader(rv), "ObjectIdReaderThread");
    t.setDaemon(true);
    t.start();
    return rv;
  }

  public SyncObjectIdSet getAllMapsObjectIDs() {
    SyncObjectIdSet rv = new SyncObjectIdSetImpl();

    Runnable reader = this.objectIDManager.getMapsObjectIDReader(rv);
    if (reader == null) { return new NullSyncObjectIdSet(); }

    rv.startPopulating();
    Thread t = new Thread(reader, "MapsObjectIdReaderThread");
    t.setDaemon(true);
    t.start();
    return rv;
  }

  public Set loadRootNames() {
    Set rv = new HashSet();
    PersistenceTransaction tx = this.ptp.newTransaction();

    List<byte[]> rootNamesInBytes = rootDB.getRootNames(tx);
    try {
      for (byte[] rootNameInBytes : rootNamesInBytes) {
        rv.add(getStringData(rootNameInBytes));
      }
    } catch (Throwable t) {
      throw new DBException(t);
    }

    return rv;
  }

  public Map loadRootNamesToIDs() {
    Map rv = new HashMap();
    try {
      PersistenceTransaction tx = this.ptp.newTransaction();
      Map<byte[], Long> mapFromDB = rootDB.getRootNamesToId(tx);

      for (Entry<byte[], Long> entry : mapFromDB.entrySet()) {
        String rootName = getStringData(entry.getKey());
        ObjectID oid = new ObjectID(entry.getValue());
        rv.put(rootName, oid);
      }
    } catch (Throwable t) {
      throw new DBException(t);
    }
    return rv;
  }

  public ManagedObject loadObjectByID(ObjectID id) {
    validateID(id);
    PersistenceTransaction tx = this.ptp.newTransaction();
    try {
      byte[] value = null;
      value = this.objectDB.get(id.toLong(), tx);
      if (value != null) {
        ManagedObject mo = getManagedObjectData(value);
        loadCollection(tx, mo);
        tx.commit();
        return mo;
      } else {
        return null;
      }
    } catch (Throwable e) {
      abortOnError(tx);
      throw new DBException(e);
    }
  }

  private void loadCollection(PersistenceTransaction tx, ManagedObject mo) throws TCDatabaseException {
    ManagedObjectState state = mo.getManagedObjectState();
    if (PersistentCollectionsUtil.isPersistableCollectionType(state.getType())) {
      try {
        this.collectionsPersistor.loadCollectionsToManagedState(tx, mo.getID(), state);
      } catch (Exception e) {
        throw new TCDatabaseException(e.getMessage());
      }
    }
  }

  public void saveObject(PersistenceTransaction persistenceTransaction, ManagedObject managedObject) {
    Assert.assertNotNull(managedObject);
    validateID(managedObject.getID());
    boolean status = false;
    try {
      status = basicSaveObject(persistenceTransaction, managedObject);
      if (status && managedObject.isNew()) {
        status = this.objectIDManager.put(persistenceTransaction, managedObject);
      }
    } catch (DBException e) {
      throw e;
    } catch (Throwable t) {
      throw new DBException("Trying to save object: " + managedObject, t);
    }

    if (!status) { throw new DBException("Unable to write ManagedObject: " + managedObject + "; status: " + status); }
  }

  private boolean basicSaveObject(PersistenceTransaction tx, ManagedObject managedObject) throws TCDatabaseException,
      IOException {
    if (!managedObject.isDirty()) { return true; }
    boolean status;
    byte[] value = setManagedObjectData(managedObject);
    int length = value.length;
    length += 8;
    try {
      status = this.objectDB.put(managedObject.getID().toLong(), value, tx);
      if (status) {
        length += basicSaveCollection(tx, managedObject);
        managedObject.setIsDirty(false);
        this.saveCount++;
        if (this.saveCount == 1 || this.saveCount % (100 * 1000) == 0) {
          this.logger.debug("saveCount: " + this.saveCount);
        }
      }
      if (this.objectStatsRecorder.getCommitDebug()) {
        updateStats(managedObject, length);
      }
    } catch (Exception de) {
      throw new TCDatabaseException(de.getMessage());
    }
    return status;
  }

  private void updateStats(ManagedObject managedObject, int length) {
    String className = managedObject.getManagedObjectState().getClassName();
    record(className, length, managedObject.isNew());
  }

  private void record(String className, int length, boolean isNew) {
    this.objectStatsRecorder.updateCommitStats(className, length, isNew); // count, bytes written, new
  }

  // logger.info("Deletes count:" + deleteCounter + " delete state count:" + deletePersistentStateCounter
  // + " usedTime(ns): " + deleteTime);

  private int basicSaveCollection(PersistenceTransaction tx, ManagedObject managedObject) throws TCDatabaseException {
    ManagedObjectState state = managedObject.getManagedObjectState();
    if (PersistentCollectionsUtil.isPersistableCollectionType(state.getType())) {
      try {
        return this.collectionsPersistor.saveCollections(tx, state);
      } catch (Exception e) {
        throw new TCDatabaseException(e.getMessage());
      }
    }
    return 0;
  }

  public void saveAllObjects(PersistenceTransaction persistenceTransaction, Collection managedObjects) {
    long t0 = System.currentTimeMillis();
    if (managedObjects.isEmpty()) { return; }
    Object failureContext = null;

    // XXX:: We are sorting so that we maintain lock ordering when writing to sleepycat (check
    // SleepycatPersistableMap.basicClear()). This is done under the assumption that this method is not called
    // twice with the same transaction
    Object old = persistenceTransaction.setProperty(MO_PERSISTOR_KEY, MO_PERSISTOR_VALUE);
    Assert.assertNull(old);
    SortedSet sortedList = getSortedManagedObjectsSet(managedObjects);
    SortedSet oidSet = new TreeSet();

    try {
      for (Iterator i = sortedList.iterator(); i.hasNext();) {
        final ManagedObject managedObject = (ManagedObject) i.next();

        final boolean status = basicSaveObject(persistenceTransaction, managedObject);

        if (!status) {
          failureContext = new Object() {
            @Override
            public String toString() {
              return "Unable to save ManagedObject: " + managedObject + "; status: " + status;
            }
          };
          break;
        }

        // record new object-IDs to be written to persistent store later.
        if (managedObject.isNew()) {
          this.objectIDManager.prePutAll(oidSet, managedObject);
        }
      }
      if (!this.objectIDManager.putAll(persistenceTransaction, oidSet)) {
        //
        throw new DBException("Failed to save Object-IDs");
      }
    } catch (Throwable t) {
      throw new DBException(t);
    }

    if (failureContext != null) { throw new DBException(failureContext.toString()); }

    long delta = System.currentTimeMillis() - t0;
    this.saveAllElapsed += delta;
    this.saveAllCount++;
    this.saveAllObjectCount += managedObjects.size();
    if (this.saveAllCount % (100 * 1000) == 0) {
      double avg = ((double) this.saveAllObjectCount / (double) this.saveAllElapsed) * 1000;
      this.logger.debug("save time: " + delta + ", " + managedObjects.size() + " objects; avg: " + avg + "/sec");
    }
  }

  private SortedSet getSortedManagedObjectsSet(Collection managedObjects) {
    TreeSet sorted = new TreeSet(MO_COMPARATOR);
    sorted.addAll(managedObjects);
    Assert.assertEquals(managedObjects.size(), sorted.size());
    return sorted;
  }

  private long saveAllCount       = 0;
  private long saveAllObjectCount = 0;
  private long saveAllElapsed     = 0;

  private void deleteObjectByID(PersistenceTransaction tx, ObjectID id) {
    validateID(id);
    try {
      boolean status = this.objectDB.delete(id.toLong(), tx);
      if (!status) {
        // make the formatter happy
        throw new DBException("Unable to remove ManagedObject for object id: " + id + ", status: " + status);
      } else {
        long startTime = 0;
        boolean isMapType = false;
        if (MEASURE_PERF) {
          startTime = System.nanoTime();
        }
        if (containsMapType(id)) {
          isMapType = true;
          // may return false if ManagedObject persistent state empty
          this.collectionsPersistor.deleteCollection(tx, id);
        }
        if (MEASURE_PERF) {
          this.perfMeasureStats.updateStats("Managed Objects deleted ", new long[] { 1, (isMapType ? 1 : 0),
              (System.nanoTime() - startTime) });
        }
      }
    } catch (TCDatabaseException t) {
      throw new DBException(t);
    }
  }

  /*
   * This method takes a SortedSet of Object ID to delete for two reasons. 1) to maintain lock ordering - check
   * saveAllObjects 2) for performance reason
   */
  public void deleteAllObjectsByID(PersistenceTransaction tx, SortedSet<ObjectID> sortedOids) {
    for (Object element : sortedOids) {
      ObjectID objectId = (ObjectID) element;
      deleteObjectByID(tx, objectId);
    }

    try {
      this.objectIDManager.deleteAll(tx, sortedOids);
      removeAllMapTypeObject(sortedOids);
    } catch (TCDatabaseException de) {
      throw new TCRuntimeException(de);
    }
  }

  /**
   * This is only package protected for tests.
   */
  SerializationAdapter getSerializationAdapter() {
    return this.threadlocalAdapter.get();
  }

  private ThreadLocal<SerializationAdapter> initializethreadlocalAdapter() {
    ThreadLocal<SerializationAdapter> threadlclAdapter = new ThreadLocal<SerializationAdapter>() {
      @Override
      protected SerializationAdapter initialValue() {
        try {
          return ManagedObjectPersistorImpl.this.saf.newAdapter(ManagedObjectPersistorImpl.this.classCatalog);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
    return threadlclAdapter;
  }

  /*********************************************************************************************************************
   * Private stuff
   */

  private void validateID(ObjectID id) {
    Assert.assertNotNull(id);
    Assert.eval(!ObjectID.NULL_ID.equals(id));
  }

  private byte[] setStringData(String string) throws IOException {
    return getSerializationAdapter().serializeString(string);
  }

  private byte[] setManagedObjectData(ManagedObject mo) throws IOException {
    return getSerializationAdapter().serializeManagedObject(mo);
  }

  private String getStringData(byte[] entry) throws IOException, ClassNotFoundException {
    return getSerializationAdapter().deserializeString(entry);
  }

  private ManagedObject getManagedObjectData(byte[] entry) throws IOException, ClassNotFoundException {
    return getSerializationAdapter().deserializeManagedObject(entry);
  }

  public PrettyPrinter prettyPrint(PrettyPrinter out) {
    out.println(this.getClass().getName());
    out = out.duplicateAndIndent();
    out.println("db: " + this.objectDB);
    out.indent().print("extantObjectIDs: ").visit(this.extantObjectIDs).println();
    out.indent().print("extantMapTypeOidSet: ").visit(this.extantMapTypeOidSet).println();
    return out;
  }

  // for testing purpose only
  ObjectIDManager getOibjectIDManager() {
    return this.objectIDManager;
  }
}
