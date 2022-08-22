package com.tapdata.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import com.tapdata.cache.ICacheGetter;
import com.tapdata.constant.MapUtil;
import com.tapdata.constant.PkgAnnoUtil;
import com.tapdata.entity.*;
import com.tapdata.mongo.ClientMongoOperator;
import com.tapdata.mongo.HttpClientMongoOperator;
import com.tapdata.processor.constant.JSEngineEnum;
import com.tapdata.processor.context.ProcessContext;
import com.tapdata.processor.context.ProcessContextEvent;
import io.tapdata.annotation.DatabaseTypeAnnotation;
import io.tapdata.annotation.DatabaseTypeAnnotations;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.BsonUndefined;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.springframework.beans.factory.config.BeanDefinition;

import javax.script.*;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;

/**
 * @author jackin
 */
public class ScriptUtil {

	private static Logger logger = LogManager.getLogger(ScriptUtil.class);

	public static final String FUNCTION_NAME = "process";

	public static final String SCRIPT_FUNCTION_NAME = "validate";

	/**
	 * 获取js引擎
	 *
	 * @param jsEngineName
	 * @return
	 */
	public static ScriptEngine getScriptEngine(String jsEngineName) {
		JSEngineEnum jsEngineEnum = JSEngineEnum.getByEngineName(jsEngineName);
		ScriptEngine scriptEngine;
		if (jsEngineEnum == JSEngineEnum.GRAALVM_JS) {
			scriptEngine = GraalJSScriptEngine
					.create(null,
							Context.newBuilder("js")
									.allowAllAccess(true)
									.allowHostAccess(HostAccess.newBuilder(HostAccess.ALL)
											.targetTypeMapping(Value.class, Object.class
													, v -> v.hasArrayElements() && v.hasMembers()
													, v -> v.as(List.class)
											).build()
									)
					);
		} else {
			scriptEngine = new ScriptEngineManager().getEngineByName(jsEngineEnum.getEngineName());
		}
		return scriptEngine;
	}

	public static Invocable getScriptEngine(String jsEngineName, String script) throws ScriptException {
		return getScriptEngine(jsEngineName, script, null, null);
	}

	public static Invocable getScriptEngine(String jsEngineName, String script, List<JavaScriptFunctions> javaScriptFunctions,
											ClientMongoOperator clientMongoOperator) throws ScriptException {
		return getScriptEngine(jsEngineName, script, javaScriptFunctions, clientMongoOperator, null, null, null);
	}
	public static Invocable getScriptEngine(String jsEngineName, String script, List<JavaScriptFunctions> javaScriptFunctions,
											ClientMongoOperator clientMongoOperator, ScriptConnection source, ScriptConnection target, ICacheGetter memoryCacheGetter) throws ScriptException {

		if (StringUtils.isBlank(script)) {
			return null;
		}

		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			if (contextClassLoader == null) {
				Thread.currentThread().setContextClassLoader(ScriptUtil.class.getClassLoader());
			}
			ScriptEngine e = getScriptEngine(jsEngineName);
			String buildInMethod = initBuildInMethod(javaScriptFunctions, clientMongoOperator);
			String scripts = script + System.lineSeparator() + buildInMethod;

			try {
				e.eval(scripts);
			} catch (Throwable ex) {
				throw new RuntimeException(String.format("script eval error: %s, %s, %s, %s", jsEngineName, e, scripts, contextClassLoader), ex);
			}
			if (source != null) {
				e.put("source", source);
			}
			if (target != null) {
				e.put("target", target);
			}
			if (memoryCacheGetter != null) {
				e.put("memoryCacheGetter", memoryCacheGetter);
			}

			return (Invocable) e;
		} finally {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		}
	}

	private static void loggerLoader(ServiceLoader<ScriptEngineFactory> loader1, String tag) {
		for (ScriptEngineFactory factory : loader1) {
			logger.info("{} factory: {} {}", tag,  factory, factory.getScriptEngine().getClass());
		}
	}

	public static Object invokeScript(
			Invocable engine,
			String functionName,
			MessageEntity message,
			Connections sourceConn,
			Connections targetConn,
			Job job,
			Map<String, Object> context,
			Logger logger
	) throws Exception {
		ProcessContext processContext = message.getProcessContext();
		if (message.getProcessContext() == null) {
			processContext = new ProcessContext(message.getOp(), message.getTableName(), sourceConn, targetConn, job, message.getOffset());
		} else {
			processContext.setOp(message.getOp());
			processContext.setTableName(message.getTableName());
			processContext.setSource(sourceConn);
			processContext.setTarget(targetConn);
			processContext.setJob(job);
			processContext.convertOffsetToSyncType(message.getOffset());
		}

		processContext.setEventTime(message.getTimestamp() != null ? message.getTimestamp() : 0);
		processContext.setTs(message.getTimestamp() != null ? message.getTimestamp() : 0);

		Map<String, Object> contextMap = MapUtil.obj2Map(processContext);
		context.putAll(contextMap);

		if (processContext.getEvent() == null) {
			ProcessContextEvent event = new ProcessContextEvent(
					message.getOp(),
					message.getTableName(),
					processContext.getSyncType(),
					message.getTimestamp() == null ? 0L : message.getTimestamp()
			);
			processContext.setEvent(event);
		}

		processContext.getEvent().setBefore(message.getBefore());
		Map<String, Object> eventMap = MapUtil.obj2Map(processContext.getEvent());
		context.put("event", eventMap);
		if (engine == null) {
			logger.error("script engine is null, {}", Arrays.asList(Thread.currentThread().getStackTrace()));
		}

		((ScriptEngine) engine).put("context", context);
		((ScriptEngine) engine).put("log", logger);

		Object o;
		Map<String, Object> record = MapUtils.isNotEmpty(message.getAfter()) ? message.getAfter() : message.getBefore();
		try {
			if (engine instanceof GraalJSScriptEngine) {
				o = engine.invokeFunction(functionName, ProxyObject.fromMap(record));
			} else {
				o = engine.invokeFunction(functionName, record);
			}
		} catch (Throwable e) {
			throw new RuntimeException(String.format("Invoke function %s error: %s", functionName, e.getMessage(), e), e);
		}

		return o;
	}

	public static List<Map<String, Object>> executeMongoQuery(ScriptConnection connection, String database, String table, String fieldsStr, Object... values) {

		List<Map<String, Object>> results = new ArrayList<>();
		Document filter = new Document();
		String[] fields = fieldsStr.split(",");
		if (values == null) {
			values = new Object[0];
		}
		for (int i = 0; i < fields.length; i++) {
			if (i < values.length) {
				filter.append(fields[i], values[i]);
			} else {
				filter.append(fields[i], null);
			}
		}
		Document executeMap = new Document();
		executeMap.append("database", database);
		executeMap.append("collection", table);
		executeMap.append("filter", filter);
		results = connection.executeQuery(executeMap);

		return results;
	}

	public static void scriptSort(List<Map> list, String sortKey, int sort) {

		if (CollectionUtils.isEmpty(list)) {
			return;
		}

		Collections.sort(list, (item1, item2) -> {
			Object o1 = item1.get(sortKey);
			Object o2 = item2.get(sortKey);
			if (o1 instanceof Comparable && o2 instanceof Comparable) {
				Comparable comparable1 = (Comparable) o1;
				Comparable comparable2 = (Comparable) o2;

				if (sort > 0) {
					return comparable1.compareTo(comparable2);
				} else {
					return comparable2.compareTo(comparable1);
				}
			}

			return 0;
		});
	}

	public static String initBuildInMethod(List<JavaScriptFunctions> javaScriptFunctions, ClientMongoOperator clientMongoOperator) {
		StringBuilder buildInMethod = new StringBuilder();
		buildInMethod.append("var DateUtil = Java.type(\"com.tapdata.constant.DateUtil\");\n");
		buildInMethod.append("var UUIDGenerator = Java.type(\"com.tapdata.constant.UUIDGenerator\");\n");
		buildInMethod.append("var idGen = Java.type(\"com.tapdata.constant.UUIDGenerator\");\n");
		buildInMethod.append("var HashMap = Java.type(\"java.util.HashMap\");\n");
		buildInMethod.append("var ArrayList = Java.type(\"java.util.ArrayList\");\n");
		buildInMethod.append("var Date = Java.type(\"java.util.Date\");\n");
		buildInMethod.append("var uuid = UUIDGenerator.uuid;\n");
		buildInMethod.append("var JSONUtil = Java.type('com.tapdata.constant.JSONUtil');\n");
		buildInMethod.append("var HanLPUtil = Java.type(\"com.tapdata.constant.HanLPUtil\");\n");
		buildInMethod.append("var split_chinese = HanLPUtil.hanLPParticiple;\n");
		buildInMethod.append("var rest = Java.type(\"com.tapdata.processor.util.CustomRest\");\n");
		buildInMethod.append("var tcp = Java.type(\"com.tapdata.processor.util.CustomTcp\");\n");
		buildInMethod.append("var util = Java.type(\"com.tapdata.processor.util.Util\");\n");
		buildInMethod.append("var mongo = Java.type(\"com.tapdata.processor.util.CustomMongodb\");\n");
		buildInMethod.append("var MD5Util = Java.type(\"com.tapdata.constant.MD5Util\");\n");
		buildInMethod.append("var MD5 = function(str){return MD5Util.crypt(str, true);};\n");
		buildInMethod.append("var Collections = Java.type(\"java.util.Collections\");\n");
		buildInMethod.append("var networkUtil = Java.type(\"com.tapdata.constant.NetworkUtil\");\n");
		buildInMethod.append("var MapUtils = Java.type(\"com.tapdata.constant.MapUtil\");\n");
		buildInMethod.append("var TapModelDeclare = Java.type(\"com.tapdata.processor.util.TapModelDeclare\");\n");
		buildInMethod.append("var sleep = function(ms){\n" +
				"var Thread = Java.type(\"java.lang.Thread\");\n" +
				"Thread.sleep(ms);\n" +
				"}\n");

		if (CollectionUtils.isNotEmpty(javaScriptFunctions)) {
			List<URL> urlList = new ArrayList<>();
			for (JavaScriptFunctions javaScriptFunction : javaScriptFunctions) {
				if (javaScriptFunction.isSystem()) {
					continue;
				}
				String jsFunction = javaScriptFunction.getJSFunction();
				if (StringUtils.isNotBlank(jsFunction)) {
					buildInMethod.append(jsFunction).append("\n");
					if (javaScriptFunction.isJar() && AppType.init().isDaas()) {
						//定义类加载器
						String fileId = javaScriptFunction.getFileId();
						final Path filePath = Paths.get(System.getenv("TAPDATA_WORK_DIR"), "lib", fileId);
						if (Files.notExists(filePath)) {
							if (clientMongoOperator instanceof HttpClientMongoOperator) {
								File file = ((HttpClientMongoOperator) clientMongoOperator).downloadFile(null, "file/" + fileId, filePath.toString(), true);
								if (null == file) {
									throw new RuntimeException("not found");
								}
							} else {
								GridFSBucket gridFSBucket = clientMongoOperator.getGridFSBucket();
								try (GridFSDownloadStream gridFSDownloadStream = gridFSBucket.openDownloadStream(new ObjectId(javaScriptFunction.getFileId()))) {
									if (Files.notExists(filePath.getParent())) {
										Files.createDirectories(filePath.getParent());
									}
									Files.createFile(filePath);
									Files.copy(gridFSDownloadStream, filePath, StandardCopyOption.REPLACE_EXISTING);
								} catch (Exception e) {
									throw new RuntimeException(String.format("create function jar file '%s' error: %s", filePath, e.getMessage()), e);
								}
							}
						}
						try {
							URL url = filePath.toUri().toURL();
							urlList.add(url);
						} catch (Exception e) {
							throw new RuntimeException(String.format("create function jar file '%s' error: %s", filePath, e.getMessage()), e);
						}
					}
				}
			}
			if (CollectionUtils.isNotEmpty(urlList)) {
				logger.debug("urlClassLoader will load: {}", urlList);
				final URLClassLoader urlClassLoader = new CustomerClassLoader(urlList.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
				Thread.currentThread().setContextClassLoader(urlClassLoader);
			}
		}
//		if (Thread.currentThread().getContextClassLoader() == null) {
//			final URLClassLoader urlClassLoader = new CustomerClassLoader(new URL[0], ScriptUtil.class.getClassLoader());
//			Thread.currentThread().setContextClassLoader(urlClassLoader);
//		}

		return buildInMethod.toString();
	}

	public static ScriptConnection initScriptConnection(Connections connection) throws ClassNotFoundException, InstantiationException, IllegalAccessException {

		ScriptConnection scriptConnection = null;
		Set<BeanDefinition> fileDetectorDefinition = PkgAnnoUtil.getBeanSetWithAnno(Arrays.asList("com.tapdata.processor"),
				Arrays.asList(DatabaseTypeAnnotations.class, DatabaseTypeAnnotation.class));

		for (BeanDefinition beanDefinition : fileDetectorDefinition) {
			Class<ScriptConnection> aClass = (Class<ScriptConnection>) Class.forName(beanDefinition.getBeanClassName());
			DatabaseTypeAnnotation[] annotations = aClass.getAnnotationsByType(DatabaseTypeAnnotation.class);
			if (annotations != null && annotations.length > 0) {
				for (DatabaseTypeAnnotation annotation : annotations) {
					if (connection != null && annotation.type().getType().equals(connection.getDatabase_type())) {
						scriptConnection = aClass.newInstance();
						scriptConnection.initialize(connection);
						return scriptConnection;
					}
				}
			}
		}

		return scriptConnection;
	}

	public static class CustomerClassLoader extends URLClassLoader {

		public CustomerClassLoader(URL[] urls, ClassLoader parent) {
			super(urls, parent);
		}
	}

	public static void main(String[] args) throws ScriptException, NoSuchMethodException, JsonProcessingException {
		ScriptEngine scriptEngine1 = getScriptEngine(null);
		System.out.println(scriptEngine1);
		String script = initBuildInMethod(null, null);

//		script += "function process(record){\n" +
//			"var cDate = DateUtil.toCalendar(Date.from(record.instant));\n" +
//			"record.year = cDate.get(1)\n" +
//			"record.month = cDate.get(2) + 1;\n" +
//			"record.day = cDate.get(5);\n" +
//			"record.undefined = record.undefined.toString();" +
//			"record.keys = {};" +
//			"for(var key in record){record.keys[key] = key}\n" +
//			"return record;\n" +
//			"\n}";

		script += "function process(record){\n" +
				"    return record;\n" +
				"}";

//    String s = JSONUtil.obj2Json(new HashMap() {{
//      put("a", 1);
//      put("instant", Instant.now());
//      put("undefined", new BsonUndefined());
//    }});
//
//    System.out.println(s);

		Invocable scriptEngine = getScriptEngine(JSEngineEnum.NASHORN.getEngineName(), script, null, null, null, null, null);
		Object a = scriptEngine.invokeFunction(FUNCTION_NAME, new HashMap() {{
			put("a", 1);
			put("instant", Instant.now());
			put("undefined", new BsonUndefined());
		}});

		System.out.println(a);
	}
}
