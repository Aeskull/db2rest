package com.homihq.db2rest.jdbc.config.dialect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homihq.db2rest.core.exception.GenericDataAccessException;
import com.homihq.db2rest.jdbc.config.model.ArrayTypeValueHolder;
import com.homihq.db2rest.jdbc.config.model.DbTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.postgresql.jdbc.PgArray;
import org.postgresql.util.PGobject;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


@RequiredArgsConstructor
@Slf4j
public class PostGreSQLDialect implements Dialect {

    private final ObjectMapper objectMapper;
    private String coverChar = "\"";

    //Use during insert, bulk-insert, update
    @Override
    public void processTypes(DbTable table, List<String> insertableColumns, Map<String, Object> data) {

        for (String columnName : insertableColumns) {
            Object value = data.get(columnName);

            String columnDataTypeName = table.getColumnDataTypeName(columnName);

            log.info("columnName : {} || columnDataTypeName - {}", columnName, columnDataTypeName);
            if (Objects.isNull(value)) continue;

            if (StringUtils.equalsAnyIgnoreCase(columnDataTypeName, "json", "jsonb")) {
                Object v = convertToJson(value, columnDataTypeName);
                data.put(columnName, v);
            } else if (StringUtils.equalsAnyIgnoreCase(columnDataTypeName, "timestamp")) {
                LocalDateTime v = convertToLocalDateTime((String) value);
                data.put(columnName, v);

            } else if (StringUtils.equalsAnyIgnoreCase(columnDataTypeName, "timestamptz")) {
                OffsetDateTime v = convertToOffsetDateTime((String) value);
                data.put(columnName, v);

            } else if (StringUtils.equalsAnyIgnoreCase(columnDataTypeName, "timetz")) {
                OffsetTime v = convertToOffsetTime((String) value);
                data.put(columnName, v);
            } else if (StringUtils.equalsAnyIgnoreCase(columnDataTypeName, "int4", "int2", "int8", "int")) {
                data.put(columnName, Long.valueOf(value.toString().trim()));
            } else if (StringUtils.equalsAnyIgnoreCase(columnDataTypeName, "numeric")) {
                data.put(columnName, Double.valueOf(value.toString().trim()));
            }
            else if (StringUtils.equalsAnyIgnoreCase(columnDataTypeName, "year")) {
                data.put(columnName, Integer.valueOf(value.toString().trim()));
            }
            else if (StringUtils.equalsAnyIgnoreCase(columnDataTypeName, "_varchar")) {

                log.info("Array type found");

                data.put(columnName, new ArrayTypeValueHolder("java.sql.Array", "varchar",
                        ((ArrayList) value).toArray() ));
            }

        }

    }

    private OffsetTime convertToOffsetTime(String value) {
        try {
            return OffsetTime.parse(value, DateTimeFormatter.ISO_OFFSET_TIME);
        } catch (Exception e) {
            throw new GenericDataAccessException("Error converting to OffsetTime type - " + e.getLocalizedMessage());
        }
    }

    private LocalDateTime convertToLocalDateTime(String value) {
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            throw new GenericDataAccessException("Error converting to LocalDateTime type - " + e.getLocalizedMessage());
        }
    }

    private OffsetDateTime convertToOffsetDateTime(String value) {
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            throw new GenericDataAccessException("Error converting to OffsetDateTime type - " + e.getLocalizedMessage());
        }
    }

    private Object convertToJson(Object value, String columnDataTypeName) {
        try {
            PGobject pGobject = new PGobject();
            pGobject.setType(columnDataTypeName);
            pGobject.setValue(objectMapper.writeValueAsString(value));

            return pGobject;
        } catch (Exception e) {
            throw new GenericDataAccessException("Error converting to JSON type - " + e.getLocalizedMessage());
        }
    }

    @Override
    public Object convertJsonToVO(Object object) {

        if(Objects.nonNull(object)) {

            PGobject pGobject = (PGobject) object;

            String val = pGobject.getValue();

            try {
                return objectMapper.readValue(val, Object.class);
            } catch (JsonProcessingException e) {
                throw new GenericDataAccessException("Error converting to JSON type - " + e.getLocalizedMessage());
            }
        }
        else{
            return null;
        }

    }

    private String getQuotedName(String name) {
        return coverChar + name + coverChar;
    }

    @Override
    public String renderTableName(DbTable table, boolean containsWhere, boolean deleteOp) {
        return getQuotedName(table.schema()) + "." + getQuotedName(table.name()) + " " + table.alias();
    }

    @Override
    public String renderTableNameWithoutAlias(DbTable table) {
        return getQuotedName(table.schema()) + "." + getQuotedName(table.name());
    }

    @Override
    public boolean isSupportedDb(String productName, int majorVersion) {
        return StringUtils.equalsIgnoreCase(productName, "PostGreSQL");
    }

    @Override
    public List<String> convertToStringArray(Object object) {

        if(Objects.nonNull(object)) {

            PgArray pgArray = (PgArray)object;
            try {
                Object o = pgArray.getArray();
                return Arrays.asList((String[])o);
            } catch (Exception e) {
                throw new GenericDataAccessException("Error converting to Array type - " + e.getLocalizedMessage());
            }

        }

        return Dialect.super.convertToStringArray(object);
    }
}
