package io.tapdata.quickapi.server;

import cn.hutool.json.JSONUtil;
import io.tapdata.entity.logger.TapLogger;
import io.tapdata.entity.utils.DataMap;
import io.tapdata.pdk.apis.context.TapConnectionContext;
import io.tapdata.quickapi.common.QuickApiConfig;

import java.util.Objects;

public class QuickApiBase {
    private static final String TAG = QuickApiBase.class.getSimpleName();

    protected TapConnectionContext connectionContext;
    protected QuickApiConfig config;

    protected QuickApiBase(TapConnectionContext connectionContext){
        DataMap connectionConfig = connectionContext.getConnectionConfig();
        config = QuickApiConfig.create();
        this.connectionContext = connectionContext;
        if (Objects.nonNull(connectionConfig)) {
            String apiType = connectionConfig.getString("apiType");
            if (Objects.isNull(apiType)) apiType = "POST_MAN";
            String jsonTxt = connectionConfig.getString("jsonTxt");
            if (Objects.isNull(jsonTxt)){
                throw new RuntimeException("API JSON must be not null or not empty. ");
            }
            if (!JSONUtil.isJson(jsonTxt)){
                throw new RuntimeException("API JSON only JSON format. ");
            }
            config.apiConfig(apiType)
                    .jsonTxt(jsonTxt);
        }
    }

}
