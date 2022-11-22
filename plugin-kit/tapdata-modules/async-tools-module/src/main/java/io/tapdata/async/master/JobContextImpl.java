package io.tapdata.async.master;

import io.tapdata.entity.annotations.Implementation;
import io.tapdata.entity.error.CoreException;
import io.tapdata.modules.api.async.master.AsyncErrors;
import io.tapdata.modules.api.async.master.JobContext;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

/**
 * @author aplomb
 */
@Implementation(JobContext.class)
public class JobContextImpl extends JobContext {

	@Override
	public void foreach(int start, int maxCount, Function<Integer, Boolean> function) {
		if(function == null)
			return;
		if(start < 0 || start > maxCount)
			throw new CoreException(AsyncErrors.ILLEGAL_ARGUMENTS, "start {} or maxCount {} illegal", start, maxCount);

		for(int i = start; i < maxCount; i++) {
			checkJobStoppedOrNot();
			Boolean result = function.apply(i);
			checkJobStoppedOrNot();
			if(result != null && !result)
				break;
		}
	}

	@Override
	public void foreach(int maxCount, Function<Integer, Boolean> function) {
		foreach(0, maxCount, function);
	}

	@Override
	public <T> void foreach(Collection<T> collection, Function<T, Boolean> function) {
		if(collection == null || function == null)
			return;
		for(T t : collection) {
			checkJobStoppedOrNot();
			Boolean result = function.apply(t);
			checkJobStoppedOrNot();
			if(result != null && !result)
				break;
		}
	}

	private void checkJobStoppedOrNot() {
		if(stopped.get())
			throw new CoreException(AsyncErrors.ASYNC_JOB_STOPPED, "Async job {} stopped, reason {}", id, stopReason);
	}

	@Override
	public <K, V> void foreach(Map<K, V> map, Function<Map.Entry<K, V>, Boolean> entryFunction) {
		if(map == null || entryFunction == null)
			return;
		for(Map.Entry<K, V> entry : map.entrySet()) {
			checkJobStoppedOrNot();
			Boolean result = entryFunction.apply(entry);
			checkJobStoppedOrNot();
			if(result != null && !result)
				break;
		}
	}

	@Override
	public void runOnce(Runnable runnable) {
		if(runnable == null)
			return;
		checkJobStoppedOrNot();
		runnable.run();
		checkJobStoppedOrNot();
	}
}
