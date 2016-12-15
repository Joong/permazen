
/*
 * Copyright (C) 2015 Archie L. Cobbs. All rights reserved.
 */

package org.jsimpledb.core;

import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;

import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;

import org.jsimpledb.kv.KVPair;
import org.jsimpledb.kv.KeyRange;
import org.jsimpledb.util.ByteReader;
import org.jsimpledb.util.ByteUtil;
import org.jsimpledb.util.ByteWriter;
import org.jsimpledb.util.UnsignedIntEncoder;

/**
 * A complex {@link Field}, such as a collection or map field.
 *
 * @param <T> Java type for the field's values
 */
public abstract class ComplexField<T> extends Field<T> {

    private final int storageIdLength;

    /**
     * Constructor.
     *
     * @param name the name of the field
     * @param storageId field content storage ID
     * @param schema schema version
     * @param typeToken Java type for the field's values
     * @throws IllegalArgumentException if any parameter is null
     * @throws IllegalArgumentException if {@code name} is invalid
     * @throws IllegalArgumentException if {@code storageId} is non-positive
     */
    ComplexField(String name, int storageId, Schema schema, TypeToken<T> typeToken) {
        super(name, storageId, schema, typeToken);
        this.storageIdLength = UnsignedIntEncoder.encodeLength(storageId);
    }

// Public methods

    /**
     * Get the sub-field(s) associated with this instance, ordered according to their meaning.
     *
     * @return unmodifiable list of simple fields
     */
    public abstract List<? extends SimpleField<?>> getSubFields();

// Non-public methods

    /**
     * Get the Java collection object representing the value of this instance in the given object.
     * This method does not need to do any validity checking of its parameters.
     */
    abstract T getValueInternal(Transaction tx, ObjId id);

    /**
     * Copy the Java collection object representing the value of this instance into memory and return a read-only view.
     * This method does not need to do any validity checking of its parameters.
     */
    abstract T getValueReadOnlyCopy(Transaction tx, ObjId id);

    /**
     * Iterate all values in the specified subfield.
     *
     * @throws IllegalArgumentException if {@code subField} is not a sub-field of this instance
     */
    abstract <F> Iterable<F> iterateSubField(Transaction tx, ObjId id, SimpleField<F> subField);

    @Override
    abstract ComplexFieldStorageInfo<T> toStorageInfo();

    /**
     * Delete all content (but not index entries) for the given object.
     *
     * @param tx transaction
     * @param id object id
     */
    void deleteContent(Transaction tx, ObjId id) {
        final byte[] minKey = this.buildKey(id);
        final byte[] maxKey = ByteUtil.getKeyAfterPrefix(minKey);
        this.deleteContent(tx, minKey, maxKey);
    }

    /**
     * Delete all content (but not index entries) for the given object in the given key range.
     *
     * @param tx transaction
     * @param id object id
     * @see #removeIndexEntries(Transaction, ObjId)
     */
    void deleteContent(Transaction tx, byte[] minKey, byte[] maxKey) {
        tx.kvt.removeRange(minKey, maxKey);
    }

    /**
     * Add an index entry corresponding to the given sub-field and content key/value pair.
     *
     * @param tx transaction
     * @param id object id
     * @param subField indexed sub-field
     * @param contentKey the content key
     * @param contentValue the value associated with the content key, or null if not needed
     */
    void addIndexEntry(Transaction tx, ObjId id, SimpleField<?> subField, byte[] contentKey, byte[] contentValue) {
        tx.kvt.put(this.buildIndexEntry(id, subField, contentKey, contentValue), ByteUtil.EMPTY);
    }

    /**
     * Remove an index entry corresponding to the given sub-field and content key/value pair.
     *
     * @param tx transaction
     * @param id object id
     * @param subField indexed sub-field
     * @param contentKey the content key
     * @param contentValue the value associated with the content key, or null if not needed
     */
    void removeIndexEntry(Transaction tx, ObjId id, SimpleField<?> subField, byte[] contentKey, byte[] contentValue) {
        tx.kvt.remove(this.buildIndexEntry(id, subField, contentKey, contentValue));
    }

    private byte[] buildIndexEntry(ObjId id, SimpleField<?> subField, byte[] contentKey, byte[] contentValue) {
        final ByteReader contentKeyReader = new ByteReader(contentKey);
        contentKeyReader.skip(ObjId.NUM_BYTES + this.storageIdLength);                  // skip to content
        final ByteWriter writer = new ByteWriter();
        UnsignedIntEncoder.write(writer, subField.storageId);
        this.buildIndexEntry(id, subField, contentKeyReader, contentValue, writer);
        return writer.getBytes();
    }

    /**
     * Build an index key for the given object, sub-field, and content key/value pair.
     *
     * @param id object id
     * @param subField indexed sub-field
     * @param reader reader of content key, positioned just after the object ID and the storage ID for this field
     * @param value the value associated with the content key, or null if not needed
     * @param writer writer for the index entry, with the sub-field's storage ID already written
     */
    abstract void buildIndexEntry(ObjId id, SimpleField<?> subField, ByteReader reader, byte[] value, ByteWriter writer);

    /**
     * Add or remove index entries for the given object as appropriate after a schema version change
     * which changed only whether some or all sub-field(s) are indexed.
     *
     * @param kvt KV store
     * @param oldField compatible field in older schema
     * @param id object id
     */
    void updateSubFieldIndexes(Transaction tx, ComplexField<?> oldField, ObjId id) {
        final Iterator<? extends SimpleField<?>> oldSubFields = oldField.getSubFields().iterator();
        final Iterator<? extends SimpleField<?>> newSubFields = this.getSubFields().iterator();
        while (oldSubFields.hasNext() || newSubFields.hasNext()) {
            final SimpleField<?> oldSubField = oldSubFields.next();
            final SimpleField<?> newSubField = newSubFields.next();
            if (!oldSubField.indexed && newSubField.indexed)
                this.addIndexEntries(tx, id, newSubField);
            else if (oldSubField.indexed && !newSubField.indexed)
                oldField.removeIndexEntries(tx, id, oldSubField);
        }
    }

    /**
     * Add all index entries for the given object and sub-field.
     *
     * @param tx transaction
     * @param id object id
     * @param subField sub-field of this field
     */
    void addIndexEntries(Transaction tx, ObjId id, SimpleField<?> subField) {
        Preconditions.checkArgument(subField.indexed, "not indexed");
        final byte[] prefix = this.buildKey(id);
        final byte[] prefixEnd = ByteUtil.getKeyAfterPrefix(prefix);
        final Iterator<KVPair> i = tx.kvt.getRange(prefix, prefixEnd, false);
        while (i.hasNext()) {
            final KVPair pair = i.next();
            assert KeyRange.forPrefix(prefix).contains(pair.getKey());
            this.addIndexEntry(tx, id, subField, pair.getKey(), pair.getValue());
        }
        Database.closeIfPossible(i);
    }

    /**
     * Remove all index entries for the given object.
     *
     * @param tx transaction
     * @param id object id
     */
    void removeIndexEntries(Transaction tx, ObjId id) {
        this.getSubFields().stream()
          .filter(subField -> subField.indexed)
          .forEach(subField -> this.removeIndexEntries(tx, id, subField));
    }

    /**
     * Remove all index entries for the given object and sub-field.
     *
     * @param tx transaction
     * @param id object id
     * @param subField sub-field of this field
     */
    void removeIndexEntries(Transaction tx, ObjId id, SimpleField<?> subField) {
        final byte[] prefix = this.buildKey(id);
        this.removeIndexEntries(tx, id, subField, prefix, ByteUtil.getKeyAfterPrefix(prefix));
    }

    /**
     * Remove index entries for the given object and sub-field, restricted to the given key range.
     *
     * @param tx transaction
     * @param id object id
     * @param subField sub-field of this field
     */
    void removeIndexEntries(Transaction tx, ObjId id, SimpleField<?> subField, byte[] minKey, byte[] maxKey) {
        Preconditions.checkArgument(subField.indexed, "not indexed");
        final Iterator<KVPair> i = tx.kvt.getRange(minKey, maxKey, false);
        while (i.hasNext()) {
            final KVPair pair = i.next();
            assert new KeyRange(minKey, maxKey).contains(pair.getKey());
            this.removeIndexEntry(tx, id, subField, pair.getKey(), pair.getValue());
        }
        Database.closeIfPossible(i);
    }

    /**
     * Remove all field entries in which the specified reference sub-field refers to an object
     * type that is in the specified set of newly disallowed object types.
     */
    abstract void unreferenceRemovedObjectTypes(Transaction tx,
      ObjId id, ReferenceField subField, SortedSet<Integer> removedStorageIds);
}

