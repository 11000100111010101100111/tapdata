package io.tapdata.pdk.run.base;

import java.util.Objects;
import java.util.StringJoiner;

public enum RunClassMap {
    BATCH_COUNT_RUN("io.tapdata.pdk.run.support.BatchCountRun","BatchCountFunction",new String[]{"batch_count"},"BatchCountRun"),
    BATCH_READ_RUN("io.tapdata.pdk.run.support.BatchReadRun","BatchReadFunction",new String[]{"batch_read"},"BatchReadRun"),
    COMMAND_RUN("io.tapdata.pdk.run.support.CommandRun","",new String[]{"command_callback"},"CommandRun"),
    CONNECTION_TEST_RUN("io.tapdata.pdk.run.support.ConnectionTestRun","",new String[]{"connection_test"},"ConnectionTestRun"),
    DISCOVER_SCHEMA_RUN("io.tapdata.pdk.run.support.DiscoverSchemaRun","",new String[]{"discover_schema"},"DiscoverSchemaRun"),
    STREAM_READ_RUN("io.tapdata.pdk.run.support.StreamReadRun","",new String[]{"stream_read"},"StreamReadRun"),
    TABLE_COUNT_RUN("io.tapdata.pdk.run.support.TableCountRun","",new String[]{"table_count"},"TableCountRun"),
    TIMESTAMP_TO_STREAM_OFFSET_RUN("io.tapdata.pdk.run.support.TimestampToStreamOffsetRun","",new String[]{"timestamp_to_stream_offset"},"TimestampToStreamOffsetRun"),
    WEB_HOOK_EVENT_RUN("io.tapdata.pdk.run.support.WebHookEventRun","",new String[]{"web_hook_event"},"WebHookEventRun"),
    WRITE_RECORD_RUN("io.tapdata.pdk.run.support.WriteRecordRun","",new String[]{"write_record","insert_record","update_record","delete_record"},"WriteRecordRun"),
    ;
    String classPath;
    String functionJavaName;
    String[] functionJsName;
    String runCaseName;
    RunClassMap(String classPath,String functionJavaName,String[] functionJsName,String runCaseName){
        this.classPath = classPath;
        this.functionJavaName = functionJavaName;
        this.runCaseName = runCaseName;
        this.functionJsName = functionJsName;
    }
    public static String whichCase(String runCaseName){
        if (Objects.nonNull(runCaseName) && !"".equals(runCaseName.trim())){
            for (RunClassMap value : values()) {
                if (value.equalsName(runCaseName) ) return value.classPath;
            }
        }
        return null;
    }
    public boolean equalsName(String runCaseName){
        return runCaseName.equals(this.classPath)
                || runCaseName.equals(this.functionJavaName)
                || this.hasInJsName(runCaseName)
                || runCaseName.equals(this.runCaseName);
    }
    public boolean hasInJsName(String runCaseName){
        String[] jsName = this.functionJsName;
        for (String name : jsName) {
            if (name.equals(runCaseName)) return true;
        }
        return false;
    }

    public static String allCaseTable(boolean showLog){
        RunClassMap[] values = values();
        int classPathMaxLength = 0;
        int functionJavaNameMaxLength = 0;
        int functionJsNameMaxLength = 0;
        int runCaseNameMaxLength = 0;
        for (RunClassMap value : values) {
            String classPath = value.classPath;
            String functionJavaName = value.functionJavaName;
            String[] functionJsName = value.functionJsName;
            String runCaseName = value.runCaseName;
            int functionJsNameLength = 0;
            for (String name : functionJsName) {
                functionJsNameLength += name.length() + 3;
            }
            classPathMaxLength = Math.max(classPathMaxLength, classPath.length());
            functionJavaNameMaxLength = Math.max(functionJavaNameMaxLength, functionJavaName.length());
            functionJsNameMaxLength = Math.max(functionJsNameMaxLength, functionJsNameLength);
            runCaseNameMaxLength = Math.max(runCaseNameMaxLength, runCaseName.length());
        }
        classPathMaxLength += 5;
        functionJavaNameMaxLength += 5;
        functionJsNameMaxLength += 5;
        runCaseNameMaxLength += 5;
        StringJoiner joiner = new StringJoiner("\n");
        int maxLineSize = classPathMaxLength
                //+ functionJavaNameMaxLength
                + functionJsNameMaxLength
                + runCaseNameMaxLength;
        if (showLog){
            joiner.add(center("------------------------------------------------------------------------------------",maxLineSize));
            joiner.add(center("[.___________.    ___      .______    _______       ___   .___________.    ___     ]",maxLineSize));
            joiner.add(center("[|           |   /   \\     |   _  \\  |       \\     /   \\  |           |   /   \\    ]",maxLineSize));
            joiner.add(center("[`---|  |----`  /  ^  \\    |  |_)  | |  .--.  |   /  ^  \\ `---|  |----`  /  ^  \\   ]",maxLineSize));
            joiner.add(center("[    |  |      /  /_\\  \\   |   ___/  |  |  |  |  /  /_\\  \\    |  |      /  /_\\  \\  ]",maxLineSize));
            joiner.add(center("[    |  |     /  _____  \\  |  |      |  '--'  | /  _____  \\   |  |     /  _____  \\ ]",maxLineSize));
            joiner.add(center("[    |__|    /__/     \\__\\ | _|      |_______/ /__/     \\__\\  |__|    /__/     \\__\\]",maxLineSize));
            joiner.add(center("------------------------------------------------------------------------------------",maxLineSize));
        }
        joiner.add(spilt(maxLineSize));
        joiner.add(spilt(classPathMaxLength,"Class Path") +
                //spilt(functionJavaNameMaxLength,"Java Function Name") +
                spilt(functionJsNameMaxLength,"JavaScript Name") +
                spilt(runCaseNameMaxLength,"Run/Debug Name"));
        joiner.add(spilt(maxLineSize));
        for (RunClassMap value : values) {
            joiner.add(spilt(classPathMaxLength,value.classPath) +
                    //spilt(functionJavaNameMaxLength,value.functionJavaName) +
                    spilt(functionJsNameMaxLength, value.jsNameInLine()) +
                    spilt(runCaseNameMaxLength,value.runCaseName));
        }
        return joiner.toString() + "\n\nTip: If you want to execute js function which name is write_record, maybe the command you can enter is:" +
                "\n\t io.tapdata.pdk.run.support.WriteRecordRun or write_record or insert_record or update_record or delete_record or WriteRecordRun.";
    }
    public static String center(String str, int lineSize){
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < (lineSize/2 - str.length()/2); i++) {
            builder.append(" ");
        }
        builder.append(str);
        return builder.toString();
    }

    public static String allCaseTable(){
       return allCaseTable(false);
    }
    public static String spilt(int maxLength, String str){
        StringBuilder builder = new StringBuilder(str);
        for (int i = 0; i < maxLength - str.length(); i++) {
            builder.append(" ");
        }
        return builder.toString();
    }
    public String jsNameInLine(){
        StringJoiner builder = new StringJoiner(" | ");
        for (String name : this.functionJsName) {
            builder.add(name);
        }
        return builder.toString();
    }

    public static void main(String[] args) {
        System.out.println(allCaseTable());
    }

    public static String spilt(int maxLength){
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < maxLength; i++) {
            builder.append("-");
        }
        return builder.toString();
    }
}
