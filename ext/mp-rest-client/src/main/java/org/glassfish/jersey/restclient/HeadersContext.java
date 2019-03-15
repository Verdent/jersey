package org.glassfish.jersey.restclient;

import java.util.Optional;
import java.util.function.Supplier;

import javax.ws.rs.core.MultivaluedMap;

/**
 * Created by David Kral.
 */
public final class HeadersContext {
    /**
     * Tracing context thread local, used by internal implementations of tracing filters.
     */
    private static final ThreadLocal<HeadersContext> HEADERS_CONTEXT = new ThreadLocal<>();

    private final MultivaluedMap<String, String> inboundHeaders;

    /**
     * The instance associated with the current thread.
     * @return context for current thread or {@code empty} if none associated
     */
    public static Optional<HeadersContext> get() {
        return Optional.ofNullable(HEADERS_CONTEXT.get());
    }

    /**
     * Computes the instance and associates it with current thread if none
     * associated, or returns the instance already associated.
     *
     * @param contextSupplier supplier for header context to be associated with the thread if none is
     * @return an instance associated with the current context, either from other provider, or from contextSupplier
     */
    public static HeadersContext compute(Supplier<HeadersContext> contextSupplier) {
        HeadersContext headersContext = HEADERS_CONTEXT.get();
        if (null == headersContext) {
            set(contextSupplier.get());
        }

        return get().orElseThrow(() -> new IllegalStateException("Computed result was null"));
    }

    /**
     * Set the header context to be associated with current thread.
     *
     * @param context context to associate
     */
    public static void set(HeadersContext context) {
        HEADERS_CONTEXT.set(context);
    }

    /**
     * Remove the header context associated with current thread.
     */
    public static void remove() {
        HEADERS_CONTEXT.remove();
    }

    /**
     * Create a new header context with client tracing enabled.
     *
     * @param inboundHeaders inbound header to be used for context propagation
     * @return a new header context (not associated with current thread)
     * @see #set(HeadersContext)
     */
    public static HeadersContext create(MultivaluedMap<String, String> inboundHeaders) {
        return new HeadersContext(inboundHeaders);
    }

    public HeadersContext(MultivaluedMap<String, String> inboundHeaders) {
        this.inboundHeaders = inboundHeaders;
    }

    /**
     * Map of headers that were received by server for an inbound call,
     * may be used to propagate additional headers fro outbound request.
     *
     * @return map of inbound headers
     */
    public MultivaluedMap<String, String> inboundHeaders() {
        return inboundHeaders;
    }


}
