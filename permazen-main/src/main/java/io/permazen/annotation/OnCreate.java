
/*
 * Copyright (C) 2015 Archie L. Cobbs. All rights reserved.
 */

package io.permazen.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates Permazen model class methods that are to be invoked whenever a database object is newly created.
 *
 * <p>
 * Note that there is a subtle distinction between (a) the creation of database objects in the database, and
 * (b) the instantiation of Java model objects that represent database objects (i.e., {@link io.permazen.JObject}s).
 * These two events do not occur at the same time; in particular, distinct Java model objects are instantiated to
 * represent the same database object in different transactions. In addition, it's even possible for a Java model
 * object to be instantiated when no corresponding database object exists in the database, e.g., via
 * {@link io.permazen.JTransaction#get(io.permazen.core.ObjId)}.
 *
 * <p>
 * Methods that are annotated with {@link OnCreate &#64;OnCreate} are invoked only for events of type (a).
 * As a consequence, for any database fields that require default initialization, this initialization should be
 * performed not in a Java constructor but rather in an {@link OnCreate &#64;OnCreate}-annotated method.
 *
 * <p>
 * For example, instead of this:
 * <pre>
 *  &#64;PermazenType
 *  public abstract class Event {
 *
 *      protected Event() {
 *          this.setCreateTime(new Date());
 *      }
 *
 *      &#64;NotNull
 *      public abstract Date getCreateTime();
 *      public abstract void setCreateTime(Date createTime);
 *
 *      ...
 * </pre>
 *
 * <p>
 * do this:
 * <pre>
 *  &#64;PermazenType
 *  public abstract class Event {
 *
 *      &#64;OnCreate
 *      private void initializeCreateTime() {
 *          this.setCreateTime(new Date());
 *      }
 *
 *      &#64;NotNull
 *      public abstract Date getCreateTime();
 *      public abstract void setCreateTime(Date createTime);
 *
 *      ...
 * </pre>
 *
 * <p>
 * Notifications are delivered in the same thread that created the object, immediately after the object is created.
 *
 * <p>
 * The annotated method must be an instance method (i.e., not static), return void, and take zero parameters.
 * It may have any level of access, including {@code private}.
 *
 * <p><b>Meta-Annotations</b></p>
 *
 * <p>
 * This annotation may be configured indirectly as a Spring
 * <a href="https://docs.spring.io/spring/docs/current/spring-framework-reference/htmlsingle/#beans-meta-annotations">meta-annotation</a>
 * when {@code spring-core} is on the classpath.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.ANNOTATION_TYPE, ElementType.METHOD })
@Documented
public @interface OnCreate {

    /**
     * Determines whether this annotation should also be enabled for
     * {@linkplain io.permazen.SnapshotJTransaction snapshot transaction} objects.
     * If unset, notifications will only be delivered to non-snapshot (i.e., normal) database instances.
     *
     * @return whether enabled for snapshot transactions
     * @see io.permazen.SnapshotJTransaction
     */
    boolean snapshotTransactions() default false;
}

