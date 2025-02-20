package com.tapdata.tm.monitor.controller;

import com.tapdata.manager.common.utils.JsonUtil;
import com.tapdata.tm.base.controller.BaseController;
import com.tapdata.tm.base.dto.Page;
import com.tapdata.tm.base.dto.ResponseMessage;
import com.tapdata.tm.monitor.dto.BatchRequestDto;
import com.tapdata.tm.monitor.dto.StatisticVo;
import com.tapdata.tm.monitor.dto.TableSyncStaticDto;
import com.tapdata.tm.monitor.dto.TransmitTotalVo;
import com.tapdata.tm.monitor.param.AggregateMeasurementParam;
import com.tapdata.tm.monitor.param.MeasurementQueryParam;
import com.tapdata.tm.monitor.service.BatchService;
import com.tapdata.tm.monitor.service.MeasurementService;
import com.tapdata.tm.monitor.service.MeasurementServiceV2;
import com.tapdata.tm.monitor.vo.BatchResponeVo;
import com.tapdata.tm.monitor.vo.QueryMeasurementVo;
import com.tapdata.tm.monitor.vo.TableSyncStaticVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.tapdata.common.sample.request.BulkRequest;
import io.tapdata.common.sample.request.QueryMeasurementParam;
import io.tapdata.common.sample.request.QuerySampleParam;
import io.tapdata.common.sample.request.QueryStisticsParam;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(value = "/api/measurement")
@Slf4j
@Tag(name = "可观测性")
@Setter(onMethod_ = {@Autowired})
public class MeasureController extends BaseController {
    private MeasurementService measurementService;
    private MeasurementServiceV2 measurementServiceV2;
    private BatchService batchService;

    @PostMapping("points")
    public ResponseMessage points(@RequestBody BulkRequest bulkRequest) {
        log.info("MeasureController-- {}", JsonUtil.toJson(bulkRequest));
        try {
            List samples = bulkRequest.getSamples();
            List statistics = bulkRequest.getStatistics();
            if (CollectionUtils.isNotEmpty(samples)) {
                measurementService.addAgentMeasurement(samples);
            }
            if (CollectionUtils.isNotEmpty(statistics)) {
                measurementService.addAgentStatistics(statistics);
            }
        } catch (Exception e) {
            log.error("添加秒点异常", e);
            return failed("添加秒点异常");
        }
        return success();
    }

    @PostMapping("points/v2")
    public ResponseMessage pointsV2(@RequestBody BulkRequest bulkRequest) {
        log.info("MeasureController-- {}", JsonUtil.toJson(bulkRequest));
        try {
            List samples = bulkRequest.getSamples();
            List statistics = bulkRequest.getStatistics();
            if (CollectionUtils.isNotEmpty(samples)) {
                measurementServiceV2.addAgentMeasurement(samples);
            }
        } catch (Exception e) {
            log.error("添加秒点异常", e);
            return failed("添加秒点异常");
        }
        return success();
    }


    @Deprecated
    @PostMapping("query")
    public ResponseMessage query(@RequestBody QueryMeasurementParam queryMeasurementParam) {
        QueryMeasurementVo queryMeasurementVo = new QueryMeasurementVo();
        List<StatisticVo> statisticVoList = new ArrayList();
        List sampleVoList = new ArrayList();
        try {

            List<QuerySampleParam> querySampleParamList = queryMeasurementParam.getSamples();
            if (CollectionUtils.isNotEmpty(querySampleParamList)) {
                for (QuerySampleParam querySampleParam : querySampleParamList) {
                    String type = querySampleParam.getType();
                    if ("headAndTail".equals(type)) {
                        sampleVoList.add(measurementService.queryHeadAndTail(querySampleParam).get(0));
                    } else {
                        sampleVoList.add(measurementService.querySample(querySampleParam).get(0));
                    }
                }
            }

            List<QueryStisticsParam> queryStaisticsParamList = queryMeasurementParam.getStatistics();
            if (CollectionUtils.isNotEmpty(queryStaisticsParamList)) {
                statisticVoList = measurementService.queryStatistics(queryMeasurementParam.getStatistics());
            }

            queryMeasurementVo.setSamples(sampleVoList);
            queryMeasurementVo.setStatistics(statisticVoList);
        } catch (Exception e) {
             log.error("查询秒点异常", e);
        }
        return success(queryMeasurementVo);
    }

    /**
     * 查询首页传输总览
     *
     * @param queryMeasurementParam
     * @return
     */
    @Deprecated
    @GetMapping("queryTransmitTotal")
    public ResponseMessage queryTransmitTotal(@RequestBody QueryMeasurementParam queryMeasurementParam) {
        TransmitTotalVo transmitTotalVo = new TransmitTotalVo();
        try {
//            List sampleVoList = measurementService.querySample(queryMeasurementParam.getSamples());
            transmitTotalVo = measurementService.queryTransmitTotal(getLoginUser());
        } catch (Exception e) {
            log.error("查询秒点异常", e);
        }
        return success(transmitTotalVo);
    }

    @PostMapping("points/aggregate")
    public ResponseMessage pointsAggregate(@RequestBody AggregateMeasurementParam aggregateMeasurementParam) {
        try {
            measurementServiceV2.aggregateMeasurement(aggregateMeasurementParam);
            return success();
        } catch (Exception e) {
            e.printStackTrace();
            return failed(e);
        }
    }

    @PostMapping("query/v2")
    public ResponseMessage queryV2(@RequestBody MeasurementQueryParam measurementQueryParam) {
        try {
            Object data = measurementServiceV2.getSamples(measurementQueryParam);
            return success(data);
        } catch (Exception e) {
            e.printStackTrace();
            return failed(e);
        }
    }

    @Operation(summary = "可观测性并行请求接口", description = "一个接口返回任务事件统计、任务日志和校验数据")
    @PostMapping("/batch")
    public ResponseMessage<BatchResponeVo> batch(@Parameter(description = "多个请求的参数集合", required = true,
                                                         content = @Content(schema = @Schema(implementation = BatchRequestDto.class)))
                                                 @RequestBody BatchRequestDto batchRequestDto) throws ExecutionException, InterruptedException {
        return success(batchService.batch(batchRequestDto));
    }

    @Operation(summary = "全量信息接口")
    @GetMapping("/full_statistics")
    public ResponseMessage<Page<TableSyncStaticVo>> querySyncStatic (@RequestParam String taskRecordId,
                                                                     @RequestParam(defaultValue = "1") Integer page,
                                                                     @RequestParam(defaultValue = "20") Integer size) {
        return success(measurementServiceV2.querySyncStatic(new TableSyncStaticDto(taskRecordId, page, size), getLoginUser()));
    }
}
