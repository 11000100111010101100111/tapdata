package io.tapdata.quickapi.support.postman.util;

import io.tapdata.quickapi.core.emun.TapApiTag;
import io.tapdata.quickapi.support.postman.entity.ApiMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ApiMapUtil {

    public static List<ApiMap.ApiEntity> tableApis(ApiMap apiMap){
        if (Objects.isNull(apiMap) || apiMap.isEmpty()) return Collections.emptyList();
        List<ApiMap.ApiEntity> list = new ArrayList<>();
        apiMap.forEach(entity->{
            if(TapApiTag.isTableName(entity.name())){
                list.add(entity);
            }
        });
        return list;
    }

    public static List<ApiMap.ApiEntity> tokenApis(ApiMap apiMap){
        if (Objects.isNull(apiMap) || apiMap.isEmpty()) return Collections.emptyList();
        List<ApiMap.ApiEntity> list = new ArrayList<>();
        apiMap.forEach(entity->{
            if(TapApiTag.isTokenApi(entity.name())){
                list.add(entity);
            }
        });
        return list;
    }

//    public static boolean isTapTableApi(String apiName) {
//        //String regx = ".*(TAP_TABLE\\[[^\\]]+).*";
////        System.out.println("START[123]JJK[526]".matches(regx));
////        System.out.println(Pattern.compile(regx).matcher("START[123]JJK[526]").group());
//        return Objects.nonNull(apiName) && apiName.matches(TapApiTag.TAP_TABLE.tagRegex());
//    }

//    public static boolean hasPageTag(ApiMap.ApiEntity apiEntity){
//        if (Objects.isNull(apiEntity)) return false;
//        String apiName = apiEntity.name();
//        if (Objects.isNull(apiName)) return false;
//        return apiName.matches(TapApiTag.PAGE_SIZE_PAGE_INDEX.tagRegex())
//                || apiName.matches(TapApiTag.FROM_TO.tagRegex())
//                || apiName.matches(TapApiTag.PAGE_LIMIT.tagRegex())
//                || apiName.matches(TapApiTag.PG_NONE.tagRegex())
//                || apiName.matches(TapApiTag.PAGE_TOKEN.tagRegex());
//    }

    public static void main(String[] args) {
        String table1 = "TAP_TABLE[558";
        String table2 = "TAP_TABLE[10xx]25ads";
        String table3 = "sfrTAP_TABLE[]";
        String table4 = "sderTAP_TABLE[10xx]";
        String table5 = "ADfr";
        System.out.println(table1 + " <-> " + (table1.matches(".*(TAP_TABLE\\[[^\\]]+).*")));
        System.out.println(table2 + " <-> " + (table2.matches(TapApiTag.TAP_TABLE.tagRegex())));
        System.out.println(table3 + " <-> " + (table3.matches(TapApiTag.TAP_TABLE.tagRegex())));
        System.out.println(table4 + " <-> " + (table4.matches(TapApiTag.TAP_TABLE.tagRegex())));
        System.out.println(table5 + " <-> " + (table5.matches(TapApiTag.TAP_TABLE.tagRegex())));
    }
}
