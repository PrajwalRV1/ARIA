package com.company.user.config;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.EnumType;
import org.hibernate.usertype.DynamicParameterizedType;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.*;
import java.util.Objects;
import java.util.Properties;

/**
 * Custom Hibernate UserType for PostgreSQL enums.
 * This ensures proper casting when saving Java enums to PostgreSQL enum columns.
 */
public class PostgreSQLEnumType implements UserType<Enum<?>>, DynamicParameterizedType {
    
    private Class<Enum<?>> enumClass;
    private String enumTypeName;

    @Override
    public void setParameterValues(Properties parameters) {
        final ParameterType reader = (ParameterType) parameters.get(PARAMETER_TYPE);
        if (reader != null) {
            enumClass = (Class<Enum<?>>) reader.getReturnedClass().asSubclass(Enum.class);
            enumTypeName = parameters.getProperty("enumTypeName", enumClass.getSimpleName().toLowerCase());
        }
    }

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<Enum<?>> returnedClass() {
        return enumClass;
    }

    @Override
    public boolean equals(Enum<?> x, Enum<?> y) {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(Enum<?> x) {
        return Objects.hashCode(x);
    }

    @Override
    public Enum<?> nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) 
            throws SQLException {
        String value = rs.getString(position);
        if (value == null) {
            return null;
        }
        return Enum.valueOf((Class) enumClass, value);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Enum<?> value, int index, SharedSessionContractImplementor session) 
            throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            st.setObject(index, value.name(), Types.OTHER);
        }
    }

    @Override
    public Enum<?> deepCopy(Enum<?> value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(Enum<?> value) {
        return value.name();
    }

    @Override
    public Enum<?> assemble(Serializable cached, Object owner) {
        return Enum.valueOf((Class) enumClass, (String) cached);
    }
}
