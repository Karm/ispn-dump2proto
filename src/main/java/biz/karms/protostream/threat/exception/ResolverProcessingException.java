package biz.karms.protostream.threat.exception;

import biz.karms.protostream.threat.task.ResolverProcessingTask;
import biz.karms.sinkit.resolver.ResolverConfiguration;
import lombok.Getter;

public class ResolverProcessingException extends RuntimeException{

    @Getter
    private ResolverConfiguration resolverConfiguration;

    @Getter
    private ResolverProcessingTask task;

    public ResolverProcessingException(String message, ResolverConfiguration resolverConfiguration, ResolverProcessingTask task) {
        super(message);
        this.resolverConfiguration = resolverConfiguration;
        this.task = task;
    }

    public ResolverProcessingException(String message, Throwable cause, ResolverConfiguration resolverConfiguration, ResolverProcessingTask task) {
        super(message, cause);
        this.resolverConfiguration = resolverConfiguration;
        this.task = task;
    }

    public ResolverProcessingException(Throwable cause, ResolverConfiguration resolverConfiguration, ResolverProcessingTask task) {
        super(cause);
        this.resolverConfiguration = resolverConfiguration;
        this.task = task;
    }
}
