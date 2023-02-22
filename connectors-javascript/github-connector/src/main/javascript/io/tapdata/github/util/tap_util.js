/**
 *  This is the toolkit encapsulated by Tap Data.
 * */
var invoker = loadAPI();
var OptionalUtil = {
    isEmpty: function (obj) {
        return typeof (obj) == 'undefined' || null == obj;
    },
    notEmpty: function (obj) {
        return !this.isEmpty(obj);
    }
}

function iterateAllData(apiName, offset, call) {
    if (OptionalUtil.isEmpty(apiName)) {
        log.error("Please specify the corresponding paging API name or URL .");
    }
    let res;
    let error;
    do {
        try{
            let response = invoker.invoke(apiName, offset);
            res = response.result;
            error = response.error;
        }catch (e){
            log.error('(26)---- error:',e);
            throw(e)
            break;
        }
    } while (call(res, offset, error));
}

function commandAndConvertData(apiName, params, call){
    if (OptionalUtil.isEmpty(apiName)) {
        log.error("Please specify the corresponding paging API name or URL .");
    }
    let invokerData = invoker.invoke(apiName, params);
    return call(invokerData.result);
}
