package io.tapdata.pdk.apis.functions.connector.source;

import io.tapdata.entity.schema.TapTable;
import io.tapdata.pdk.apis.context.TapConnectorContext;
import io.tapdata.pdk.apis.entity.TapFilter;
import io.tapdata.pdk.apis.partition.FieldMinMaxValue;
import io.tapdata.pdk.apis.partition.TapPartitionFilter;

public interface QueryFieldMinMaxValueFunction {
    FieldMinMaxValue minMaxValue(TapConnectorContext connectorContext, TapTable table, TapPartitionFilter filter, String fieldName);
}