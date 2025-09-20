package io.github.koosty;

import io.github.koosty.xmpp.server.XmppTcpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class ReactiveInstantMessagingApplication {
	
	private static final Logger logger = LoggerFactory.getLogger(ReactiveInstantMessagingApplication.class);

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(ReactiveInstantMessagingApplication.class, args);
		
		// Graceful shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			logger.info("Shutting down Reactive Instant Messaging Application");
			XmppTcpServer xmppServer = context.getBean(XmppTcpServer.class);
			xmppServer.stop().block();
		}));
	}
	
	/**
	 * Start XMPP server when application is ready
	 */
	@Component
	public static class XmppServerStarter implements ApplicationListener<ApplicationReadyEvent> {
		
		private final XmppTcpServer xmppServer;
		
		public XmppServerStarter(XmppTcpServer xmppServer) {
			this.xmppServer = xmppServer;
		}
		
		@Override
		public void onApplicationEvent(ApplicationReadyEvent event) {
			logger.info("Starting XMPP Server...");
			xmppServer.start().subscribe(
				null,
				error -> logger.error("Failed to start XMPP Server: {}", error.getMessage(), error),
				() -> logger.info("XMPP Server startup completed")
			);
		}
	}
}
