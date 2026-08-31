package com.hmg.ipmap.ingestion.file.job.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchJobEventPublisher Tests")
class BatchJobEventPublisherTest {

    @InjectMocks private BatchJobEventPublisherImpl publisher;

    @Mock private RedissonClient redissonClient;

    @Mock private RTopic topic;

    @BeforeEach
    void setUp() {
        when(redissonClient.getTopic(any(), any())).thenReturn(topic);
    }

    @Nested
    @DisplayName("publish()")
    class PublishTests {

        @Test
        @DisplayName("Gets topic with the correct channel and StringCodec")
        void shouldGetTopicWithCorrectChannelAndCodec() {
            publisher.publish("job-123", 42L);

            verify(redissonClient).getTopic(BatchJobEventPublisher.CHANNEL, StringCodec.INSTANCE);
        }

        @Test
        @DisplayName("Publishes message in jobId|userId format")
        void shouldPublishCorrectPayload() {
            publisher.publish("job-123", 42L);

            verify(topic).publish("job-123|42");
        }
    }
}
