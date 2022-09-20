package com.tapdata.tm.discovery.bean;

import com.tapdata.tm.commons.dag.DAG;
import lombok.Data;

import java.util.Date;


/**
 * 存储对象概述
 */
@Data
public class DiscoveryTaskDto extends DataDiscoveryDto {
    /** 创建时间 */
    private Date createAt;
    /** 更新时间 */
    private Date lastUpdAt;
    /** 数据项 */
    private Integer nodeNum;
    /** 任务描述 */
    private String taskDesc;
    private String version;
    private DAG dag;
}
