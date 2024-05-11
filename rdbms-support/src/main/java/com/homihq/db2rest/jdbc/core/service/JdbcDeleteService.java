package com.homihq.db2rest.jdbc.core.service;

import com.homihq.db2rest.jdbc.JdbcManager;
import com.homihq.db2rest.jdbc.core.DbOperationService;
import com.homihq.db2rest.jdbc.dto.DeleteContext;
import com.homihq.db2rest.jdbc.sql.SqlCreatorTemplate;
import com.homihq.db2rest.core.exception.GenericDataAccessException;
import com.homihq.db2rest.jdbc.config.model.DbWhere;
import com.homihq.db2rest.jdbc.config.model.DbTable;
import com.homihq.db2rest.jdbc.rsql.parser.RSQLParserBuilder;
import com.homihq.db2rest.jdbc.rsql.visitor.BaseRSQLVisitor;
import cz.jirutka.rsql.parser.ast.Node;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public class JdbcDeleteService implements DeleteService {

    private final JdbcManager jdbcManager;
    private final SqlCreatorTemplate sqlCreatorTemplate;
    private final DbOperationService dbOperationService;


    @Override
    @Transactional
    public int delete(String dbName,String schemaName, String tableName, String filter) {


        DbTable dbTable = jdbcManager.getTable(dbName, schemaName,tableName);

        DeleteContext context = DeleteContext.builder()
                .dbName(dbName)
                .tableName(tableName)
                .table(dbTable).build();



        return executeDelete(dbName, filter, dbTable, context);

    }

    private int executeDelete(String dbName, String filter, DbTable table, DeleteContext context) {

        addWhere(dbName, filter, table, context);
        String sql =
                sqlCreatorTemplate.deleteQuery(context);

        log.info("{}", sql);
        log.info("{}", context.getParamMap());

        try {
            return dbOperationService.delete(
                    jdbcManager.getNamedParameterJdbcTemplate(dbName),
                    context.getParamMap(),
                    sql);
        } catch (DataAccessException e) {
            log.error("Error in delete op : " , e);
            throw new GenericDataAccessException(e.getMostSpecificCause().getMessage());
        }
    }



    private void addWhere(String dbName, String filter, DbTable table, DeleteContext context) {

        if(StringUtils.isNotBlank(filter)) {
            context.createParamMap();

            DbWhere dbWhere = new DbWhere(
                    context.getTableName(),
                    table, null ,context.getParamMap(), "delete");

            Node rootNode = RSQLParserBuilder.newRSQLParser().parse(filter);

            String where = rootNode
                    .accept(new BaseRSQLVisitor(
                            dbWhere, jdbcManager.getDialect(dbName)));
            context.setWhere(where);

        }
    }
}