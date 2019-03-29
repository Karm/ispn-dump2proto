package biz.karms.protostream.threat.task;

import biz.karms.sinkit.resolver.ResolverConfiguration;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Resolver exporting task
 */
public interface ResolverCacheExportTask<T> {

    /**
     * Export the given data
     *
     * @param resolverConfiguration resolver represented by its configuration to be exported
     * @param data                  the data to be exported
     */
    void export(ResolverConfiguration resolverConfiguration, final T data, ThreadPoolExecutor notificationExecutor);

}
