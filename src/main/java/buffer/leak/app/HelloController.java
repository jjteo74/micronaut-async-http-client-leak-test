package buffer.leak.app;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Context
@Controller("/hello")
public class HelloController {

    private static final Logger LOGGER = LoggerFactory.getLogger(HelloController.class);

    private final RxHttpClient client;
    private final ClientTestMethod testMethod;
    private final int count;
    private final Duration delay;
    private final CompositeDisposable disposable = new CompositeDisposable();

    enum ClientTestMethod {
        RETRIEVE {
            @Override
            Flowable<String> get(RxHttpClient client) {
                return client.retrieve("/hello/retrieve");
            }
        },
        EXCHANGE {
            @Override
            Flowable<String> get(RxHttpClient client) {
                return client
                        .exchange("/hello/exchange", String.class)
                        .map(r -> r.getBody().orElse(null));
            }
        },
        EXCHANGE_RELEASE {
            @Override
            Flowable<String> get(RxHttpClient client) {
                return client
                        .exchange("/hello/exchange_release", String.class)
                        .doOnNext(res -> res
                                .getBody(ByteBuf.class)
                                .ifPresent(bb -> {
                                    if (bb.refCnt() > 0) {
                                        LOGGER.debug("Releasing {}", bb);
                                        ReferenceCountUtil.release(bb);
                                    }
                                }))
                        .map(r -> r.getBody().orElse(null));
            }
        };

        abstract Flowable<String> get(RxHttpClient client);
    }

    @Inject
    public HelloController(
            @Client("myself") RxHttpClient client,
            @Property(name = "async-http-client.test-method") ClientTestMethod testMethod,
            @Value("${async-http-client.test-workers:1}") int count,
            @Value("${async-http-client.test-delay:50ms}") Duration delay
    ) {
        this.client = client;
        this.testMethod = testMethod;
        this.count = count;
        this.delay = delay;
    }

    @Get("{username}")
    public String greeting(String username) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("Hello ").append(username).append("; ");
        }
        return sb.toString();
    }

    @PostConstruct
    void startLoop() {
        LOGGER.info("Starting test of {} with {} worker(s) and delay {}", testMethod, count, delay);
        for (int i = 0; i < count; i++) {
            final Disposable d = testMethod
                    .get(client)
                    .repeatWhen(completed -> completed.delay(delay.toMillis(), TimeUnit.MILLISECONDS))
                    .forEach(reply -> LOGGER.debug("Received {}", reply));
            this.disposable.add(d);
        }
    }

    @PreDestroy
    void cleanup() {
        disposable.dispose();
    }

}
