package io.tapdata.autoinspect.connector.pdk;

import io.tapdata.autoinspect.connector.IDataCursor;
import io.tapdata.autoinspect.entity.CompareRecord;
import io.tapdata.entity.codec.TapCodecsRegistry;
import io.tapdata.entity.codec.filter.TapCodecsFilterManager;
import io.tapdata.entity.schema.TapTable;
import io.tapdata.entity.utils.DataMap;
import io.tapdata.pdk.apis.entity.Projection;
import io.tapdata.pdk.apis.entity.QueryOperator;
import io.tapdata.pdk.apis.entity.SortOn;
import io.tapdata.pdk.apis.entity.TapAdvanceFilter;
import io.tapdata.pdk.apis.functions.connector.target.QueryByAdvanceFilterFunction;
import io.tapdata.pdk.core.api.ConnectorNode;
import io.tapdata.pdk.core.monitor.PDKInvocationMonitor;
import io.tapdata.pdk.core.monitor.PDKMethod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:harsen_lin@163.com">Harsen</a>
 * @version v1.0 2022/8/9 10:43 Create
 */
public class PdkQueryCursor implements IDataCursor<CompareRecord> {
    private static final Logger logger = LogManager.getLogger(PdkQueryCursor.class);
    private static final String TAG = PdkQueryCursor.class.getSimpleName();
    private static final int BATCH_SIZE = 500;
    private static final boolean fullMatch = true;

    private Throwable throwable;
    private final ConnectorNode connectorNode;
    private final QueryByAdvanceFilterFunction queryByAdvanceFilterFunction;
    private final TapCodecsFilterManager codecsFilterManager;
    private final TapCodecsFilterManager defaultCodecsFilterManager;

    private final LinkedBlockingQueue<CompareRecord> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean hasNext = new AtomicBoolean(true);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final String tableName;
    private final TapTable tapTable;
    private DataMap offset;
    private Projection projection;
    private final List<SortOn> sortOnList = new ArrayList<>();
    private final Supplier<Boolean> isRunning;

    public PdkQueryCursor(ConnectorNode connectorNode, String tableName, Supplier<Boolean> isRunning) {
        this.isRunning = () -> !closed.get() && isRunning.get();
        this.tableName = tableName;
        this.connectorNode = connectorNode;
        this.queryByAdvanceFilterFunction = connectorNode.getConnectorFunctions().getQueryByAdvanceFilterFunction();
        this.codecsFilterManager = connectorNode.getCodecsFilterManager();
        this.defaultCodecsFilterManager = TapCodecsFilterManager.create(TapCodecsRegistry.create());

        this.tapTable = connectorNode.getConnectorContext().getTableMap().get(tableName);
        for (String k : tapTable.primaryKeys()) {
            sortOnList.add(new SortOn(k, SortOn.ASCENDING));
        }

        if (!fullMatch) {
            projection = new Projection();
            for (SortOn sortOn : sortOnList) {
                projection.include(sortOn.getKey());
            }
        }
    }

    @Override
    public CompareRecord next() throws Exception {
        if (hasNext.get() && queue.size() == 0) {
            queryNextBatch();
        }
        while (isRunning.get()) {
            try {
                CompareRecord record = queue.poll(100L, TimeUnit.MILLISECONDS);
                if (null != record) {
                    codecsFilterManager.transformToTapValueMap(record.getData(), tapTable.getNameFieldMap());
                    defaultCodecsFilterManager.transformFromTapValueMap(record.getData());
                    return record;
                }

                if (!hasNext.get()) break;
            } catch (InterruptedException e) {
                break;
            }
        }
        return null;
    }

    @Override
    public void close() throws Exception {
        logger.info("close {} query cursor", tableName);
        closed.set(true);
        queue.clear();
    }

    private void queryNextBatch() throws Exception {
        TapAdvanceFilter tapAdvanceFilter = TapAdvanceFilter.create();
        if (null == offset) {
            offset = new DataMap();
        } else {
            List<QueryOperator> operators = new LinkedList<>();
            offset.forEach((k, v) -> operators.add(QueryOperator.gt(k, v)));
            tapAdvanceFilter.setOperators(operators);
        }
        tapAdvanceFilter.setLimit(BATCH_SIZE);
        tapAdvanceFilter.setSortOnList(sortOnList);
        tapAdvanceFilter.setProjection(projection);

        PDKInvocationMonitor.invoke(connectorNode, PDKMethod.SOURCE_QUERY_BY_ADVANCE_FILTER
                , () -> queryByAdvanceFilterFunction.query(connectorNode.getConnectorContext(), tapAdvanceFilter, tapTable, filterResults -> {
                    Throwable error = filterResults.getError();
                    if (null != error) {
                        throwable = error;
                        hasNext.set(false);
                        return;
                    }

                    hasNext.set(Optional.ofNullable(filterResults.getResults()).map(results -> {
                        if (results.isEmpty()) return false;

                        for (Map<String, Object> result : results) {
                            CompareRecord record = new CompareRecord();
                            record.getData().putAll(result);
                            for (SortOn s : sortOnList) {
                                record.getKeyNames().add(s.getKey());
                                record.getOriginalKey().put(s.getKey(), result.get(s.getKey()));
                            }

                            while (true) {
                                if (!isRunning.get()) return false;
                                try {
                                    if (queue.offer(record, 100L, TimeUnit.MILLISECONDS)) {
                                        sortOnList.forEach(s -> offset.put(s.getKey(), result.get(s.getKey())));
                                        break;
                                    }
                                } catch (InterruptedException e) {
                                    return false;
                                }
                            }
                        }
                        return true;
                    }).orElse(false));
                })
                , TAG
        );

        if (throwable instanceof Exception) {
            throw (Exception) throwable;
        } else if (null != throwable) {
            throw new Exception(throwable);
        }

        if (queue.size() == 0) {
            hasNext.set(false);
        }
    }
}
