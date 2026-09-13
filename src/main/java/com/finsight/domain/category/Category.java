package com.finsight.domain.category;

import java.util.Objects;

/**
 * A category used to classify transactions (e.g. "Groceries", "Salary").
 *
 * <p>Instances are immutable and validated eagerly in the constructor.
 *
 * <p><b>Equality:</b> based solely on {@link #getId() id}, matching entity identity rather than
 * comparing every field — see the equivalent note on {@code Account} for the same caveat about
 * not-yet-persisted instances.
 *
 * <p><b>Persistence id assignment:</b> as with {@code Account}, this class has no notion of a
 * "transient" versus "persisted" instance. A repository that generates a real id for a
 * not-yet-saved category calls {@link #withId(long)} to obtain a new, separate {@code Category}
 * carrying that id, rather than mutating the instance it was given.
 */
public final class Category {

    private final long id;
    private final String name;
    private final CategoryType type;

    public Category(long id, String name, CategoryType type) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be >= 0");
        }
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(type, "type must not be null");

        this.id = id;
        this.name = name;
        this.type = type;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CategoryType getType() {
        return type;
    }

    /**
     * Returns a new {@code Category} identical to this one except for its {@code id}. Does not
     * modify this instance. Intended for a repository to attach a database-generated id to an
     * otherwise-already-built category after persisting it, without needing a mutable id field.
     */
    public Category withId(long id) {
        return new Category(id, this.name, this.type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Category other)) {
            return false;
        }
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "Category{id=" + id + ", name='" + name + '\'' + ", type=" + type + '}';
    }
}
