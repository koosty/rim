package io.github.koosty;

import io.github.koosty.xmpp.stream.XmlStreamProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReactiveInstantMessagingApplicationTests {

	@Autowired
	private XmlStreamProcessor xmlStreamProcessor;

	@Test
	void contextLoads() {
	}

	@Test
	void testStreamHeaderGeneration() {
		// Test that stream header is properly formed with closing >
		StepVerifier.create(xmlStreamProcessor.generateStreamHeader("localhost", null, "test-id-123"))
			.assertNext(header -> {
				System.out.println("Generated header: " + header);
				assertThat(header).contains("from=\"localhost\"");
				assertThat(header).contains("id=\"test-id-123\"");
				assertThat(header).contains("version=\"1.0\"");
				assertThat(header).endsWith(">"); // Critical: must end with >
				assertThat(header).startsWith("<stream:stream");
			})
			.verifyComplete();
	}

}
