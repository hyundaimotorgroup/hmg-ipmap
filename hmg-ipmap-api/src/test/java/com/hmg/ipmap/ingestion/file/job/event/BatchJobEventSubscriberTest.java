package com.hmg.ipmap.ingestion.file.job.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.ingestion.file.AsyncJobRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BatchJobEventSubscriber Tests")
class BatchJobEventSubscriberTest {

    @InjectMocks private BatchJobEventSubscriber subscriber;

    @Mock private RedissonClient redissonClient;

    @Mock private AsyncJobRunner asyncJobRunner;

    @Mock private RTopic topic;

    private MessageListener<String> listener;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(redissonClient.getTopic(any(), any())).thenReturn(topic);

        subscriber.subscribe();

        ArgumentCaptor<MessageListener<String>> captor =
                (ArgumentCaptor<MessageListener<String>>)
                        (ArgumentCaptor<?>) ArgumentCaptor.forClass(MessageListener.class);
        verify(topic).addListener(eq(String.class), captor.capture());
        listener = captor.getValue();
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Nested
    @DisplayName("subscribe()")
    class SubscribeTests {

        @Test
        @DisplayName("Registers a String listener on the batch job start channel")
        void shouldRegisterListenerOnCorrectChannel() {
            verify(redissonClient).getTopic(eq(BatchJobEventPublisher.CHANNEL), any());
            verify(topic).addListener(eq(String.class), any());
        }
    }

    @Nested
    @DisplayName("Message listener")
    class MessageListenerTests {

        @Test
        @DisplayName("Given valid message, run() is called with parsed jobId and userId")
        void shouldDispatchValidMessage() {
            listener.onMessage(BatchJobEventPublisher.CHANNEL, "job-xyz|99");

            verify(asyncJobRunner).run("job-xyz", 99L);
        }

        @ParameterizedTest(name = "message={0}")
        @NullSource
        @ValueSource(strings = {"job-xyz-only", "job-xyz|99|extra"})
        @DisplayName("Given null or malformed message, run() is not called")
        void shouldIgnoreInvalidMessage(String message) {
            listener.onMessage(BatchJobEventPublisher.CHANNEL, message);

            verify(asyncJobRunner, never()).run(any(), any());
        }

        @Test
        @DisplayName(
                "Given valid message, UserContext carries the correct userId when run() is called")
        void shouldSetUserContextWithCorrectUserIdBeforeRun() {
            UserContext[] capturedContext = new UserContext[1];
            doAnswer(
                            invocation -> {
                                capturedContext[0] = UserContextHolder.get();
                                return null;
                            })
                    .when(asyncJobRunner)
                    .run("job-abc", 7L);

            listener.onMessage(BatchJobEventPublisher.CHANNEL, "job-abc|7");

            assertThat(capturedContext[0]).isNotNull();
            assertThat(capturedContext[0].id()).isEqualTo(7L);
        }

        @Test
        @DisplayName("Given valid message, UserContext is cleared after run() completes")
        void shouldClearUserContextAfterRun() {
            listener.onMessage(BatchJobEventPublisher.CHANNEL, "job-abc|7");

            assertThat(UserContextHolder.get()).isNull();
        }

        @Test
        @DisplayName("Given run() throws, UserContext is still cleared")
        void shouldClearUserContextEvenIfRunThrows() {
            doThrow(new RuntimeException("async failure")).when(asyncJobRunner).run(any(), any());

            try {
                listener.onMessage(BatchJobEventPublisher.CHANNEL, "job-abc|7");
            } catch (RuntimeException _) {
                // exception propagates through the listener after finally block clears the context
            }

            assertThat(UserContextHolder.get()).isNull();
        }
    }
}
