package io.tapdata.pdk.tdd.tests.v2;

import io.tapdata.entity.event.ddl.table.TapCreateTableEvent;
import io.tapdata.entity.event.dml.TapRecordEvent;
import io.tapdata.entity.schema.TapTable;
import io.tapdata.pdk.apis.context.TapConnectorContext;
import io.tapdata.pdk.apis.entity.TestItem;
import io.tapdata.pdk.apis.entity.WriteListResult;
import io.tapdata.pdk.apis.functions.ConnectorFunctions;
import io.tapdata.pdk.apis.functions.PDKMethod;
import io.tapdata.pdk.apis.functions.connector.source.BatchReadFunction;
import io.tapdata.pdk.apis.functions.connector.source.StreamReadFunction;
import io.tapdata.pdk.apis.functions.connector.source.TableCountFunction;
import io.tapdata.pdk.apis.functions.connector.target.*;
import io.tapdata.pdk.cli.commands.TapSummary;
import io.tapdata.pdk.core.api.PDKIntegration;
import io.tapdata.pdk.core.monitor.PDKInvocationMonitor;
import io.tapdata.pdk.tdd.core.PDKTestBase;
import io.tapdata.pdk.tdd.core.SupportFunction;
import io.tapdata.pdk.tdd.tests.support.Record;
import io.tapdata.pdk.tdd.tests.support.TapAssert;
import io.tapdata.pdk.tdd.tests.support.TapGo;
import io.tapdata.pdk.tdd.tests.support.TapTestCase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static io.tapdata.entity.simplify.TapSimplify.field;
import static io.tapdata.entity.simplify.TapSimplify.table;
import static io.tapdata.entity.utils.JavaTypesToTapTypes.JAVA_Long;

@DisplayName("tableCount.test")//tableCount表数量， 必测方法
@TapGo(sort = 5)
public class TableCountTest extends PDKTestBase {
    protected TapTable targetTable = table(testTableId)
            .add(field("id", JAVA_Long).isPrimaryKey(true).primaryKeyPos(1))
            .add(field("name", "STRING"))
            .add(field("text", "STRING"));
    @DisplayName("tableCount.findTableCount")//用例1， 查询表数量
    @Test
    @TapTestCase(sort = 1)
    /**
     * 调用tableCount方法之后返回表数量大于1为正确
     * */
    void findTableCount(){
        super.consumeQualifiedTapNodeInfo(nodeInfo -> {
            TestNode prepare = this.prepare(nodeInfo);
            try {
                PDKInvocationMonitor.invoke(prepare.connectorNode(),
                        PDKMethod.INIT,
                        prepare.connectorNode()::connectorInit,
                        "Init PDK","TEST mongodb"
                );
                prepare.recordEventExecute().testCase(super.getMethod("findTableCount"));
                tableCount(prepare);
            }catch (Throwable e) {
                throw new RuntimeException(e);
            }finally {
                if (null != prepare.connectorNode()){
                    PDKInvocationMonitor.invoke(prepare.connectorNode(),
                            PDKMethod.STOP,
                            prepare.connectorNode()::connectorStop,
                            "Stop PDK",
                            "TEST mongodb"
                    );
                    PDKIntegration.releaseAssociateId("releaseAssociateId");
                }
            }
        });
    }
    int tableCount(TestNode prepare) throws Throwable {
        TapConnectorContext connectorContext = prepare.connectorNode().getConnectorContext();
        Method testCase = prepare.recordEventExecute().testCase();
        Integer tableCount = 0;
        try {
            tableCount = prepare.connectorNode().getConnector().tableCount(connectorContext);
        }catch (Exception e){
            TapAssert.asserts(()->
                    Assertions.fail(TapSummary.format("tableCount.findTableCount.errorFun",e.getMessage()))
            ).acceptAsError(this.get(),testCase, null);
        }
        int tableCountFinal = tableCount;
        TapAssert.asserts(()->
                Assertions.assertTrue(tableCountFinal > 0,
                        TapSummary.format("tableCount.findTableCount.error",tableCountFinal)
                )
        ).acceptAsError(this.get(),testCase,
                TapSummary.format("tableCount.findTableCount.succeed",tableCountFinal)
        );
        return tableCount;
    }

    @DisplayName("tableCount.findTableCountAfterNewTable")//用例2， 新建表之后查询表数量
    @Test
    @TapTestCase(sort = 2)
    /**
     * 调用tableCount方法之后获得表数量，
     * 调用CreateTableFunction新建一张表，
     * 再调用tableCount方法获得表数量，
     * 比之前的数量加1就是正确的
     * */
    void findTableCountAfterNewTable(){
        super.consumeQualifiedTapNodeInfo(nodeInfo -> {
            TestNode prepare = this.prepare(nodeInfo);
            try {
                PDKInvocationMonitor.invoke(prepare.connectorNode(),
                        PDKMethod.INIT,
                        prepare.connectorNode()::connectorInit,
                        "Init PDK","TEST mongodb"
                );

                Method testCase = super.getMethod("findTableCountAfterNewTable");
                prepare.recordEventExecute().testCase(testCase);

                //调用tableCount方法之后获得表数量，
                int tableCountNewTableAgo = tableCount(prepare);

                //调用CreateTableFunction新建一张表，
                if (!this.createTable(prepare)) return;

                //再调用tableCount方法获得表数量，
                int tableCountNewTableAfter = tableCount(prepare);

                //比之前的数量加1就是正确的 @TODO 实际场景下不一定数量高度符合预期
                TapAssert.asserts(()->
                        Assertions.assertTrue(tableCountNewTableAgo+1 == tableCountNewTableAfter,
                                TapSummary.format("tableCount.findTableCountAfterNewTable.afterNewTable.error",tableCountNewTableAgo,tableCountNewTableAfter)
                        )
                ).acceptAsWarn(this.get(),testCase,
                        TapSummary.format("tableCount.findTableCountAfterNewTable.afterNewTable.succeed",tableCountNewTableAgo,tableCountNewTableAfter)
                );

            }catch (Throwable e) {
                throw new RuntimeException(e);
            }finally {
                prepare.recordEventExecute().dropTable();
                if (null != prepare.connectorNode()){
                    PDKInvocationMonitor.invoke(prepare.connectorNode(),
                            PDKMethod.STOP,
                            prepare.connectorNode()::connectorStop,
                            "Stop PDK",
                            "TEST mongodb"
                    );
                    PDKIntegration.releaseAssociateId("releaseAssociateId");
                }
            }
        });
    }

    public static List<SupportFunction> testFunctions() {
        List<SupportFunction> supportFunctions = Arrays.asList(

        );
        return supportFunctions;
    }
}
