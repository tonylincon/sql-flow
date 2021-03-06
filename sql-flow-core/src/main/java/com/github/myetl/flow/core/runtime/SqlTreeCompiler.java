package com.github.myetl.flow.core.runtime;


import com.github.myetl.flow.core.exception.FlowException;
import com.github.myetl.flow.core.exception.SqlCompileException;
import com.github.myetl.flow.core.exception.SqlRunException;
import com.github.myetl.flow.core.parser.DDL;
import com.github.myetl.flow.core.parser.DML;
import com.github.myetl.flow.core.parser.SqlTree;
import com.github.myetl.flow.core.util.FlinkFieldTypeUtil;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.sinks.TableSink;
import org.apache.flink.table.sources.TableSource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * compile to flink
 */
public class SqlTreeCompiler {


    public static void compile(SqlTree sqlTree, TableEnvironment env, ExecutionConfig executionConfig) throws FlowException {
        Map<String, DDL> tables = new LinkedHashMap<>();
        for (DDL ddl : sqlTree.getDdls()) {
            tables.put(ddl.getTableName(), ddl);
        }
        Map<String, DDL> source = new LinkedHashMap<>();
        Map<String, DDL> sink = new LinkedHashMap<>();

        for (DML dml : sqlTree.getDmls()) {
            DDL target = tables.get(dml.getTargetTable());
            if (target == null)
                throw new SqlCompileException(String.format("table [%s] has no DDL", dml.getTargetTable()));
            sink.put(target.getTableName(), target);
            for (String s : dml.getSourceTable()) {
                DDL sourceTable = tables.get(s);
                if (sourceTable == null) throw new SqlCompileException(String.format("table [%s] as no DDL", s));
                source.put(sourceTable.getTableName(), sourceTable);
            }
        }

        try {
            for (DDL s : source.values()) {
                RowTypeInfo rowTypeInfo = FlinkFieldTypeUtil.toFlinkType(s);
                TableSource tableSource = DDLCompileFactory.getTableSource(s, rowTypeInfo, executionConfig);

                env.registerTableSource(s.getTableName(), tableSource);
            }
            for (DDL s : sink.values()) {
                RowTypeInfo rowTypeInfo = FlinkFieldTypeUtil.toFlinkType(s);
                TableSink tableSink = DDLCompileFactory.getTableSink(s, rowTypeInfo, executionConfig);
                env.registerTableSink(s.getTableName(), rowTypeInfo.getFieldNames(), rowTypeInfo.getFieldTypes(), tableSink);
            }
        } catch (Exception e) {
            throw new SqlCompileException(e);
        }

        try {
            for (DML dml : sqlTree.getDmls()) {
                env.sqlUpdate(dml.getSql());
            }
        } catch (Exception e) {
            throw new SqlRunException(e);
        }
    }

    /**
     * check the parsed sql tree
     *
     * @param sqlTree
     * @return
     */
    public boolean check(SqlTree sqlTree) throws SqlCompileException {
        // todo

        return true;
    }

}
