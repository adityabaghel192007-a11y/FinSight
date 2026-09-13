package com.finsight.repository.jdbc;

import com.finsight.domain.category.Category;
import com.finsight.domain.category.CategoryType;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Converts one {@code categories} row into a {@link Category}. Kept as a small, explicit,
 * hand-written mapper rather than a generic reflection-based one, matching {@code AccountMapper}:
 *
 * <ul>
 *   <li>{@code id} (SQL {@code INTEGER}) → {@code long}</li>
 *   <li>{@code name} (SQL {@code TEXT}) → {@code String}</li>
 *   <li>{@code type} (SQL {@code TEXT}) → {@link CategoryType} via {@link CategoryType#valueOf}</li>
 * </ul>
 *
 * <p>Package-private: only {@link CategoryRepositoryJdbc} needs this mapping.
 */
final class CategoryMapper {

    private CategoryMapper() {
        // stateless utility class; not instantiable
    }

    static Category fromRow(ResultSet row) throws SQLException {
        long id = row.getLong("id");
        String name = row.getString("name");
        CategoryType type = CategoryType.valueOf(row.getString("type"));

        return new Category(id, name, type);
    }
}
